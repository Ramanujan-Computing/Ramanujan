# GPT-2 124M Transformer Stack Kernel for Ramanujan
#
# Runs ALL 12 transformer blocks in a single JVM kernel invocation,
# eliminating 11 of the 12 pipe round-trips that cost ~89ms each.
#
# Input CSV arrays (auto-named from filename stem by the Ramanujan runtime):
#   hidden        flat 1D  n_seq * 768   running hidden state (updated in-place)
#   params        flat 1D  [n_seq]       sequence length
#   l0_ln1_g .. l11_c_fc_proj_b         all 12x14 = 168 layer weight arrays
#
# Output (via dump):
#   hidden   (contains the output of all 12 transformer blocks)
#
# GPU/host sync contract:
#   - GPU kernels read/write the GPU buffer (ArrayValue.val).
#   - Host code reads/writes the Java Map.
#   - Ramanujan syncs GPU->Java Map before host code executes.
#   - Ramanujan does NOT sync Java Map->GPU before GPU dispatch.
#
# Therefore: ALL residual adds on `hidden` for layers 0-10 use residual_add_GPU_2
# (GPU), keeping hidden entirely in the GPU buffer between layers.
# Only layer 11's final residual uses a host loop so the Java Map is
# populated for the dump command.

n_seq = params[0]

# Shared scratch arrays (reused across all 12 layers)
h_ln1     = [0 for _ in range(76800)]    # 100 * 768
h_ln2     = [0 for _ in range(76800)]    # 100 * 768
attn_out  = [0 for _ in range(76800)]    # 100 * 768
scores_2d = [0 for _ in range(120000)]   # 12 * 100 * 100

qkv_buf    = [0 for _ in range(230400)]  # 100 * 2304
h_attn_buf = [0 for _ in range(76800)]   # 100 * 768
h_ff_buf   = [0 for _ in range(307200)]  # 100 * 3072
h_out_buf  = [0 for _ in range(76800)]   # 100 * 768

kp_qkv  = [0 for _ in range(2)]
kp_proj = [0 for _ in range(2)]
kp_fc   = [0 for _ in range(2)]
kp_fcp  = [0 for _ in range(2)]

kp_qkv[0]  = 768.0
kp_qkv[1]  = 2304.0
kp_proj[0] = 768.0
kp_proj[1] = 768.0
kp_fc[0]   = 768.0
kp_fc[1]   = 3072.0
kp_fcp[0]  = 3072.0
kp_fcp[1]  = 768.0

# GPU Kernel: batched matrix-multiply + bias
def matmul_bias_GPU_2(A, W, bias, C, kparams, row, col):
    K = kparams[0]
    N = kparams[1]
    s = 0.0
    k = 0
    while k < K:
        a_idx = row * K + k
        w_idx = k * N + col
        s = s + A[a_idx] * W[w_idx]
        k = k + 1
    c_idx = row * N + col
    C[c_idx] = s + bias[col]


# GPU Kernel: GELU activation (in-place on h_ff_buf)
def gelu_GPU_2(h_ff_buf, row, col):
    gid = row * 3072 + col
    val = h_ff_buf[gid]
    cube = val * val * val
    u = 0.7978845608 * (val + 0.044715 * cube)
    two_u = 2.0 * u
    EXP(two_u)
    denom = two_u + 1.0
    tanh_u = 1.0 - 2.0 / denom
    h_ff_buf[gid] = 0.5 * val * (1.0 + tanh_u)


# GPU Kernel: Layer Normalisation
def layernorm_GPU_1(hidden, gamma, beta, out, pos):
    base = pos * 768
    mean = 0.0
    k = 0
    hk = 0
    while k < 768:
        hk = base + k
        mean = mean + hidden[hk]
        k = k + 1
    mean = mean / 768.0
    var = 0.0
    k = 0
    while k < 768:
        hk = base + k
        diff = hidden[hk] - mean
        var = var + diff * diff
        k = k + 1
    var = var / 768.0
    std_val = var + 0.00001
    SQRT(std_val)
    k = 0
    while k < 768:
        idx = base + k
        norm_val = (hidden[idx] - mean) / std_val
        out[idx] = norm_val * gamma[k] + beta[k]
        k = k + 1


# GPU Kernel: Causal Multi-Head Self-Attention
def causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, i, h):
    n_seq = params[0]
    h_off = h * 64
    score_base = h * 10000 + i * 100
    j = 0
    si = 0
    dk = 0
    while j < n_seq:
        sc = 0.0
        dk = 0
        while dk < 64:
            q_idx = i * 2304 + h_off + dk
            k_idx = j * 2304 + 768 + h_off + dk
            sc = sc + qkv_buf[q_idx] * qkv_buf[k_idx]
            dk = dk + 1
        sc = sc / 8.0
        if j > i:
            sc = -10000000000.0
        si = score_base + j
        scores_2d[si] = sc
        j = j + 1
    max_sc = scores_2d[score_base]
    j = 1
    while j < n_seq:
        si = score_base + j
        s = scores_2d[si]
        if s > max_sc:
            max_sc = s
        j = j + 1
    sum_e = 0.0
    j = 0
    while j < n_seq:
        si = score_base + j
        sc_j = scores_2d[si] - max_sc
        EXP(sc_j)
        scores_2d[si] = sc_j
        sum_e = sum_e + sc_j
        j = j + 1
    j = 0
    while j < n_seq:
        si = score_base + j
        scores_2d[si] = scores_2d[si] / sum_e
        j = j + 1
    dk = 0
    while dk < 64:
        ov = 0.0
        j = 0
        while j < n_seq:
            si = score_base + j
            v_idx = j * 2304 + 1536 + h_off + dk
            ov = ov + scores_2d[si] * qkv_buf[v_idx]
            j = j + 1
        ao_idx = i * 768 + h_off + dk
        attn_out[ao_idx] = ov
        dk = dk + 1


# GPU Kernel: Residual Add  hidden[idx] += buf[idx]
def residual_add_GPU_2(hidden, buf, row, col):
    idx = row * 768 + col
    hidden[idx] = hidden[idx] + buf[idx]


# ════════════════════════════════════════════════════════════════════════════
# Layers 0-10: both residuals (attn + FFN) run on GPU so `hidden` stays in
# the GPU buffer between layers.  No host loop, no Java-Map write needed.
# ════════════════════════════════════════════════════════════════════════════

# ── Layer 0 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l0_ln1_g, l0_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l0_c_attn_w, l0_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l0_c_proj_w, l0_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l0_ln2_g, l0_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l0_c_fc_w, l0_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l0_c_fc_proj_w, l0_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 1 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l1_ln1_g, l1_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l1_c_attn_w, l1_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l1_c_proj_w, l1_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l1_ln2_g, l1_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l1_c_fc_w, l1_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l1_c_fc_proj_w, l1_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 2 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l2_ln1_g, l2_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l2_c_attn_w, l2_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l2_c_proj_w, l2_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l2_ln2_g, l2_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l2_c_fc_w, l2_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l2_c_fc_proj_w, l2_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 3 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l3_ln1_g, l3_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l3_c_attn_w, l3_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l3_c_proj_w, l3_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l3_ln2_g, l3_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l3_c_fc_w, l3_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l3_c_fc_proj_w, l3_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 4 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l4_ln1_g, l4_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l4_c_attn_w, l4_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l4_c_proj_w, l4_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l4_ln2_g, l4_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l4_c_fc_w, l4_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l4_c_fc_proj_w, l4_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 5 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l5_ln1_g, l5_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l5_c_attn_w, l5_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l5_c_proj_w, l5_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l5_ln2_g, l5_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l5_c_fc_w, l5_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l5_c_fc_proj_w, l5_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 6 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l6_ln1_g, l6_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l6_c_attn_w, l6_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l6_c_proj_w, l6_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l6_ln2_g, l6_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l6_c_fc_w, l6_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l6_c_fc_proj_w, l6_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 7 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l7_ln1_g, l7_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l7_c_attn_w, l7_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l7_c_proj_w, l7_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l7_ln2_g, l7_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l7_c_fc_w, l7_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l7_c_fc_proj_w, l7_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 8 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l8_ln1_g, l8_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l8_c_attn_w, l8_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l8_c_proj_w, l8_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l8_ln2_g, l8_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l8_c_fc_w, l8_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l8_c_fc_proj_w, l8_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 9 ──────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l9_ln1_g, l9_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l9_c_attn_w, l9_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l9_c_proj_w, l9_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l9_ln2_g, l9_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l9_c_fc_w, l9_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l9_c_fc_proj_w, l9_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ── Layer 10 ─────────────────────────────────────────────────────────────────
layernorm_GPU_1(hidden, l10_ln1_g, l10_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l10_c_attn_w, l10_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l10_c_proj_w, l10_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l10_ln2_g, l10_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l10_c_fc_w, l10_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l10_c_fc_proj_w, l10_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
residual_add_GPU_2(hidden, h_out_buf, n_seq, 768)

# ════════════════════════════════════════════════════════════════════════════
# Layer 11 — attention residual is still GPU; only the FINAL FFN residual
# uses a host loop so that the Java Map is populated for the dump command.
# (GPU writes update ArrayValue.val but NOT the Java Map; dump reads the Map.)
# ════════════════════════════════════════════════════════════════════════════
layernorm_GPU_1(hidden, l11_ln1_g, l11_ln1_b, h_ln1, n_seq)
matmul_bias_GPU_2(h_ln1, l11_c_attn_w, l11_c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)
matmul_bias_GPU_2(attn_out, l11_c_proj_w, l11_c_proj_b, h_attn_buf, kp_proj, n_seq, 768)
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)
layernorm_GPU_1(hidden, l11_ln2_g, l11_ln2_b, h_ln2, n_seq)
matmul_bias_GPU_2(h_ln2, l11_c_fc_w, l11_c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)
gelu_GPU_2(h_ff_buf, n_seq, 3072)
matmul_bias_GPU_2(h_ff_buf, l11_c_fc_proj_w, l11_c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)
# Host loop: populates Java Map so dump can read the final hidden state.
_i = 0
_total = n_seq * 768
while _i < _total:
    hidden[_i] = hidden[_i] + h_out_buf[_i]
    _i = _i + 1

# hidden now contains output of all 12 transformer blocks.
# Orchestrator dumps it with:  dump hidden <path>

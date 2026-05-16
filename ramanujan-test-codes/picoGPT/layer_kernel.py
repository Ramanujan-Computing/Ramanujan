# GPT-2 124M Transformer Layer Kernel for Ramanujan
#
# Applies one transformer block (attention + FFN) to the running hidden state.
# Matrix multiplications run on the GPU via OpenCL (_GPU_2 kernels).
# Layer norms, multi-head attention scoring, softmax, GELU, and residual adds
# run on the host.
#
# Input CSV arrays (auto-named from filename by the Ramanujan runtime):
#   hidden        flat 1D  n_seq * 768           running hidden state
#   params        flat 1D  [n_seq]               sequence length
#   ln1_g         flat 1D  768                   LN-1 gamma
#   ln1_b         flat 1D  768                   LN-1 beta
#   c_attn_w      flat 1D  768 * 2304            QKV weight  (in=768, out=2304)
#   c_attn_b      flat 1D  2304                  QKV bias
#   c_proj_w      flat 1D  768 * 768             O-projection weight
#   c_proj_b      flat 1D  768                   O-projection bias
#   ln2_g         flat 1D  768                   LN-2 gamma
#   ln2_b         flat 1D  768                   LN-2 beta
#   c_fc_w        flat 1D  768 * 3072            FFN up-projection weight
#   c_fc_b        flat 1D  3072                  FFN up-projection bias
#   c_fc_proj_w   flat 1D  3072 * 768            FFN down-projection weight
#   c_fc_proj_b   flat 1D  768                   FFN down-projection bias
#   (qkv_buf, h_attn_buf, h_ff_buf, h_out_buf are now local arrays, not CSV inputs)
#
# Output (via dump):
#   hidden   (updated in-place; contains the block output)
#
# GPT-2 124M constants (hard-coded as literals):
#   n_embd=768  n_head=12  d_head=64  n_ff=3072

n_seq = params[0]

# ── Local working arrays (read by GPU kernels, written by host) ──────────────
# Size = MAX_SEQ * dimension (MAX_SEQ=100 supports prompts + 40 generated tokens)

h_ln1    = [0 for _ in range(76800)]    # 100 * 768  — LN-1 output
h_ln2    = [0 for _ in range(76800)]    # 100 * 768  — LN-2 output
attn_out = [0 for _ in range(76800)]    # 100 * 768  — attention weighted sum
scores_2d = [0 for _ in range(120000)]  # 12 * 100 * 100 scratch for causal_attn GPU kernel

# GPU output buffers — declared locally so the JVM never serialises their
# zero-init data to JSON (saves ~290K zero entries = ~4.3 MB per layer call).
qkv_buf    = [0 for _ in range(230400)]   # 100 * 2304 — QKV projection output
h_attn_buf = [0 for _ in range(76800)]    # 100 * 768  — O-proj output
h_ff_buf   = [0 for _ in range(307200)]   # 100 * 3072 — FFN up-proj output
h_out_buf  = [0 for _ in range(76800)]    # 100 * 768  — FFN down-proj output

# kparams arrays carry [K, N] for each matmul variant
kp_qkv  = [0 for _ in range(2)]    # QKV:   K=768,  N=2304
kp_proj = [0 for _ in range(2)]    # O-proj: K=768,  N=768
kp_fc   = [0 for _ in range(2)]    # FFN-1:  K=768,  N=3072
kp_fcp  = [0 for _ in range(2)]    # FFN-2:  K=3072, N=768

kp_qkv[0]  = 768.0
kp_qkv[1]  = 2304.0
kp_proj[0] = 768.0
kp_proj[1] = 768.0
kp_fc[0]   = 768.0
kp_fc[1]   = 3072.0
kp_fcp[0]  = 3072.0
kp_fcp[1]  = 768.0

# ── GPU Kernel: batched matrix-multiply + bias ───────────────────────────────
# Computes: C[row * N + col] = bias[col] + Σ_{k} A[row*K + k] * W[k*N + col]
# A : (M × K)  W : (K × N)  bias : (N,)  C : (M × N)
# Dispatched as a 2-D NDRange: row ∈ [0, M)  col ∈ [0, N)
# kparams[0] = K,  kparams[1] = N
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


# ── GPU Kernel: GELU activation (in-place on h_ff_buf) ──────────────────────
# GELU(x) = 0.5 * x * (1 + tanh(√(2/π) * (x + 0.044715·x³)))
# Dispatched as a 2-D NDRange: row ∈ [0, n_seq)  col ∈ [0, 3072)
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


# ── GPU Kernel: Layer Normalisation ─────────────────────────────────────────
# Each work item (pos) normalises one token row of n_embd=768.
# Dispatched as a 1-D NDRange: pos ∈ [0, n_seq)
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


# ── GPU Kernel: Causal Multi-Head Self-Attention ─────────────────────────────
# Each work item (i, h) handles one (query_position, head) pair end-to-end:
#   1. Compute QK scores and apply causal mask
#   2. Softmax over the sequence dimension
#   3. Weighted value accumulation → attn_out
# scores_2d layout: [h * 10000 + i * 100 + j]  (MAX_SEQ=100, n_head=12)
# Each (i,h) pair writes to a disjoint slice — no race conditions.
# Dispatched as a 2-D NDRange: i ∈ [0, n_seq)  h ∈ [0, 12)
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


# ── GPU Kernel: Residual Add ─────────────────────────────────────────────────
# hidden[idx] += buf[idx]  for every element in n_seq * 768.
# Dispatched as a 2-D NDRange: row ∈ [0, n_seq)  col ∈ [0, 768)
def residual_add_GPU_2(hidden, buf, row, col):
    idx = row * 768 + col
    hidden[idx] = hidden[idx] + buf[idx]


# ════════════════════════════════════════════════════════════════════════════
# Step 1 — Layer Norm 1 (GPU)
# ════════════════════════════════════════════════════════════════════════════
layernorm_GPU_1(hidden, ln1_g, ln1_b, h_ln1, n_seq)


# ════════════════════════════════════════════════════════════════════════════
# Step 2 — QKV Projection (GPU)
# qkv_buf[row*2304 + col] = Σ_k h_ln1[row*768+k] * c_attn_w[k*2304+col]
#                           + c_attn_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(h_ln1, c_attn_w, c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)


# ════════════════════════════════════════════════════════════════════════════
# Step 3 — Multi-Head Causal Self-Attention (GPU)
# scores_2d[h*10000 + i*100 + j] holds per-(i,h) softmax weights.
# ════════════════════════════════════════════════════════════════════════════
causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, params, n_seq, 12)


# ════════════════════════════════════════════════════════════════════════════
# Step 4 — Output Projection (GPU)
# h_attn_buf[row*768+col] = Σ_k attn_out[row*768+k] * c_proj_w[k*768+col]
#                           + c_proj_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(attn_out, c_proj_w, c_proj_b, h_attn_buf, kp_proj, n_seq, 768)


# ════════════════════════════════════════════════════════════════════════════
# Step 5 — Residual Add 1 (GPU):  hidden += h_attn_buf
# ════════════════════════════════════════════════════════════════════════════
residual_add_GPU_2(hidden, h_attn_buf, n_seq, 768)


# ════════════════════════════════════════════════════════════════════════════
# Step 6 — Layer Norm 2 (GPU)
# ════════════════════════════════════════════════════════════════════════════
layernorm_GPU_1(hidden, ln2_g, ln2_b, h_ln2, n_seq)


# ════════════════════════════════════════════════════════════════════════════
# Step 7 — FFN Up-Projection (GPU)
# h_ff_buf[row*3072+col] = Σ_k h_ln2[row*768+k] * c_fc_w[k*3072+col]
#                          + c_fc_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(h_ln2, c_fc_w, c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)


# ════════════════════════════════════════════════════════════════════════════
# Step 8 — GELU Activation (GPU, in-place on h_ff_buf)
# ════════════════════════════════════════════════════════════════════════════
gelu_GPU_2(h_ff_buf, n_seq, 3072)


# ════════════════════════════════════════════════════════════════════════════
# Step 9 — FFN Down-Projection (GPU)
# h_out_buf[row*768+col] = Σ_k h_ff_buf[row*3072+k] * c_fc_proj_w[k*768+col]
#                          + c_fc_proj_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(h_ff_buf, c_fc_proj_w, c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)


# ════════════════════════════════════════════════════════════════════════════
# Step 10 — Residual Add 2 (host):  hidden += h_out_buf
# Must run on host so Array.getValues() is populated for the dump command.
# GPU writes update ArrayValue.val but NOT the Java Map — dump reads the Map.
# ════════════════════════════════════════════════════════════════════════════
_i = 0
_total = n_seq * 768
while _i < _total:
    hidden[_i] = hidden[_i] + h_out_buf[_i]
    _i = _i + 1

# hidden is now the output of this transformer block.
# The orchestrator will dump it with: dump hidden <path>

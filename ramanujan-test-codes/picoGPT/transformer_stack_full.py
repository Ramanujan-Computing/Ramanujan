# GPT-2 124M Transformer Stack FULL Kernel for Ramanujan
#
# Runs ALL 12 transformer blocks for ALL n_tokens generation steps in one
# JVM kernel invocation.  wte, wpe, ln_f_g, ln_f_b are passed as CSV inputs
# so the full head (ln_f -> logits -> argmax -> embed_next) runs entirely in
# GPU memory between steps.  Output: generated_tokens (n_tokens floats whose
# integer values are the BPE token IDs in generation order).
#
# Input CSV arrays (in argument order):
#   hidden          flat 1D  (n_seq+n_tokens)*768   prompt embed in first n_seq rows
#   params          flat 1D  [n_seq, n_tokens]
#   cur_n_seq_arr   flat 1D  [n_seq]                GPU-maintained current seq length
#   step_arr        flat 1D  [0.0]                  GPU-maintained step counter
#   wte             flat 1D  50257*768              token embeddings
#   wpe             flat 1D  1024*768               position embeddings
#   ln_f_g          flat 1D  768                    final layer-norm gamma
#   ln_f_b          flat 1D  768                    final layer-norm beta
#   l0_ln1_g .. l11_c_fc_proj_b                     168 layer weight arrays
#
# Output (via dump):
#   generated_tokens   n_tokens integer-valued floats
#
# GPU/host sync contract:
#   All 12 residual adds per step use residual_add_GPU_2 so hidden stays in
#   the GPU buffer across layers AND across token steps.
#   cur_n_seq_arr[0] and step_arr[0] are incremented in GPU memory by
#   inc_counters_GPU_1 at the end of each step; subsequent GPU kernels in the
#   same in-order OpenCL queue see the updated values automatically.
#   GPU_SYNC(generated_tokens) after the uber loop flushes all steps' token
#   writes to the Java Map so the orchestrator's dump command can read them.

n_seq    = params[0]
n_tokens = params[1]

# ── Shared scratch arrays (reused across all 12 layers and all steps) ────────
# Sized for N_CTX=1024 so any n_seq+n_tokens <= 1024 works.
h_ln1     = [0 for _ in range(786432)]    # 1024 * 768
h_ln2     = [0 for _ in range(786432)]    # 1024 * 768
attn_out  = [0 for _ in range(786432)]    # 1024 * 768
scores_2d = [0 for _ in range(12582912)]  # 12 * 1024 * 1024

qkv_buf    = [0 for _ in range(2359296)]  # 1024 * 2304
h_attn_buf = [0 for _ in range(786432)]   # 1024 * 768
h_ff_buf   = [0 for _ in range(3145728)]  # 1024 * 3072
h_out_buf  = [0 for _ in range(786432)]   # 1024 * 768
h_state    = [0 for _ in range(786432)]   # 1024 * 768

# ── Head scratch arrays ───────────────────────────────────────────────────────
logits           = [0 for _ in range(50257)]  # vocab logits for the last position
argmax_arr       = [0 for _ in range(1)]      # argmax result stored as float
generated_tokens = [0 for _ in range(1024)]   # output: one token ID per step

# ── Matrix-dimension parameter arrays (constant) ─────────────────────────────
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


# ── GPU kernel definitions ────────────────────────────────────────────────────

# Batched matrix-multiply + bias  C[row,col] = A[row] · W[:,col] + bias[col]
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


# GELU activation in-place on h_ff_buf
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


# Layer normalisation: out[pos] = layernorm(hidden[pos], gamma, beta)
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


# Causal multi-head self-attention.
# MODIFIED vs transformer_stack.py: reads n_seq from cur_n_seq_arr[0]
# (GPU-maintained, incremented by inc_counters_GPU_1) so the inner loop
# bound is correct for the current generation step.
def causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, i, h):
    n_seq = cur_n_seq_arr[0]
    h_off = h * 64
    score_base = h * 1048576 + i * 1024
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


# Residual add: hidden[row,col] += buf[row,col]
def residual_add_GPU_2(hidden, buf, row, col):
    idx = row * 768 + col
    hidden[idx] = hidden[idx] + buf[idx]


# Copy: dst[row,col] = src[row,col]
def copy_GPU_2(src, dst, row, col):
    idx = row * 768 + col
    dst[idx] = src[idx]


# Logits for the last sequence position: logits[j] = dot(h_ln1[last_row], wte[j])
# Dispatched with 50257 work items (one per vocab token).
def logits_last_GPU_1(h_ln1, wte, logits, cur_n_seq_arr, j):
    n_s = cur_n_seq_arr[0]
    base = (n_s - 1) * 768
    s = 0.0
    k = 0
    while k < 768:
        h_idx = base + k
        w_idx = j * 768 + k
        s = s + h_ln1[h_idx] * wte[w_idx]
        k = k + 1
    logits[j] = s


# Argmax over logits — 1 work item, serial loop over 50257 elements.
# Stores the winning index (as a float) into argmax_arr[0].
def argmax_GPU_1(logits, argmax_arr, dummy):
    max_v = logits[0]
    max_i = 0.0
    j = 1
    while j < 50257:
        v = logits[j]
        if v > max_v:
            max_v = v
            max_i = j
        j = j + 1
    argmax_arr[0] = max_i


# Store the current step's token ID into generated_tokens — 1 work item.
def store_token_GPU_1(argmax_arr, generated_tokens, step_arr, dummy):
    stepArrIndex = step_arr[0];
    generated_tokens[stepArrIndex] = argmax_arr[0]


# Embed next token: hidden[cur_n_seq, col] = wte[tok, col] + wpe[cur_n_seq, col]
# Dispatched with 768 work items (one per embedding dimension).
def embed_next_GPU_1(hidden, wte, wpe, argmax_arr, cur_n_seq_arr, col):
    tok = argmax_arr[0]
    n_s = cur_n_seq_arr[0]
    n_sIndex = n_s * 768 + col
    tok_index = tok * 768 + col

    hidden[n_sIndex] = wte[tok_index] + wpe[n_sIndex]


# Increment both GPU-maintained counters — 1 work item.
# Called at the end of every token step so the next step's GPU kernels see
# the updated cur_n_seq_arr[0] and step_arr[0] values.
def inc_counters_GPU_1(step_arr, cur_n_seq_arr, dummy):
    step_arr[0]      = step_arr[0]      + 1.0
    cur_n_seq_arr[0] = cur_n_seq_arr[0] + 1.0


# ════════════════════════════════════════════════════════════════════════════
# Uber generation loop
#
# cur_n_seq is a host variable used as the NDRange dispatch dimension; its
# value is captured at each call-site, so each iteration dispatches the right
# number of work items even though the GPU executes non-blocking.
#
# cur_n_seq_arr[0] is the GPU-resident copy read inside causal_attn_GPU_2.
# It starts at n_seq (from cur_n_seq.csv) and is incremented each step by
# inc_counters_GPU_1.  Because OpenCL uses an in-order queue, kernels queued
# after inc_counters_GPU_1 are guaranteed to see the incremented value.
# ════════════════════════════════════════════════════════════════════════════
_step = 0
while _step < n_tokens:
    cur_n_seq = n_seq + _step

    copy_GPU_2(hidden, h_state, cur_n_seq, 768)

    # ── Layer 0 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l0_ln1_g, l0_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l0_c_attn_w, l0_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l0_c_proj_w, l0_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l0_ln2_g, l0_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l0_c_fc_w, l0_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l0_c_fc_proj_w, l0_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 1 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l1_ln1_g, l1_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l1_c_attn_w, l1_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l1_c_proj_w, l1_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l1_ln2_g, l1_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l1_c_fc_w, l1_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l1_c_fc_proj_w, l1_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 2 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l2_ln1_g, l2_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l2_c_attn_w, l2_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l2_c_proj_w, l2_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l2_ln2_g, l2_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l2_c_fc_w, l2_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l2_c_fc_proj_w, l2_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 3 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l3_ln1_g, l3_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l3_c_attn_w, l3_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l3_c_proj_w, l3_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l3_ln2_g, l3_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l3_c_fc_w, l3_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l3_c_fc_proj_w, l3_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 4 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l4_ln1_g, l4_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l4_c_attn_w, l4_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l4_c_proj_w, l4_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l4_ln2_g, l4_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l4_c_fc_w, l4_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l4_c_fc_proj_w, l4_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 5 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l5_ln1_g, l5_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l5_c_attn_w, l5_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l5_c_proj_w, l5_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l5_ln2_g, l5_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l5_c_fc_w, l5_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l5_c_fc_proj_w, l5_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 6 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l6_ln1_g, l6_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l6_c_attn_w, l6_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l6_c_proj_w, l6_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l6_ln2_g, l6_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l6_c_fc_w, l6_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l6_c_fc_proj_w, l6_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 7 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l7_ln1_g, l7_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l7_c_attn_w, l7_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l7_c_proj_w, l7_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l7_ln2_g, l7_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l7_c_fc_w, l7_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l7_c_fc_proj_w, l7_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 8 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l8_ln1_g, l8_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l8_c_attn_w, l8_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l8_c_proj_w, l8_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l8_ln2_g, l8_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l8_c_fc_w, l8_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l8_c_fc_proj_w, l8_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 9 ──────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l9_ln1_g, l9_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l9_c_attn_w, l9_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l9_c_proj_w, l9_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l9_ln2_g, l9_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l9_c_fc_w, l9_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l9_c_fc_proj_w, l9_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 10 ─────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l10_ln1_g, l10_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l10_c_attn_w, l10_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l10_c_proj_w, l10_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l10_ln2_g, l10_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l10_c_fc_w, l10_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l10_c_fc_proj_w, l10_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Layer 11 ─────────────────────────────────────────────────────────────
    layernorm_GPU_1(h_state, l11_ln1_g, l11_ln1_b, h_ln1, cur_n_seq)
    matmul_bias_GPU_2(h_ln1, l11_c_attn_w, l11_c_attn_b, qkv_buf, kp_qkv, cur_n_seq, 2304)
    causal_attn_GPU_2(qkv_buf, scores_2d, attn_out, cur_n_seq_arr, cur_n_seq, 12)
    matmul_bias_GPU_2(attn_out, l11_c_proj_w, l11_c_proj_b, h_attn_buf, kp_proj, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_attn_buf, cur_n_seq, 768)
    layernorm_GPU_1(h_state, l11_ln2_g, l11_ln2_b, h_ln2, cur_n_seq)
    matmul_bias_GPU_2(h_ln2, l11_c_fc_w, l11_c_fc_b, h_ff_buf, kp_fc, cur_n_seq, 3072)
    gelu_GPU_2(h_ff_buf, cur_n_seq, 3072)
    matmul_bias_GPU_2(h_ff_buf, l11_c_fc_proj_w, l11_c_fc_proj_b, h_out_buf, kp_fcp, cur_n_seq, 768)
    residual_add_GPU_2(h_state, h_out_buf, cur_n_seq, 768)

    # ── Head: ln_f → logits → argmax → store → embed next token ─────────────
    # Reuse h_ln1 as scratch for the final layer norm (ln_f).
    layernorm_GPU_1(h_state, ln_f_g, ln_f_b, h_ln1, cur_n_seq)
    logits_last_GPU_1(h_ln1, wte, logits, cur_n_seq_arr, 50257)
    argmax_GPU_1(logits, argmax_arr, 1)
    store_token_GPU_1(argmax_arr, generated_tokens, step_arr, 1)
    embed_next_GPU_1(hidden, wte, wpe, argmax_arr, cur_n_seq_arr, 768)
    inc_counters_GPU_1(step_arr, cur_n_seq_arr, 1)

    _step = _step + 1

# Flush generated_tokens from GPU buffer to Java Map so the orchestrator's
# dump command can read all n_tokens token IDs.
GPU_SYNC(generated_tokens)

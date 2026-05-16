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
#   qkv_buf       flat 1D  n_seq * 2304  zeros   GPU write: QKV output
#   h_attn_buf    flat 1D  n_seq * 768   zeros   GPU write: O-proj output
#   h_ff_buf      flat 1D  n_seq * 3072  zeros   GPU write: FFN-1 output
#   h_out_buf     flat 1D  n_seq * 768   zeros   GPU write: FFN-2 output
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
scores   = [0 for _ in range(100)]      # per-query attention scores (max n_seq)

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


# ════════════════════════════════════════════════════════════════════════════
# Step 1 — Layer Norm 1 (host)
# Normalises each row of hidden with mean/variance computed over n_embd=768.
# Writes result into h_ln1.
# ════════════════════════════════════════════════════════════════════════════
pos = 0
while pos < n_seq:
    # --- mean ---
    mean = 0.0
    k = 0
    while k < 768:
        idx = pos * 768 + k
        mean = mean + hidden[idx]
        k = k + 1
    mean = mean / 768

    # --- variance ---
    var = 0.0
    k = 0
    while k < 768:
        idx = pos * 768 + k
        diff = hidden[idx] - mean
        var = var + diff * diff
        k = k + 1
    var = var / 768

    # --- inverse std ---
    std = var + 0.00001
    SQRT(std)

    # --- normalise, scale, shift ---
    k = 0
    while k < 768:
        idx = pos * 768 + k
        norm_val = (hidden[idx] - mean) / std
        h_ln1[idx] = norm_val * ln1_g[k] + ln1_b[k]
        k = k + 1

    pos = pos + 1


# ════════════════════════════════════════════════════════════════════════════
# Step 2 — QKV Projection (GPU)
# qkv_buf[row*2304 + col] = Σ_k h_ln1[row*768+k] * c_attn_w[k*2304+col]
#                           + c_attn_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(h_ln1, c_attn_w, c_attn_b, qkv_buf, kp_qkv, n_seq, 2304)


# ════════════════════════════════════════════════════════════════════════════
# Step 3 — Multi-Head Causal Self-Attention (host)
#
# Layout inside qkv_buf for token i:
#   Q[i, h, k] = qkv_buf[i*2304 + h*64 + k]          (heads 0..11, dim 0..63)
#   K[i, h, k] = qkv_buf[i*2304 + 768  + h*64 + k]
#   V[i, h, k] = qkv_buf[i*2304 + 1536 + h*64 + k]
#
# For each head:
#   scores[j] = (Q[i,h] · K[j,h]) / sqrt(64) = ... / 8.0
#   causal mask: scores[j] = -1e10 when j > i
#   attn_weights = softmax(scores)
#   attn_out[i, h] = Σ_j attn_weights[j] * V[j, h]
# ════════════════════════════════════════════════════════════════════════════
h = 0
while h < 12:
    h_off = h * 64          # byte offset into each token's 768-dim embedding

    i = 0
    while i < n_seq:

        # --- compute attention scores for query position i, head h ---
        j = 0
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
                sc = -10000000000.0     # causal mask (positions beyond query)
            scores[j] = sc
            j = j + 1

        # --- softmax over scores[0 .. n_seq-1] ---
        max_sc = scores[0]
        j = 1
        while j < n_seq:
            if scores[j] > max_sc:
                max_sc = scores[j]
            j = j + 1

        sum_e = 0.0
        j = 0
        while j < n_seq:
            sc_j = scores[j] - max_sc
            EXP(sc_j)
            scores[j] = sc_j
            sum_e = sum_e + sc_j
            j = j + 1

        j = 0
        while j < n_seq:
            scores[j] = scores[j] / sum_e
            j = j + 1

        # --- weighted sum of values → attn_out[i, h] ---
        dk = 0
        while dk < 64:
            ov = 0.0
            j = 0
            while j < n_seq:
                v_idx = j * 2304 + 1536 + h_off + dk
                ov = ov + scores[j] * qkv_buf[v_idx]
                j = j + 1
            ao_idx = i * 768 + h_off + dk
            attn_out[ao_idx] = ov
            dk = dk + 1

        i = i + 1
    h = h + 1


# ════════════════════════════════════════════════════════════════════════════
# Step 4 — Output Projection (GPU)
# h_attn_buf[row*768+col] = Σ_k attn_out[row*768+k] * c_proj_w[k*768+col]
#                           + c_proj_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(attn_out, c_proj_w, c_proj_b, h_attn_buf, kp_proj, n_seq, 768)


# ════════════════════════════════════════════════════════════════════════════
# Step 5 — Residual Add 1 (host):  hidden += h_attn_buf
# ════════════════════════════════════════════════════════════════════════════
pos = 0
while pos < n_seq:
    k = 0
    while k < 768:
        idx = pos * 768 + k
        hidden[idx] = hidden[idx] + h_attn_buf[idx]
        k = k + 1
    pos = pos + 1


# ════════════════════════════════════════════════════════════════════════════
# Step 6 — Layer Norm 2 (host)
# Same structure as LN-1; writes result into h_ln2.
# ════════════════════════════════════════════════════════════════════════════
pos = 0
while pos < n_seq:
    mean = 0.0
    k = 0
    while k < 768:
        idx = pos * 768 + k
        mean = mean + hidden[idx]
        k = k + 1
    mean = mean / 768

    var = 0.0
    k = 0
    while k < 768:
        idx = pos * 768 + k
        diff = hidden[idx] - mean
        var = var + diff * diff
        k = k + 1
    var = var / 768

    std = var + 0.00001
    SQRT(std)

    k = 0
    while k < 768:
        idx = pos * 768 + k
        norm_val = (hidden[idx] - mean) / std
        h_ln2[idx] = norm_val * ln2_g[k] + ln2_b[k]
        k = k + 1

    pos = pos + 1


# ════════════════════════════════════════════════════════════════════════════
# Step 7 — FFN Up-Projection (GPU)
# h_ff_buf[row*3072+col] = Σ_k h_ln2[row*768+k] * c_fc_w[k*3072+col]
#                          + c_fc_b[col]
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(h_ln2, c_fc_w, c_fc_b, h_ff_buf, kp_fc, n_seq, 3072)


# ════════════════════════════════════════════════════════════════════════════
# Step 8 — GELU Activation (host, in-place on h_ff_buf)
#
# GELU(x) = 0.5 * x * (1 + tanh(√(2/π) * (x + 0.044715·x³)))
# where tanh(u) = 1 − 2/(exp(2u)+1)   (computed via built-in EXP)
# √(2/π) ≈ 0.7978845608
# ════════════════════════════════════════════════════════════════════════════
pos = 0
while pos < n_seq:
    j = 0
    while j < 3072:
        ff_idx = pos * 3072 + j
        val  = h_ff_buf[ff_idx]
        cube = val * val * val
        u    = 0.7978845608 * (val + 0.044715 * cube)
        two_u = 2.0 * u
        EXP(two_u)                                  # two_u  ← exp(2u)
        denom  = two_u + 1.0
        tanh_u = 1.0 - 2.0 / denom
        h_ff_buf[ff_idx] = 0.5 * val * (1.0 + tanh_u)
        j = j + 1
    pos = pos + 1


# ════════════════════════════════════════════════════════════════════════════
# Step 9 — FFN Down-Projection (GPU)
# h_out_buf[row*768+col] = Σ_k h_ff_buf[row*3072+k] * c_fc_proj_w[k*768+col]
#                          + c_fc_proj_b[col]
# (h_ff_buf has been modified in-place by GELU on the host; the runtime
#  re-stages host → GPU memory before dispatching this kernel.)
# ════════════════════════════════════════════════════════════════════════════
matmul_bias_GPU_2(h_ff_buf, c_fc_proj_w, c_fc_proj_b, h_out_buf, kp_fcp, n_seq, 768)


# ════════════════════════════════════════════════════════════════════════════
# Step 10 — Residual Add 2 (host):  hidden += h_out_buf
# ════════════════════════════════════════════════════════════════════════════
pos = 0
while pos < n_seq:
    k = 0
    while k < 768:
        idx = pos * 768 + k
        hidden[idx] = hidden[idx] + h_out_buf[idx]
        k = k + 1
    pos = pos + 1

# hidden is now the output of this transformer block.
# The orchestrator will dump it with: dump hidden <path>

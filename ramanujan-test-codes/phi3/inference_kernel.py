# =============================================================================
# Phi-3-mini-4k-instruct — Ramanujan GPU Kernel (element-wise ops)
# =============================================================================
#
# This kernel runs the parallelisable element-wise operations of one
# transformer layer phase on the Ramanujan distributed GPU runtime.
# Heavy matrix multiplications are handled by the NumPy orchestrator.
#
# CSV inputs (injected automatically from filenames):
#   hidden_in  – flat [SEQ*3072]  hidden state
#   norm_w     – flat [3072]      RMS norm weights
#   rope_cos   – flat [SEQ*48]    cos table
#   rope_sin   – flat [SEQ*48]    sin table
#   q_in       – flat [SEQ*3072]  Q after projection
#   k_in       – flat [SEQ*3072]  K after projection
#   v_in       – flat [SEQ*3072]  V after projection
#   params     – flat [4]         [actual_seq, mode, 0, 0]
#
# Query outputs:
#   var actual_seq           → confirms params were read
#   arr concat <flat_idx>    → attention context output
# =============================================================================

# ── Buffers ──────────────────────────────────────────────────────────────────
attn_sc  = [0 for _ in range(4096)]
concat   = [0 for _ in range(196608)]
rms_vals = [0 for _ in range(64)]
head_buf = [0 for _ in range(1)]
normed   = [0 for _ in range(196608)]

actual_seq = params[0][0]


# =============================================================================
# GPU KERNELS
# =============================================================================

def rms_apply_GPU_1(vec, rms_vals, weight, out, gid):
    pos = gid / 3072
    dim = gid - pos * 3072
    rv  = rms_vals[pos]
    out[gid] = vec[gid] * rv * weight[dim]


def rope_GPU_1(q_in, k_in, rope_cos, rope_sin, gid):
    pos  = gid / 1536
    pi   = gid - pos * 1536
    head = pi / 48
    pair = pi - head * 48
    base = pos * 3072 + head * 96
    i0   = base + pair
    i1   = base + 48 + pair
    ri   = pos * 48 + pair
    c    = rope_cos[ri]
    s    = rope_sin[ri]
    q0   = q_in[i0]
    q1   = q_in[i1]
    q_in[i0] = q0 * c - q1 * s
    q_in[i1] = q1 * c + q0 * s
    k0   = k_in[i0]
    k1   = k_in[i1]
    k_in[i0] = k0 * c - k1 * s
    k_in[i1] = k1 * c + k0 * s


def attn_score_GPU_1(q_in, k_in, attn_sc, head_buf, gid):
    t1   = gid / 64
    t2   = gid - t1 * 64
    hoff = head_buf[0] * 96
    acc  = 0
    d    = 0
    while d < 96:
        qi  = t1 * 3072 + hoff + d
        ki  = t2 * 3072 + hoff + d
        acc = acc + q_in[qi] * k_in[ki]
        d   = d + 1
    attn_sc[gid] = acc * 0.10206207


def attn_ctx_GPU_1(attn_sc, v_in, concat, head_buf, gid):
    t1   = gid / 96
    d    = gid - t1 * 96
    hoff = head_buf[0] * 96
    acc  = 0
    t2   = 0
    while t2 < 64:
        si  = t1 * 64 + t2
        vi  = t2 * 3072 + hoff + d
        acc = acc + attn_sc[si] * v_in[vi]
        t2  = t2 + 1
    ci         = t1 * 3072 + hoff + d
    concat[ci] = acc


# =============================================================================
# HOST HELPERS
# =============================================================================

def rms_norm_compute(vec):
    pos = 0
    while pos < 64:
        acc = 0
        d   = 0
        while d < 3072:
            idx = pos * 3072 + d
            acc = acc + vec[idx] * vec[idx]
            d   = d + 1
        acc = acc / 3072 + 0.00001
        SQRT(acc)
        rms_vals[pos] = 1 / acc
        pos = pos + 1


def softmax_attn():
    t1 = 0
    while t1 < 64:
        base1 = t1 * 64
        t2 = t1 + 1
        while t2 < 64:
            idx = base1 + t2
            attn_sc[idx] = -1000000
            t2 = t2 + 1
        mx = attn_sc[base1]
        j  = 1
        while j <= t1:
            idx = base1 + j
            if attn_sc[idx] > mx:
                mx = attn_sc[idx]
            j = j + 1
        sm = 0
        j  = 0
        while j <= t1:
            idx = base1 + j
            v   = attn_sc[idx] - mx
            EXP(v)
            attn_sc[idx] = v
            sm = sm + v
            j  = j + 1
        j = 0
        while j <= t1:
            idx = base1 + j
            attn_sc[idx] = attn_sc[idx] / sm
            j = j + 1
        t2 = t1 + 1
        while t2 < 64:
            idx = base1 + t2
            attn_sc[idx] = 0
            t2 = t2 + 1
        t1 = t1 + 1


# =============================================================================
# MAIN — Pre-attention phase: RMS norm → RoPE → multi-head attention
# =============================================================================

rms_norm_compute(hidden_in)
rms_apply_GPU_1(hidden_in, rms_vals, norm_w, normed, 196608)

rope_GPU_1(q_in, k_in, rope_cos, rope_sin, 98304)

h = 0
while h < 32:
    head_buf[0] = h
    attn_score_GPU_1(q_in, k_in, attn_sc, head_buf, 4096)
    softmax_attn()
    attn_ctx_GPU_1(attn_sc, v_in, concat, head_buf, 6144)
    h = h + 1

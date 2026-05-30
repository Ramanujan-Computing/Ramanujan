# phi3_lm_head.py
# LM Head execution

n_seq       = params[0]
is_decode   = params[1]
cur_n_seq   = params[2]

cur_n_seq_arr = [0 for _ in range(1)]
cur_n_seq_arr[0] = cur_n_seq

logits           = [0 for _ in range(32064)]
logits_partial   = [0 for _ in range(98500608)]  
h_ln1     = [0 for _ in range(3145728)]   

# argmax_arr is now passed as a CSV-backed argument (not locally declared)
# so that its values are always present in the Java Array.values map for dump.

def matmul_4bit_GPU_2(A, W_packed, W_scales, C, kparams, row, col):
    K = kparams[0]
    N = kparams[1]
    K_pack = kparams[2]

    W_scales_idx0 = 0
    w_base = 0
    W_packed_idx0 = 0
    a_idx = 0
    A_idx0 = 0
    c_idx = 0
    packed = 0.0
    w0 = 0.0
    w1 = 0.0
    w2 = 0.0
    w3 = 0.0
    w4 = 0.0
    w5 = 0.0

    W_scales_idx0 = col
    scale = W_scales[W_scales_idx0]
    w_base = col * K_pack

    s = 0.0
    k_pack = 0
    while k_pack < K_pack:
        W_packed_idx0 = w_base + k_pack
        packed = W_packed[W_packed_idx0]

        w5 = packed / 1048576.0
        FLOOR(w5)
        packed = packed - w5 * 1048576.0
        w4 = packed / 65536.0
        FLOOR(w4)
        packed = packed - w4 * 65536.0
        w3 = packed / 4096.0
        FLOOR(w3)
        packed = packed - w3 * 4096.0
        w2 = packed / 256.0
        FLOOR(w2)
        packed = packed - w2 * 256.0
        w1 = packed / 16.0
        FLOOR(w1)
        packed = packed - w1 * 16.0
        w0 = packed

        w0 = (w0 - 8.0) * scale
        w1 = (w1 - 8.0) * scale
        w2 = (w2 - 8.0) * scale
        w3 = (w3 - 8.0) * scale
        w4 = (w4 - 8.0) * scale
        w5 = (w5 - 8.0) * scale

        a_idx = row * K + k_pack * 6
        s = s + A[a_idx] * w0
        a_idx = a_idx + 1
        s = s + A[a_idx] * w1
        a_idx = a_idx + 1
        s = s + A[a_idx] * w2
        a_idx = a_idx + 1
        s = s + A[a_idx] * w3
        a_idx = a_idx + 1
        s = s + A[a_idx] * w4
        a_idx = a_idx + 1
        s = s + A[a_idx] * w5

        k_pack = k_pack + 1

    c_idx = row * N + col
    C[c_idx] = s

def matmul_4bit_decode_GPU_1(A, W_packed, W_scales, C, kparams, cur_n_seq_arr, col):
    K = kparams[0]
    N = kparams[1]
    K_pack = kparams[2]

    K_int = 0
    if K == 3072.0:
        K_int = 3072
    if K == 8192.0:
        K_int = 8192
    if K == 16384.0:
        K_int = 16384

    N_int = 0
    if N == 3072.0:
        N_int = 3072
    if N == 9216.0:
        N_int = 9216
    if N == 16384.0:
        N_int = 16384

    row = cur_n_seq_arr[0] - 1.0
    row_int = 0
    r_f = row + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0

    W_scales_idx0 = 0
    w_base = 0
    W_packed_idx0 = 0
    a_idx = 0
    c_idx = 0
    packed = 0.0
    w0 = 0.0
    w1 = 0.0
    w2 = 0.0
    w3 = 0.0
    w4 = 0.0
    w5 = 0.0

    W_scales_idx0 = col
    scale = W_scales[W_scales_idx0]
    w_base = col * K_pack

    s = 0.0
    k_pack = 0
    while k_pack < K_pack:
        W_packed_idx0 = w_base + k_pack
        packed = W_packed[W_packed_idx0]

        w5 = packed / 1048576.0
        FLOOR(w5)
        packed = packed - w5 * 1048576.0
        w4 = packed / 65536.0
        FLOOR(w4)
        packed = packed - w4 * 65536.0
        w3 = packed / 4096.0
        FLOOR(w3)
        packed = packed - w3 * 4096.0
        w2 = packed / 256.0
        FLOOR(w2)
        packed = packed - w2 * 256.0
        w1 = packed / 16.0
        FLOOR(w1)
        packed = packed - w1 * 16.0
        w0 = packed

        w0 = (w0 - 8.0) * scale
        w1 = (w1 - 8.0) * scale
        w2 = (w2 - 8.0) * scale
        w3 = (w3 - 8.0) * scale
        w4 = (w4 - 8.0) * scale
        w5 = (w5 - 8.0) * scale

        a_idx = row_int * K_int + k_pack * 6
        s = s + A[a_idx] * w0
        a_idx = a_idx + 1
        s = s + A[a_idx] * w1
        a_idx = a_idx + 1
        s = s + A[a_idx] * w2
        a_idx = a_idx + 1
        s = s + A[a_idx] * w3
        a_idx = a_idx + 1
        s = s + A[a_idx] * w4
        a_idx = a_idx + 1
        s = s + A[a_idx] * w5

        k_pack = k_pack + 1

    c_idx = row_int * N_int + col
    C[c_idx] = s

def rmsnorm_GPU_1(hidden, gamma, out, row):
    base = 0
    hk = 0
    idx = 0

    base = row * 3072
    s = 0.0
    k = 0
    while k < 3072:
        hk = base + k
        val = hidden[hk]
        s = s + val * val
        k = k + 1

    rms = s / 3072.0 + 0.00001
    SQRT(rms)

    k = 0
    while k < 3072:
        idx = base + k
        out[idx] = (hidden[idx] / rms) * gamma[k]
        k = k + 1

def rmsnorm_decode_GPU_1(hidden, gamma, out, cur_n_seq_arr, k):
    row = cur_n_seq_arr[0] - 1.0
    base = 0
    hk = 0
    idx = 0

    base = row * 3072
    s = 0.0
    ki = 0
    while ki < 3072:
        hk = base + ki
        val = hidden[hk]
        s = s + val * val
        ki = ki + 1

    rms = s / 3072.0 + 0.00001
    SQRT(rms)

    idx = base + k
    out[idx] = (hidden[idx] / rms) * gamma[k]

def rope_and_cache_GPU_2(qkv_buf, cos_cache, sin_cache, k_cache, v_cache, row, col):
    h_off_q = 0
    h_off_k = 0
    h_off_v = 0
    q_base = 0
    k_base = 0
    v_base = 0
    cos_base = 0
    sin_base = 0
    c_base = 0
    cos_cache_idx0 = 0
    sin_cache_idx0 = 0
    qkv_buf_idx0 = 0
    k_cache_idx0 = 0
    v_cache_idx0 = 0

    pos = row * 1.0
    h_off_q = col * 96
    h_off_k = 3072 + col * 96
    h_off_v = 6144 + col * 96

    q_base = row * 9216 + h_off_q
    k_base = row * 9216 + h_off_k
    v_base = row * 9216 + h_off_v

    cos_base = pos * 48
    sin_base = pos * 48

    c_base = row * 3072 + col * 96

    i = 0
    while i < 48:
        cos_cache_idx0 = cos_base + i
        c = cos_cache[cos_cache_idx0]
        sin_cache_idx0 = sin_base + i
        s = sin_cache[sin_cache_idx0]

        qkv_buf_idx0 = q_base + i
        q1 = qkv_buf[qkv_buf_idx0]
        qkv_buf_idx0 = q_base + i + 48
        q2 = qkv_buf[qkv_buf_idx0]
        qkv_buf_idx0 = q_base + i
        qkv_buf[qkv_buf_idx0] = q1 * c - q2 * s
        qkv_buf_idx0 = q_base + i + 48
        qkv_buf[qkv_buf_idx0] = q2 * c + q1 * s

        qkv_buf_idx0 = k_base + i
        k1 = qkv_buf[qkv_buf_idx0]
        qkv_buf_idx0 = k_base + i + 48
        k2 = qkv_buf[qkv_buf_idx0]
        new_k1 = k1 * c - k2 * s
        new_k2 = k2 * c + k1 * s
        qkv_buf_idx0 = k_base + i
        qkv_buf[qkv_buf_idx0] = new_k1
        qkv_buf_idx0 = k_base + i + 48
        qkv_buf[qkv_buf_idx0] = new_k2

        k_cache_idx0 = c_base + i
        k_cache[k_cache_idx0] = new_k1
        k_cache_idx0 = c_base + i + 48
        k_cache[k_cache_idx0] = new_k2

        qkv_buf_idx0 = v_base + i
        v1 = qkv_buf[qkv_buf_idx0]
        qkv_buf_idx0 = v_base + i + 48
        v2 = qkv_buf[qkv_buf_idx0]
        v_cache_idx0 = c_base + i
        v_cache[v_cache_idx0] = v1
        v_cache_idx0 = c_base + i + 48
        v_cache[v_cache_idx0] = v2

        i = i + 1

def rope_and_cache_decode_GPU_1(qkv_buf, cos_cache, sin_cache, k_cache, v_cache, cur_n_seq_arr, col):
    pos = cur_n_seq_arr[0] - 1.0
    row_int = 0
    r_f = pos + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0

    h_off_q = 0
    h_off_k = 0
    q_base = 0
    k_base = 0
    v_base = 0
    q_idx_1 = 0
    q_idx_2 = 0
    k_idx_1 = 0
    k_idx_2 = 0
    v_idx = 0
    cos_cache_idx0 = 0

    h_off_q = col * 96
    h_off_k = col * 96
    q_base = row_int * 9216 + h_off_q
    k_base = row_int * 9216 + 3072 + h_off_k
    v_base = row_int * 9216 + 6144 + h_off_k

    d = 0
    while d < 48:
        q_idx_1 = q_base + d
        q_idx_2 = q_base + d + 48

        k_idx_1 = k_base + d
        k_idx_2 = k_base + d + 48

        cos_cache_idx0 = pos * 48.0 + d
        c = cos_cache[cos_cache_idx0]
        s_val = sin_cache[cos_cache_idx0]

        q1 = qkv_buf[q_idx_1]
        q2 = qkv_buf[q_idx_2]
        k1 = qkv_buf[k_idx_1]
        k2 = qkv_buf[k_idx_2]

        qkv_buf[q_idx_1] = q1 * c - q2 * s_val
        qkv_buf[q_idx_2] = q1 * s_val + q2 * c

        new_k1 = k1 * c - k2 * s_val
        new_k2 = k1 * s_val + k2 * c

        qkv_buf[k_idx_1] = new_k1
        qkv_buf[k_idx_2] = new_k2

        kc_idx1 = row_int * 3072 + h_off_k + d
        k_cache[kc_idx1] = new_k1
        kc_idx2 = row_int * 3072 + h_off_k + d + 48
        k_cache[kc_idx2] = new_k2
        d = d + 1

    d = 0
    while d < 96:
        v_idx = v_base + d
        v_val = qkv_buf[v_idx]
        vc_idx = row_int * 3072 + h_off_k + d
        v_cache[vc_idx] = v_val
        d = d + 1

def causal_attn_GPU_2(qkv_buf, k_cache, v_cache, scores_2d, attn_out, cur_n_seq_arr, row, col):
    n_seq = cur_n_seq_arr[0]

    h_off = 0
    score_base = 0
    q_base = 0
    k_base = 0
    qkv_buf_idx1 = 0
    k_cache_idx0 = 0
    scores_2d_idx0 = 0
    scores_2d_idx1 = 0
    v_offset = 0
    v_cache_idx0 = 0
    attn_out_idx0 = 0

    h_off = col * 96
    score_base = col * 1048576 + row * 1024

    dk = 0
    j = 0
    while j < n_seq:
        sc = 0.0
        dk = 0
        q_base = row * 9216 + h_off
        k_base = j * 3072 + h_off
        while dk < 96:
            qkv_buf_idx1 = q_base + dk
            k_cache_idx0 = k_base + dk
            sc = sc + qkv_buf[qkv_buf_idx1] * k_cache[k_cache_idx0]
            dk = dk + 1

        sc = sc / 9.79795897
        if j > row:
            sc = -65504.0

        scores_2d_idx0 = score_base + j
        scores_2d[scores_2d_idx0] = sc
        j = j + 1

    max_sc = scores_2d[score_base]
    j = 1
    while j < n_seq:
        scores_2d_idx0 = score_base + j
        s = scores_2d[scores_2d_idx0]
        if s > max_sc:
            max_sc = s
        j = j + 1

    sum_e = 0.0
    j = 0
    while j < n_seq:
        scores_2d_idx0 = score_base + j
        sc_j = scores_2d[scores_2d_idx0] - max_sc
        EXP(sc_j)
        scores_2d_idx0 = score_base + j
        scores_2d[scores_2d_idx0] = sc_j
        sum_e = sum_e + sc_j
        j = j + 1

    j = 0
    while j < n_seq:
        scores_2d_idx1 = score_base + j
        scores_2d_idx0 = score_base + j
        scores_2d[scores_2d_idx1] = scores_2d[scores_2d_idx0] / sum_e
        j = j + 1

    dk = 0
    while dk < 96:
        ov = 0.0
        j = 0
        v_offset = h_off + dk
        while j < n_seq:
            scores_2d_idx1 = score_base + j
            v_cache_idx0 = v_offset + j * 3072
            ov = ov + scores_2d[scores_2d_idx1] * v_cache[v_cache_idx0]
            j = j + 1
        attn_out_idx0 = row * 3072 + h_off + dk
        attn_out[attn_out_idx0] = ov
        dk = dk + 1

def causal_attn_k_decode_GPU_2(qkv_buf, k_cache, scores_2d, cur_n_seq_arr, j, col):
    n_seq = cur_n_seq_arr[0]
    if j < n_seq:
        row_int = 0
        r_f = cur_n_seq_arr[0] - 1.0 + 0.1
        while r_f >= 1024.0:
            row_int = row_int + 1024
            r_f = r_f - 1024.0
        while r_f >= 1.0:
            row_int = row_int + 1
            r_f = r_f - 1.0
        h_off = col * 96
        score_base = col * 1048576 + row_int * 1024
        sc = 0.0
        dk = 0
        q_base = row_int * 9216 + h_off
        k_base = j * 3072 + h_off
        qkv_buf_idx1 = 0
        k_cache_idx0 = 0
        while dk < 96:
            qkv_buf_idx1 = q_base + dk
            k_cache_idx0 = k_base + dk
            sc = sc + qkv_buf[qkv_buf_idx1] * k_cache[k_cache_idx0]
            dk = dk + 1
        sc = sc / 9.79795897
        scores_2d_idx0 = score_base + j
        scores_2d[scores_2d_idx0] = sc

def causal_attn_softmax_decode_GPU_1(scores_2d, cur_n_seq_arr, col):
    n_seq = cur_n_seq_arr[0]
    row_int = 0
    r_f = cur_n_seq_arr[0] - 1.0 + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0
    score_base = col * 1048576 + row_int * 1024

    scores_2d_idx0 = 0
    scores_2d_idx1 = 0
    sc_j = 0.0
    s = 0.0

    max_sc = scores_2d[score_base]
    j = 1
    while j < n_seq:
        scores_2d_idx0 = score_base + j
        s = scores_2d[scores_2d_idx0]
        if s > max_sc:
            max_sc = s
        j = j + 1

    sum_e = 0.0
    j = 0
    while j < n_seq:
        scores_2d_idx0 = score_base + j
        sc_j = scores_2d[scores_2d_idx0] - max_sc
        EXP(sc_j)
        scores_2d[scores_2d_idx0] = sc_j
        sum_e = sum_e + sc_j
        j = j + 1

    j = 0
    while j < n_seq:
        scores_2d_idx1 = score_base + j
        scores_2d_idx0 = score_base + j
        scores_2d[scores_2d_idx1] = scores_2d[scores_2d_idx0] / sum_e
        j = j + 1

def causal_attn_v_decode_GPU_2(scores_2d, v_cache, attn_out, cur_n_seq_arr, dk, col):
    n_seq = cur_n_seq_arr[0]
    row_int = 0
    r_f = cur_n_seq_arr[0] - 1.0 + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0
    h_off = col * 96
    score_base = col * 1048576 + row_int * 1024

    ov = 0.0
    j = 0
    v_offset = h_off + dk
    while j < n_seq:
        scores_2d_idx1 = score_base + j
        v_cache_idx0 = v_offset + j * 3072
        ov = ov + scores_2d[scores_2d_idx1] * v_cache[v_cache_idx0]
        j = j + 1
    attn_out_idx0 = row_int * 3072 + h_off + dk
    attn_out[attn_out_idx0] = ov

def silu_GPU_2(h_ff_buf, row, col):
    idx = 0
    h_ff_buf_idx0 = 0

    idx = row * 16384 + col
    gate = h_ff_buf[idx]
    h_ff_buf_idx0 = idx + 8192
    up = h_ff_buf[h_ff_buf_idx0]

    neg_gate = 0.0 - gate
    EXP(neg_gate)
    sig = 1.0 / (1.0 + neg_gate)

    res = gate * sig * up
    h_ff_buf[idx] = res

def silu_decode_GPU_1(h_ff_buf, cur_n_seq_arr, col):
    idx = 0
    h_ff_buf_idx0 = 0

    row = cur_n_seq_arr[0] - 1.0
    row_int = 0
    r_f = row + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0

    idx = row_int * 16384 + col
    gate = h_ff_buf[idx]
    h_ff_buf_idx0 = idx + 8192
    up = h_ff_buf[h_ff_buf_idx0]

    neg_gate = 0.0 - gate
    EXP(neg_gate)
    sig = 1.0 / (1.0 + neg_gate)

    res = gate * sig * up
    h_ff_buf[idx] = res

def residual_add_GPU_2(hidden, buf, row, col):
    idx = 0
    idx = row * 3072 + col
    hidden[idx] = hidden[idx] + buf[idx]

def residual_add_decode_GPU_1(hidden, buf, cur_n_seq_arr, col):
    idx = 0
    row = cur_n_seq_arr[0] - 1.0
    idx = row * 3072 + col
    hidden[idx] = hidden[idx] + buf[idx]

def copy_GPU_2(src, dst, row, col):
    idx = 0
    idx = row * 3072 + col
    dst[idx] = src[idx]

def logits_compute_GPU_2(h_ln1, lm_head_1, lm_head_2, logits_partial, cur_n_seq_arr, j, k):
    row_int = 0
    r_f = (cur_n_seq_arr[0] - 1.0) + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0
    h_ln1_idx1 = row_int * 3072 + k
    w_val = 0.0
    wte_idx0 = 0
    if j < 16000:
        wte_idx0 = j * 3072 + k
        w_val = lm_head_1[wte_idx0]
    else:
        wte_idx0 = (j - 16000) * 3072 + k
        w_val = lm_head_2[wte_idx0]
    partial_idx = j * 3072 + k
    logits_partial[partial_idx] = h_ln1[h_ln1_idx1] * w_val

def logits_reduce_GPU_1(logits_partial, logits, j):
    s = 0.0
    k = 0
    partial_idx = 0
    while k < 3072:
        partial_idx = j * 3072 + k
        s = s + logits_partial[partial_idx]
        k = k + 1
    logits[j] = s

def argmax_GPU_1(logits, argmax_arr, gid):
    max_v = logits[0]
    max_i = 0.0
    j = 1
    while j < 32064:
        v = logits[j]
        if v > max_v:
            max_v = v
            max_i = j
        j = j + 1
    argmax_arr[0] = max_i

def store_token_GPU_1(argmax_arr, generated_tokens, step_arr, gid):
    step_f = step_arr[0] + 0.1
    step_int = 0
    while step_f >= 1024.0:
        step_int = step_int + 1024
        step_f = step_f - 1024.0
    while step_f >= 1.0:
        step_int = step_int + 1
        step_f = step_f - 1.0
    generated_tokens[step_int] = argmax_arr[0]

def embed_next_GPU_1(hidden, wte_1, wte_2, argmax_arr, cur_n_seq_arr, col):
    idx = 0
    wte_idx0 = 0

    tok = argmax_arr[0]
    t_int = 0
    t_f = tok + 0.1
    while t_f >= 1024.0:
        t_int = t_int + 1024
        t_f = t_f - 1024.0
    while t_f >= 1.0:
        t_int = t_int + 1
        t_f = t_f - 1.0

    n_s = cur_n_seq_arr[0]
    row_int = 0
    r_f = n_s + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0

    idx = row_int * 3072 + col
    wte_idx0 = 0
    if t_int < 16000:
        wte_idx0 = t_int * 3072 + col
        hidden[idx] = wte_1[wte_idx0]
    else:
        wte_idx0 = (t_int - 16000) * 3072 + col
        hidden[idx] = wte_2[wte_idx0]

def inc_counters_GPU_1(step_arr, cur_n_seq_arr, gid):
    step_arr[0] = step_arr[0] + 1.0
    cur_n_seq_arr[0] = cur_n_seq_arr[0] + 1.0

if is_decode == 0.0:
    # PREFILL
    rmsnorm_GPU_1(hidden, ln_f_g, h_ln1, n_seq)
    j = 0
    while j < n_seq:
        logits_compute_GPU_2(h_ln1, lm_head_1, lm_head_2, logits_partial, cur_n_seq_arr, 32064, 3072)
        j = j + 1
    j_tok = n_seq - 1.0
    row_int = 0
    r_f = j_tok + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0

    GPU_SYNC(h_ln1)   # h_ln1 was written on GPU by rmsnorm_GPU_1; sync before CPU logits loop
    i = 0
    while i < 32064:
        wte_idx0 = 0
        w_val = 0.0
        k = 0
        s = 0.0
        while k < 3072:
            if i < 16000:
                idx1 = i * 3072 + k
                w_val = lm_head_1[idx1]
            else:
                idx2 = i - 16000
                idx2 = idx2 * 3072 + k
                w_val = lm_head_2[idx2]
            h_idx = row_int * 3072 + k
            s = s + h_ln1[h_idx] * w_val
            k = k + 1
        logits[i] = s
        i = i + 1
        
    argmax_v = logits[0]
    argmax_i = 0.0
    argmax_j = 1
    while argmax_j < 32064:
        argmax_vj = logits[argmax_j]
        if argmax_vj > argmax_v:
            argmax_v = argmax_vj
            argmax_i = argmax_j * 1.0
        argmax_j = argmax_j + 1
    argmax_arr[0] = argmax_i
else:
    # DECODE
    rmsnorm_decode_GPU_1(hidden, ln_f_g, h_ln1, cur_n_seq_arr, 3072)
    j_tok = cur_n_seq_arr[0] - 1.0
    row_int = 0
    r_f = j_tok + 0.1
    while r_f >= 1024.0:
        row_int = row_int + 1024
        r_f = r_f - 1024.0
    while r_f >= 1.0:
        row_int = row_int + 1
        r_f = r_f - 1.0

    GPU_SYNC(h_ln1)   # h_ln1 was written on GPU by rmsnorm_decode_GPU_1; sync before CPU logits loop
    i = 0
    while i < 32064:
        w_val = 0.0
        k = 0
        s = 0.0
        while k < 3072:
            if i < 16000:
                idx1 = i * 3072 + k
                w_val = lm_head_1[idx1]
            else:
                idx2 = i - 16000
                idx2 = idx2 * 3072 + k
                w_val = lm_head_2[idx2]
            h_idx = row_int * 3072 + k
            s = s + h_ln1[h_idx] * w_val
            k = k + 1
        logits[i] = s
        i = i + 1

    argmax_v = logits[0]
    argmax_i = 0.0
    argmax_j = 1
    while argmax_j < 32064:
        argmax_vj = logits[argmax_j]
        if argmax_vj > argmax_v:
            argmax_v = argmax_vj
            argmax_i = argmax_j * 1.0
        argmax_j = argmax_j + 1
    argmax_arr[0] = argmax_i


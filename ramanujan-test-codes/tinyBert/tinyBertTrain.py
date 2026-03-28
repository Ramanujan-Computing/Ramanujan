# =============================================================================
# TinyBERT Training on Ramanujan Distributed Computing
# =============================================================================
#
# Architecture (all sizes chosen to fit Ramanujan fixed-array constraints):
#   VOCAB_SIZE  = 128   (ASCII-range tokens)
#   SEQ_LEN     = 16    (tokens per sample)
#   D_MODEL     = 32    (embedding / hidden dim)
#   D_FF        = 64    (feed-forward inner dim)
#   N_HEADS     = 4     (attention heads; D_HEAD = D_MODEL / N_HEADS = 8)
#   N_LAYERS    = 1     (single transformer block for Ramanujan constraints)
#   BATCH_SIZE  = 8     (samples per gradient-descent step)
#
# Training task: Masked-token prediction (MLM).
#   - One token per sample is "masked" (replaced with token id 0).
#   - The model predicts the original token id at the masked position.
#   - Loss: cross-entropy over VOCAB_SIZE classes at the masked position.
#
# Ramanujan language constraints honoured:
#   * No import, no class, no for-loop, no **, no %, no and/or/not in conditions
#   * Only while loops, if/else (no elif), fixed-size arrays
#   * Functions are pass-by-reference; last parameter receives the return value
#   * GPU kernels named <name>_GPU_<ndim>; data args first, range args last
#   * No nested function calls as arguments; use intermediate variables
#   * No array element as function argument; assign to local variable first
#   * Array index must be a simple variable or literal (not an expression)
#
# =============================================================================

# ── Constants ─────────────────────────────────────────────────────────────────

VOCAB   = 128
SEQ     = 16
DM      = 32
DFF     = 64
DH      = 8
NH      = 4
BS      = 8
LR      = 0.01
EPOCHS  = 3
STEPS   = 4

# ── Weight matrices (flat 1-D) ────────────────────────────────────────────────
tok_emb  = [0 for _ in range(4096)]
pos_emb  = [0 for _ in range(512)]
Wq       = [0 for _ in range(1024)]
Wk       = [0 for _ in range(1024)]
Wv       = [0 for _ in range(1024)]
Wo       = [0 for _ in range(1024)]
W1       = [0 for _ in range(2048)]
W2       = [0 for _ in range(2048)]
Wout     = [0 for _ in range(4096)]
b1       = [0 for _ in range(64)]
b2       = [0 for _ in range(32)]
bout     = [0 for _ in range(128)]

# ── Gradient accumulators ─────────────────────────────────────────────────────
g_tok_emb = [0 for _ in range(4096)]
g_pos_emb = [0 for _ in range(512)]
g_Wq      = [0 for _ in range(1024)]
g_Wk      = [0 for _ in range(1024)]
g_Wv      = [0 for _ in range(1024)]
g_Wo      = [0 for _ in range(1024)]
g_W1      = [0 for _ in range(2048)]
g_W2      = [0 for _ in range(2048)]
g_Wout    = [0 for _ in range(4096)]
g_b1      = [0 for _ in range(64)]
g_b2      = [0 for _ in range(32)]
g_bout    = [0 for _ in range(128)]

# ── Activation buffers ────────────────────────────────────────────────────────
x_emb       = [0 for _ in range(512)]
q_buf       = [0 for _ in range(512)]
k_buf       = [0 for _ in range(512)]
v_buf       = [0 for _ in range(512)]
attn_w      = [0 for _ in range(256)]
attn_out    = [0 for _ in range(512)]
ff_h        = [0 for _ in range(64)]
ff_out      = [0 for _ in range(32)]
logits      = [0 for _ in range(128)]
softmax_out = [0 for _ in range(128)]

# ── Backprop delta buffers ────────────────────────────────────────────────────
d_logits   = [0 for _ in range(128)]
d_ff_out   = [0 for _ in range(32)]
d_ff_h     = [0 for _ in range(64)]
d_attn_out = [0 for _ in range(512)]
d_q        = [0 for _ in range(512)]
d_k        = [0 for _ in range(512)]
d_v        = [0 for _ in range(512)]
d_x_emb    = [0 for _ in range(512)]

# ── Synthetic dataset ─────────────────────────────────────────────────────────
dataset    = [0 for _ in range(512)]
labels     = [0 for _ in range(32)]
masked_pos = 8

ds_i = 0
while ds_i < 512:
    tmp_val = ds_i
    while tmp_val >= 128:
        tmp_val = tmp_val - 128
    dataset[ds_i] = tmp_val
    ds_i = ds_i + 1

lbl_i = 0
while lbl_i < 32:
    lbl_ds_idx = lbl_i * 16 + 8
    labels[lbl_i] = dataset[lbl_ds_idx]
    lbl_i = lbl_i + 1

# =============================================================================
# Weight initialisation  (Xavier uniform via LCG RNG)
# =============================================================================
rng_state = 12345

def lcg_next(state):
    next_s = state * 1664525 + 1013904223
    while next_s >= 2147483648:
        next_s = next_s - 2147483648
    while next_s < 0:
        next_s = next_s + 2147483648
    return next_s

def rand_float(state, lo, hi, out_val):
    next_s = lcg_next(state)
    norm = next_s / 2147483648
    out_val = lo + norm * (hi - lo)
    state = next_s

def init_array(arr, size, scale):
    idx = 0
    sv = 0
    neg_scale = 0 - scale
    while idx < size:
        rand_float(rng_state, neg_scale, scale, sv)
        arr[idx] = sv
        idx = idx + 1

scale_emb = 0.1
scale_w   = 0.1

init_array(tok_emb,  4096, scale_emb)
init_array(pos_emb,  512,  scale_emb)
init_array(Wq,       1024, scale_w)
init_array(Wk,       1024, scale_w)
init_array(Wv,       1024, scale_w)
init_array(Wo,       1024, scale_w)
init_array(W1,       2048, scale_w)
init_array(W2,       2048, scale_w)
init_array(Wout,     4096, scale_w)

# =============================================================================
# GPU KERNELS  (_GPU_1: gid = work-item index)
# =============================================================================

def embed_GPU_1(tok_ids, tok_emb, pos_emb, x_emb, mp_buf, gid):
    pos    = gid / 32
    dim    = gid - pos * 32
    tid    = tok_ids[pos]
    if pos == mp_buf[0]:
        tid = 0
    te_idx     = tid * 32 + dim
    x_emb[gid] = tok_emb[te_idx] + pos_emb[gid]

def linear_dm_dm_GPU_1(in_vec, W, out_vec, gid):
    acc = 0
    j = 0
    while j < 32:
        w_idx = gid * 32 + j
        acc = acc + in_vec[j] * W[w_idx]
        j = j + 1
    out_vec[gid] = acc

# Q/K/V projections for all SEQ positions in parallel.
# gid encodes (pos, d_out): pos = gid/32, d_out = gid%32.  Range = SEQ*DM = 512.
def proj_qkv_GPU_1(x_emb, Wq, Wk, Wv, q_buf, k_buf, v_buf, gid):
    pos   = gid / 32
    d_out = gid - pos * 32
    accq  = 0
    acck  = 0
    accv  = 0
    d_in  = 0
    while d_in < 32:
        xe_idx = pos * 32 + d_in
        w_idx  = d_in * 32 + d_out
        accq   = accq + x_emb[xe_idx] * Wq[w_idx]
        acck   = acck + x_emb[xe_idx] * Wk[w_idx]
        accv   = accv + x_emb[xe_idx] * Wv[w_idx]
        d_in   = d_in + 1
    q_buf[gid] = accq
    k_buf[gid] = acck
    v_buf[gid] = accv

# Scaled dot-product attention scores for one head.
# gid encodes (t1, t2): t1 = gid/16, t2 = gid%16.  Range = SEQ*SEQ = 256.
# head_buf[0] holds the current head index; h_off = head * DH = head * 8.
def attn_score_GPU_1(q_buf, k_buf, attn_w, head_buf, gid):
    t1    = gid / 16
    t2    = gid - t1 * 16
    h_off = head_buf[0] * 8
    acc   = 0
    dh    = 0
    while dh < 8:
        qi_idx = t1 * 32 + h_off + dh
        ki_idx = t2 * 32 + h_off + dh
        acc    = acc + q_buf[qi_idx] * k_buf[ki_idx]
        dh     = dh + 1
    attn_w[gid] = acc * 0.35355339

def softmax_vec(vec, size, out_vec):
    mx = vec[0]
    si = 1
    while si < size:
        if vec[si] > mx:
            mx = vec[si]
        si = si + 1
    s = 0
    si = 0
    while si < size:
        z = vec[si] - mx
        ez = z
        EXP(ez)
        out_vec[si] = ez
        s = s + ez
        si = si + 1
    si = 0
    while si < size:
        out_vec[si] = out_vec[si] / s
        si = si + 1

# Weighted context vector for one head.
# gid encodes (t1, dh): t1 = gid/8, dh = gid%8.  Range = SEQ*DH = 128.
# head_buf[0] holds the current head index.
def context_GPU_1(attn_w, v_buf, concat_buf, head_buf, gid):
    t1    = gid / 8
    dh    = gid - t1 * 8
    h_off = head_buf[0] * 8
    acc   = 0
    t2    = 0
    while t2 < 16:
        aw_idx = t1 * 16 + t2
        vi_idx = t2 * 32 + h_off + dh
        acc    = acc + attn_w[aw_idx] * v_buf[vi_idx]
        t2     = t2 + 1
    cb_idx           = t1 * 32 + h_off + dh
    concat_buf[cb_idx] = acc

# Output projection + residual add.
# gid encodes (ao, d_out): ao = gid/32, d_out = gid%32.  Range = SEQ*DM = 512.
def proj_out_GPU_1(concat_buf, Wo, x_emb, attn_out, gid):
    ao    = gid / 32
    d_out = gid - ao * 32
    acc   = 0
    id_o  = 0
    while id_o < 32:
        cb_idx = ao * 32 + id_o
        wo_idx = id_o * 32 + d_out
        acc    = acc + concat_buf[cb_idx] * Wo[wo_idx]
        id_o   = id_o + 1
    attn_out[gid] = x_emb[gid] + acc

# FFN first layer with ReLU.  gid runs over DFF = 64.  mp_buf[0] = mask_pos.
def ffn1_GPU_1(attn_out, W1, b1, ff_h, mp_buf, gid):
    mp  = mp_buf[0]
    acc = 0
    di  = 0
    while di < 32:
        ao_idx = mp * 32 + di
        w1_idx = di * 64 + gid
        acc    = acc + attn_out[ao_idx] * W1[w1_idx]
        di     = di + 1
    acc = acc + b1[gid]
    if acc > 0:
        ff_h[gid] = acc
    else:
        ff_h[gid] = 0

# FFN second layer.  gid runs over DM = 32.
def ffn2_GPU_1(ff_h, W2, b2, ff_out, gid):
    acc  = 0
    d_ff = 0
    while d_ff < 64:
        w2_idx = d_ff * 32 + gid
        acc    = acc + ff_h[d_ff] * W2[w2_idx]
        d_ff   = d_ff + 1
    ff_out[gid] = acc + b2[gid]

# Classifier linear layer.  gid runs over VOCAB = 128.
def classifier_GPU_1(ff_out, Wout, bout, logits, gid):
    acc = 0
    di  = 0
    while di < 32:
        wo_idx = di * 128 + gid
        acc    = acc + ff_out[di] * Wout[wo_idx]
        di     = di + 1
    logits[gid] = acc + bout[gid]

def sgd_update_GPU_1(W, grad, lr_arr, gid):
    W[gid] = W[gid] - lr_arr[0] * grad[gid]

# =============================================================================
# Host-side helpers
# Native built-ins (in-place): EXP(v)->v=e^v  LOG(v)->v=ln(v)  SQRT(v)->v=sqrt(v)
# =============================================================================

def zero_arr(arr, size):
    zi = 0
    while zi < size:
        arr[zi] = 0
        zi = zi + 1

def copy_arr(src, dst, size):
    ci = 0
    while ci < size:
        dst[ci] = src[ci]
        ci = ci + 1

def layer_norm(vec, size, out_vec):
    mu = 0
    li = 0
    while li < size:
        mu = mu + vec[li]
        li = li + 1
    mu = mu / size
    var2 = 0
    li = 0
    while li < size:
        diff = vec[li] - mu
        var2 = var2 + diff * diff
        li = li + 1
    var2 = var2 / size
    std = var2
    SQRT(std)
    eps = 0.00001
    li = 0
    while li < size:
        denom = std + eps
        out_vec[li] = (vec[li] - mu) / denom
        li = li + 1

def cross_entropy(probs, label_id, loss_out):
    p = probs[label_id]
    if p < 1e-38:
        p = 1e-38
    ln_p = p
    LOG(ln_p)
    loss_out = 0 - ln_p

def ce_softmax_grad(probs, label_id, d_logits_out, size):
    gi = 0
    while gi < size:
        d_logits_out[gi] = probs[gi]
        gi = gi + 1
    d_logits_out[label_id] = d_logits_out[label_id] - 1

# =============================================================================
# FORWARD PASS
# =============================================================================

scores_row  = [0 for _ in range(16)]
lr_buf      = [0 for _ in range(1)]
mp_buf      = [0 for _ in range(1)]
head_buf    = [0 for _ in range(1)]
concat_buf  = [0 for _ in range(512)]
tmp_dm      = [0 for _ in range(32)]
tmp_dh      = [0 for _ in range(8)]
tok_emb_row = [0 for _ in range(32)]
ctx_head    = [0 for _ in range(8)]
softmax_scores = [0 for _ in range(16)]
ln_buf      = [0 for _ in range(32)]

def forward_embed(tok_ids, mask_pos):
    mp_buf[0] = mask_pos
    embed_GPU_1(tok_ids, tok_emb, pos_emb, x_emb, mp_buf, 512)

def forward_attention():
    zero_arr(q_buf,    512)
    zero_arr(k_buf,    512)
    zero_arr(v_buf,    512)
    zero_arr(attn_out, 512)
    zero_arr(concat_buf, 512)

    proj_qkv_GPU_1(x_emb, Wq, Wk, Wv, q_buf, k_buf, v_buf, 512)

    head = 0
    while head < 4:
        head_buf[0] = head

        # Scaled dot-product scores for this head (SEQ×SEQ = 256)
        attn_score_GPU_1(q_buf, k_buf, attn_w, head_buf, 256)

        # Softmax each query row – sequential reduction, host side
        t1 = 0
        while t1 < 16:
            sr = 0
            while sr < 16:
                aw_idx         = t1 * 16 + sr
                scores_row[sr] = attn_w[aw_idx]
                sr = sr + 1
            softmax_vec(scores_row, 16, softmax_scores)
            sr = 0
            while sr < 16:
                aw_idx        = t1 * 16 + sr
                attn_w[aw_idx] = softmax_scores[sr]
                sr = sr + 1
            t1 = t1 + 1

        # Weighted context vectors for this head → concat_buf (SEQ×DH = 128)
        context_GPU_1(attn_w, v_buf, concat_buf, head_buf, 128)

        head = head + 1

    # Output projection + residual (SEQ×DM = 512)
    proj_out_GPU_1(concat_buf, Wo, x_emb, attn_out, 512)

    # Layer-norm per token row – sequential reduction, host side
    ao = 0
    while ao < 16:
        di = 0
        while di < 32:
            ao_idx     = ao * 32 + di
            ln_buf[di] = attn_out[ao_idx]
            di = di + 1
        layer_norm(ln_buf, 32, ln_buf)
        di = 0
        while di < 32:
            ao_idx           = ao * 32 + di
            attn_out[ao_idx] = ln_buf[di]
            di = di + 1
        ao = ao + 1

def forward_ffn(mask_pos):
    mp_buf[0] = mask_pos
    ffn1_GPU_1(attn_out, W1, b1, ff_h, mp_buf, 64)
    ffn2_GPU_1(ff_h, W2, b2, ff_out, 32)

    # Residual + layer-norm (single DM=32 vector at the mask position)
    di = 0
    while di < 32:
        ao_idx     = mask_pos * 32 + di
        ln_buf[di] = attn_out[ao_idx] + ff_out[di]
        di = di + 1
    layer_norm(ln_buf, 32, ff_out)

def forward_classifier():
    classifier_GPU_1(ff_out, Wout, bout, logits, 128)
    softmax_vec(logits, 128, softmax_out)

# =============================================================================
# BACKWARD PASS
# =============================================================================

def backward_classifier(label_id):
    ce_softmax_grad(softmax_out, label_id, d_logits, 128)

    v = 0
    while v < 128:
        di = 0
        while di < 32:
            gwo_idx = di * 128 + v
            g_Wout[gwo_idx] = g_Wout[gwo_idx] + ff_out[di] * d_logits[v]
            di = di + 1
        g_bout[v] = g_bout[v] + d_logits[v]
        v = v + 1

    di = 0
    while di < 32:
        acc = 0
        v = 0
        while v < 128:
            wout_idx = di * 128 + v
            acc = acc + Wout[wout_idx] * d_logits[v]
            v = v + 1
        d_ff_out[di] = acc
        di = di + 1

def backward_ffn(mask_pos):
    d_ff = 0
    while d_ff < 64:
        acc = 0
        di = 0
        while di < 32:
            w2_idx = d_ff * 32 + di
            acc = acc + d_ff_out[di] * W2[w2_idx]
            di = di + 1
        if ff_h[d_ff] > 0:
            d_ff_h[d_ff] = acc
        else:
            d_ff_h[d_ff] = 0
        d_ff = d_ff + 1

    d_ff = 0
    while d_ff < 64:
        di = 0
        while di < 32:
            w2_idx = d_ff * 32 + di
            g_W2[w2_idx] = g_W2[w2_idx] + ff_h[d_ff] * d_ff_out[di]
            di = di + 1
        g_b1[d_ff] = g_b1[d_ff] + d_ff_h[d_ff]
        d_ff = d_ff + 1

    di = 0
    while di < 32:
        g_b2[di] = g_b2[di] + d_ff_out[di]
        di = di + 1

    di = 0
    while di < 32:
        ao_idx = mask_pos * 32 + di
        d_ff = 0
        while d_ff < 64:
            w1_idx = di * 64 + d_ff
            g_W1[w1_idx] = g_W1[w1_idx] + attn_out[ao_idx] * d_ff_h[d_ff]
            d_ff = d_ff + 1
        di = di + 1

    di = 0
    while di < 32:
        acc = 0
        d_ff = 0
        while d_ff < 64:
            w1_idx = di * 64 + d_ff
            acc = acc + d_ff_h[d_ff] * W1[w1_idx]
            d_ff = d_ff + 1
        dao_idx = mask_pos * 32 + di
        d_attn_out[dao_idx] = acc + d_ff_out[di]
        di = di + 1

def backward_attention(mask_pos, tok_ids):
    d_concat_row = [0 for _ in range(32)]
    od = 0
    while od < 32:
        acc = 0
        id_o = 0
        while id_o < 32:
            wo_idx  = od * 32 + id_o
            dao_idx = mask_pos * 32 + id_o
            acc = acc + Wo[wo_idx] * d_attn_out[dao_idx]
            id_o = id_o + 1
        d_concat_row[od] = acc
        od = od + 1

    id_o = 0
    while id_o < 32:
        od = 0
        while od < 32:
            gwo_idx  = id_o * 32 + od
            dao2_idx = mask_pos * 32 + od
            g_Wo[gwo_idx] = g_Wo[gwo_idx] + d_concat_row[id_o] * d_attn_out[dao2_idx]
            od = od + 1
        id_o = id_o + 1

    di = 0
    while di < 32:
        acc = 0
        od = 0
        while od < 32:
            wq_idx = di * 32 + od
            acc = acc + d_concat_row[od] * Wq[wq_idx]
            od = od + 1
        dxe_idx = mask_pos * 32 + di
        d_x_emb[dxe_idx] = acc
        di = di + 1

    di = 0
    while di < 32:
        xe_idx = mask_pos * 32 + di
        od = 0
        while od < 32:
            gwq_idx = di * 32 + od
            g_Wq[gwq_idx] = g_Wq[gwq_idx] + x_emb[xe_idx] * d_concat_row[od]
            od = od + 1
        di = di + 1

    tok_id = tok_ids[mask_pos]
    di = 0
    while di < 32:
        gte_idx = tok_id * 32 + di
        dxe_idx = mask_pos * 32 + di
        g_tok_emb[gte_idx] = g_tok_emb[gte_idx] + d_x_emb[dxe_idx]
        di = di + 1

    di = 0
    while di < 32:
        gpe_idx = mask_pos * 32 + di
        g_pos_emb[gpe_idx] = g_pos_emb[gpe_idx] + d_x_emb[gpe_idx]
        di = di + 1

# =============================================================================
# SGD UPDATE
# =============================================================================

def sgd_step(lr):
    lr_buf[0] = lr
    sgd_update_GPU_1(tok_emb, g_tok_emb, lr_buf, 4096)
    sgd_update_GPU_1(pos_emb, g_pos_emb, lr_buf, 512)
    sgd_update_GPU_1(Wq,      g_Wq,      lr_buf, 1024)
    sgd_update_GPU_1(Wk,      g_Wk,      lr_buf, 1024)
    sgd_update_GPU_1(Wv,      g_Wv,      lr_buf, 1024)
    sgd_update_GPU_1(Wo,      g_Wo,      lr_buf, 1024)
    sgd_update_GPU_1(W1,      g_W1,      lr_buf, 2048)
    sgd_update_GPU_1(W2,      g_W2,      lr_buf, 2048)
    sgd_update_GPU_1(Wout,    g_Wout,    lr_buf, 4096)
    sgd_update_GPU_1(b1,      g_b1,      lr_buf, 64)
    sgd_update_GPU_1(b2,      g_b2,      lr_buf, 32)
    sgd_update_GPU_1(bout,    g_bout,    lr_buf, 128)

# =============================================================================
# TRAINING LOOP
# =============================================================================

step_tok_ids = [0 for _ in range(128)]
step_labels  = [0 for _ in range(8)]
thread_loss  = [0 for _ in range(8)]

epoch    = 0
step     = 0
avg_loss = 0

def run_sample(thread_id, tok_ids_flat, label_id, loss_out):
    sample_toks = [0 for _ in range(16)]
    ti = 0
    while ti < 16:
        tf_idx = thread_id * 16 + ti
        sample_toks[ti] = tok_ids_flat[tf_idx]
        ti = ti + 1

    forward_embed(sample_toks, 8)
    forward_attention()
    forward_ffn(8)
    forward_classifier()
    cross_entropy(softmax_out, label_id, loss_out)

    backward_classifier(label_id)
    backward_ffn(8)
    backward_attention(8, sample_toks)

# =============================================================================
# Main training execution
#
# There are EPOCHS * STEPS = 3 * 4 = 12 cycles total.
# Each threadStart runs one sample for its batch slot in the current cycle.
# threadOnEnd(..., 12) re-spawns the 8 threads after each of the first 11
# cycles (aggregating loss + applying SGD + zeroing grads each time), and on
# the 12th cycle executes its body one final time without re-spawning.
#
# A global cycle counter tracks which (epoch, step) the current round is for.
# =============================================================================

# Cycle counter: 0..11  (cycle = epoch * STEPS + step)
current_cycle = 0

def run_one_sample(thread_id):
    # Derive which step and epoch this cycle corresponds to
    step_id  = current_cycle
    si = 0
    while si < 128:
        ds_idx = step_id * 128 + si
        step_tok_ids[si] = dataset[ds_idx]
        si = si + 1

    lbl_idx = step_id * 8 + thread_id
    label_id = labels[lbl_idx]

    loss_out = 0
    run_sample(thread_id, step_tok_ids, label_id, loss_out)
    thread_loss[thread_id] = loss_out

# Zero gradients before the first cycle
zero_arr(g_tok_emb, 4096)
zero_arr(g_pos_emb, 512)
zero_arr(g_Wq,  1024)
zero_arr(g_Wk,  1024)
zero_arr(g_Wv,  1024)
zero_arr(g_Wo,  1024)
zero_arr(g_W1,  2048)
zero_arr(g_W2,  2048)
zero_arr(g_Wout, 4096)
zero_arr(g_b1,  64)
zero_arr(g_b2,  32)
zero_arr(g_bout, 128)

# ── 8 threads, one sample each, 12 cycles (3 epochs × 4 steps) ────────────
threadStart(t0) {
    run_one_sample(0)
}
threadStart(t1) {
    run_one_sample(1)
}
threadStart(t2) {
    run_one_sample(2)
}
threadStart(t3) {
    run_one_sample(3)
}
threadStart(t4) {
    run_one_sample(4)
}
threadStart(t5) {
    run_one_sample(5)
}
threadStart(t6) {
    run_one_sample(6)
}
threadStart(t7) {
    run_one_sample(7)
}

# ── After every cycle: aggregate loss, SGD step, zero grads, advance cycle ─
# threadParallelismCycle runs its body after EVERY one of the 12 cycles,
# re-spawning the 8 threads for cycles 1..11 and executing the body for the
# 12th time without re-spawning (training complete).
threadParallelismCycle(t0, t1, t2, t3, t4, t5, t6, t7, 12) {
    avg_loss = thread_loss[0] + thread_loss[1] + thread_loss[2] + thread_loss[3]
    avg_loss = avg_loss + thread_loss[4] + thread_loss[5] + thread_loss[6] + thread_loss[7]
    avg_loss = avg_loss / 8
    sgd_step(0.01)
    current_cycle = current_cycle + 1
    zero_arr(g_tok_emb, 4096)
    zero_arr(g_pos_emb, 512)
    zero_arr(g_Wq,  1024)
    zero_arr(g_Wk,  1024)
    zero_arr(g_Wv,  1024)
    zero_arr(g_Wo,  1024)
    zero_arr(g_W1,  2048)
    zero_arr(g_W2,  2048)
    zero_arr(g_Wout, 4096)
    zero_arr(g_b1,  64)
    zero_arr(g_b2,  32)
    zero_arr(g_bout, 128)
}

# Training complete – avg_loss holds the mean loss from the final cycle

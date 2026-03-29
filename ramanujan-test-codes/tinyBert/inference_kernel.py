# =============================================================================
# TinyBERT GPU Inference Kernel
# =============================================================================
#
# This is a STATIC file – never edit it directly.
# All weights are injected via CSV files; inference.py writes the CSVs and
# calls:
#
#   rj inference_kernel.py \
#      tok_emb.csv pos_emb.csv Wq.csv Wk.csv Wv.csv Wo.csv \
#      W1.csv W2.csv Wout.csv b1.csv b2.csv bout.csv seq_input.csv
#
# CSV injection (handled by Ramanujan's TranslateUtil.generateCsvInitPythonCode):
#   Weight CSVs   – 1 row × N columns → weight_name[flat_index]  (flat 1-D __global float*)
#   seq_input.csv – 1 row × 17 columns → seq_input[0][0..16]
#       seq_input[0][0..15]  = ASCII token IDs
#       seq_input[0][16]     = mask position
#
# After execution query the result via:
#   arr softmax_out 0  ...  arr softmax_out 127
#
# GPU kernels follow the Ramanujan _GPU_N naming convention:
#   def funcName_GPU_N(dataArg1, ..., rangeDim1, ..., rangeDimN)
#   The last N parameters are work-item index variables; their call-site values
#   become global_work_size[] for clEnqueueNDRangeKernel.
#
# Architecture: VOCAB=128  SEQ=16  DM=32  DFF=64  NH=4  DH=8
# =============================================================================

# ── Activation buffers (1D flat) ──────────────────────────────────────────────
x_emb       = [0 for _ in range(512)]
q_buf       = [0 for _ in range(512)]
k_buf       = [0 for _ in range(512)]
v_buf       = [0 for _ in range(512)]
attn_w      = [0 for _ in range(256)]
concat_buf  = [0 for _ in range(512)]
attn_out    = [0 for _ in range(512)]
ff_h        = [0 for _ in range(64)]
ff_out      = [0 for _ in range(32)]
logits      = [0 for _ in range(128)]
softmax_out = [0 for _ in range(128)]

# ── Input buffer (filled from seq_input CSV in the setup section below) ───────
tok_ids  = [0 for _ in range(16)]
mask_pos = 0

# ── Scalar buffers for passing values into GPU kernels ────────────────────────
mp_buf   = [0 for _ in range(1)]   # mask_pos
head_buf = [0 for _ in range(1)]   # current attention head index

# =============================================================================
# Weight matrices are NOT declared here – injected from CSV files by Ramanujan.
# Each CSV is a single-row file; the runtime exposes it as a flat 1-D array:
#
#   tok_emb[0..4095]   pos_emb[0..511]
#   Wq[0..1023]        Wk[0..1023]   Wv[0..1023]  Wo[0..1023]
#   W1[0..2047]        W2[0..2047]
#   Wout[0..4095]      b1[0..63]     b2[0..31]    bout[0..127]
#
# Access pattern inside GPU kernels: weight_name[flat_index]  (__global float*)
# The host-side setup loop still reads seq_input[0][i] (2-D CSV → tok_ids 1-D).
# EXP / SQRT are Ramanujan built-in host intrinsics (in-place).
# =============================================================================

# =============================================================================
# GPU KERNELS  (_GPU_1: one NDRange dimension; gid = work-item index)
# All data arguments must be arrays; the last parameter is the range variable.
# =============================================================================

# Embedding lookup: x_emb[gid] = tok_emb[tok_id*32 + dim] + pos_emb[gid]
# gid runs over SEQ*DM = 512 positions.  mp_buf[0] holds the mask position.
def embed_lookup_GPU_1(tok_ids, tok_emb, pos_emb, x_emb, mp_buf, gid):
    pos    = gid / 32
    dim    = gid - pos * 32
    tid    = tok_ids[pos]
    if pos == mp_buf[0]:
        tid = 0
    te_idx     = tid * 32 + dim
    x_emb[gid] = tok_emb[te_idx] + pos_emb[gid]

# Q/K/V linear projections DM→DM for all SEQ positions in parallel.
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
# attn_w[gid] = dot(q_head[t1], k_head[t2]) * scale
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

# Weighted context vector for one head.
# concat_buf[t1*32 + h_off + dh] = sum_t2( attn_w[t1*16+t2] * v_buf[t2*32+h_off+dh] )
# gid encodes (t1, dh): t1 = gid/8, dh = gid%8.  Range = SEQ*DH = 16*8 = 128.
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
    cb_idx            = t1 * 32 + h_off + dh
    concat_buf[cb_idx] = acc

# Output projection + residual add.
# attn_out[gid] = x_emb[gid] + sum_id_o( concat_buf[ao*32+id_o] * Wo[id_o*32+d_out] )
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

# FFN first layer with ReLU activation.
# ff_h[gid] = ReLU( sum_di( attn_out[mp*32+di] * W1[di*64+gid] ) + b1[gid] )
# gid runs over DFF = 64.  mp_buf[0] holds the mask position.
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

# FFN second layer.
# ff_out[gid] = sum_df( ff_h[df] * W2[df*32+gid] ) + b2[gid]
# gid runs over DM = 32.
def ffn2_GPU_1(ff_h, W2, b2, ff_out, gid):
    acc  = 0
    d_ff = 0
    while d_ff < 64:
        w2_idx = d_ff * 32 + gid
        acc    = acc + ff_h[d_ff] * W2[w2_idx]
        d_ff   = d_ff + 1
    ff_out[gid] = acc + b2[gid]

# Classifier linear layer.
# logits[gid] = sum_di( ff_out[di] * Wout[di*128+gid] ) + bout[gid]
# gid runs over VOCAB = 128.
def classifier_GPU_1(ff_out, Wout, bout, logits, gid):
    acc = 0
    di  = 0
    while di < 32:
        wo_idx = di * 128 + gid
        acc    = acc + ff_out[di] * Wout[wo_idx]
        di     = di + 1
    logits[gid] = acc + bout[gid]

# =============================================================================
# Host-side helpers  (softmax, layer_norm – use Ramanujan built-ins EXP / SQRT)
# These run sequentially on the host; they cannot be GPU kernels because they
# require sequential reduction (max, sum, sqrt).
# =============================================================================

def softmax_vec(vec, size, out_vec):
    mx = vec[0]
    si = 1
    while si < size:
        if vec[si] > mx:
            mx = vec[si]
        si = si + 1
    s  = 0
    si = 0
    while si < size:
        z  = vec[si] - mx
        ez = z
        EXP(ez)
        out_vec[si] = ez
        s  = s + ez
        si = si + 1
    si = 0
    while si < size:
        out_vec[si] = out_vec[si] / s
        si = si + 1

def layer_norm(vec, size, out_vec):
    mu = 0
    li = 0
    while li < size:
        mu = mu + vec[li]
        li = li + 1
    mu   = mu / size
    var2 = 0
    li   = 0
    while li < size:
        diff = vec[li] - mu
        var2 = var2 + diff * diff
        li   = li + 1
    var2 = var2 / size
    std  = var2
    SQRT(std)
    eps = 0.00001
    li  = 0
    while li < size:
        denom       = std + eps
        out_vec[li] = (vec[li] - mu) / denom
        li = li + 1

# Scratch buffers for host-side softmax / layer-norm loops
scores_row     = [0 for _ in range(16)]
softmax_scores = [0 for _ in range(16)]
ln_buf         = [0 for _ in range(32)]

# =============================================================================
# Forward-pass orchestration  (called inside threadStart on the device)
# =============================================================================

def forward_embed():
    mp_buf[0] = mask_pos
    embed_lookup_GPU_1(tok_ids, tok_emb, pos_emb, x_emb, mp_buf, 512)

def forward_attention():
    proj_qkv_GPU_1(x_emb, Wq, Wk, Wv, q_buf, k_buf, v_buf, 512)

    head = 0
    while head < 4:
        head_buf[0] = head

        # Scaled dot-product scores for this head (SEQ×SEQ = 256 elements)
        attn_score_GPU_1(q_buf, k_buf, attn_w, head_buf, 256)

        # Softmax each query row – sequential reduction, host side
        t1 = 0
        while t1 < 16:
            sr = 0
            while sr < 16:
                aw_idx        = t1 * 16 + sr
                scores_row[sr] = attn_w[aw_idx]
                sr = sr + 1
            softmax_vec(scores_row, 16, softmax_scores)
            sr = 0
            while sr < 16:
                aw_idx        = t1 * 16 + sr
                attn_w[aw_idx] = softmax_scores[sr]
                sr = sr + 1
            t1 = t1 + 1

        # Weighted context vectors for this head → concat_buf (SEQ*DH = 128)
        context_GPU_1(attn_w, v_buf, concat_buf, head_buf, 128)

        head = head + 1

    # Output projection + residual (SEQ*DM = 512 elements)
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
            ao_idx          = ao * 32 + di
            attn_out[ao_idx] = ln_buf[di]
            di = di + 1
        ao = ao + 1

def forward_ffn():
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

# ── Setup: copy input from the seq_input CSV ─────────────────────────────────
# seq_input is injected by Ramanujan from seq_input.csv:
#   seq_input[0][0..15] = token IDs,  seq_input[0][16] = mask position
si = 0
while si < 16:
    tok_ids[si] = seq_input[0][si]
    si = si + 1
mask_pos = seq_input[0][16]

# ── Execute forward pass ─────────────────────────────────────────────────────
forward_embed()
forward_attention()
forward_ffn()
forward_classifier()

# Result: softmax_out[0..127] holds the predicted probability for each ASCII token.

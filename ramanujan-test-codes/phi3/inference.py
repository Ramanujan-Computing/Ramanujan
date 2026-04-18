#!/usr/bin/env python3
"""
inference.py  –  Phi-3-mini-4k-instruct inference (NumPy + Ramanujan hybrid)
=============================================================================

Heavy matrix multiplications run in NumPy (fast, vectorised).
Element-wise GPU operations (RMS-norm, RoPE, attention, SiLU) can optionally
be dispatched to the Ramanujan 'rj' runtime via --rj to demonstrate
distributed GPU execution.

Usage
-----
  python inference.py "Hello world"                      # next-token prediction
  python inference.py --generate 30 "Hello world"        # generate 30 tokens
  python inference.py --chat "Explain gravity"            # Phi-3 chat template
  python inference.py --layers 2 "Hello"                  # first 2 layers only
  python inference.py --rj "Hello world"                  # run GPU ops on Ramanujan
  python inference.py                                     # interactive mode
"""

import json, math, os, re, struct, subprocess, shutil, sys, time
import numpy as np

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR  = os.path.join(os.path.dirname(SCRIPT_DIR), "..", "..", "Phi-3-mini-4k-instruct")

# ── Architecture ──────────────────────────────────────────────────────────────
VOCAB      = 32064
HIDDEN     = 3072
INTER      = 8192
HEADS      = 32
HD         = 96          # HIDDEN // HEADS
HD_HALF    = 48          # HD // 2
MAX_SEQ    = 64
ROPE_THETA = 10000.0
RMS_EPS    = 1e-5
NUM_LAYERS = 32
EOS_ID     = 32000
SCALE      = 1.0 / math.sqrt(HD)   # 0.10206207…


# =============================================================================
# Weight loading  (BF16 safetensors → float32 NumPy)
# =============================================================================

def _bf16_to_f32(raw: bytes, shape):
    u16 = np.frombuffer(raw, dtype=np.uint16)
    return (u16.astype(np.uint32) << 16).view(np.float32).reshape(shape)

_DTYPE_NP = {"F32": np.float32, "F16": np.float16, "I32": np.int32,
             "I64": np.int64, "U8": np.uint8}

def load_weights(model_dir: str):
    """Load all tensors from *.safetensors into a dict[str, ndarray(f32)]."""
    weights = {}
    for fname in sorted(os.listdir(model_dir)):
        if not fname.endswith(".safetensors"):
            continue
        path = os.path.join(model_dir, fname)
        print(f"  Loading {fname} …", end="", flush=True)
        with open(path, "rb") as fh:
            hlen = struct.unpack("<Q", fh.read(8))[0]
            header = json.loads(fh.read(hlen))
            data_start = 8 + hlen
            for key, meta in header.items():
                if key == "__metadata__":
                    continue
                fh.seek(data_start + meta["data_offsets"][0])
                raw = fh.read(meta["data_offsets"][1] - meta["data_offsets"][0])
                dt = meta["dtype"]
                sh = meta["shape"]
                if dt == "BF16":
                    weights[key] = _bf16_to_f32(raw, sh)
                elif dt in _DTYPE_NP:
                    weights[key] = np.frombuffer(raw, dtype=_DTYPE_NP[dt]).reshape(sh).astype(np.float32)
                else:
                    weights[key] = np.frombuffer(raw, dtype=np.float16).reshape(sh).astype(np.float32)
        print(f"  ({len([k for k in header if k != '__metadata__'])} tensors)")
    print(f"  Total: {len(weights)} tensors loaded")
    return weights


# =============================================================================
# Tokenizer  (uses HuggingFace `tokenizers` for proper BPE)
# =============================================================================

class Tokenizer:
    def __init__(self, model_dir: str):
        self.bos_id = 1
        self.eos_id = EOS_ID
        self._tok = None
        self.id_to_token = {}
        tok_path = os.path.join(model_dir, "tokenizer.json")
        try:
            from tokenizers import Tokenizer as HFTok
            self._tok = HFTok.from_file(tok_path)
        except Exception:
            pass
        # Build id→token map for decoding
        with open(tok_path) as f:
            data = json.load(f)
        vocab = data.get("model", {}).get("vocab", {})
        if isinstance(vocab, dict):
            self.id_to_token = {int(v): k for k, v in vocab.items()}
        for at in data.get("added_tokens", []):
            self.id_to_token[at["id"]] = at["content"]

    def encode(self, text: str) -> list:
        if self._tok:
            return [self.bos_id] + self._tok.encode(text).ids
        raise RuntimeError("tokenizers library required – pip install tokenizers")

    def decode_token(self, tid: int) -> str:
        t = self.id_to_token.get(tid, f"<{tid}>")
        return t.replace("▁", " ")

    def decode(self, ids: list) -> str:
        return "".join(self.decode_token(i) for i in ids)


def phi3_chat_prompt(msg: str) -> str:
    return f"<|user|>\n{msg}<|end|>\n<|assistant|>\n"


# =============================================================================
# RoPE tables  (precomputed once)
# =============================================================================

def build_rope_tables(max_seq=MAX_SEQ, hd_half=HD_HALF, theta=ROPE_THETA):
    """Return (cos, sin) each shape [max_seq, hd_half]."""
    inv_freq = 1.0 / (theta ** (np.arange(0, hd_half, dtype=np.float32) / hd_half))
    pos = np.arange(max_seq, dtype=np.float32)
    angles = np.outer(pos, inv_freq)        # [max_seq, hd_half]
    return np.cos(angles), np.sin(angles)


# =============================================================================
# NumPy forward pass  (pure Python, no rj dependency)
# =============================================================================

def rms_norm(x: np.ndarray, weight: np.ndarray) -> np.ndarray:
    """RMS LayerNorm.  x: [seq, hidden]  weight: [hidden]"""
    rms = np.sqrt(np.mean(x * x, axis=-1, keepdims=True) + RMS_EPS)
    return (x / rms) * weight

def apply_rope(q: np.ndarray, k: np.ndarray,
               cos_table: np.ndarray, sin_table: np.ndarray,
               seq_len: int):
    """Apply split-half RoPE.  q, k: [seq, heads, hd]"""
    cos = cos_table[:seq_len, :][:, np.newaxis, :]   # [seq,1,hd_half]
    sin = sin_table[:seq_len, :][:, np.newaxis, :]
    def rotate(x):
        x1, x2 = x[..., :HD_HALF], x[..., HD_HALF:]
        return np.concatenate([x1 * cos - x2 * sin,
                               x2 * cos + x1 * sin], axis=-1)
    return rotate(q), rotate(k)

def attention(q, k, v, seq_len):
    """Multi-head attention.  q,k,v: [seq, heads, hd]  → [seq, heads, hd]"""
    # scores: [heads, seq, seq]
    scores = np.einsum("shd,thd->hst", q, k) * SCALE
    # Causal mask
    mask = np.triu(np.full((seq_len, seq_len), -1e9, dtype=np.float32), k=1)
    scores = scores + mask[np.newaxis, :, :]
    # Softmax
    scores = scores - scores.max(axis=-1, keepdims=True)
    e = np.exp(scores)
    scores = e / (e.sum(axis=-1, keepdims=True) + 1e-12)
    # Context
    ctx = np.einsum("hst,thd->shd", scores, v)
    return ctx

def forward_layer(hidden, layer_w, rope_cos, rope_sin, seq_len):
    """One transformer layer.  hidden: [seq, hidden]"""
    # ── Self-attention ──
    normed = rms_norm(hidden, layer_w["iln"])
    qkv = normed @ layer_w["qkv"].T                     # [seq, 9216]
    q = qkv[:, :HIDDEN].reshape(seq_len, HEADS, HD)
    k = qkv[:, HIDDEN:2*HIDDEN].reshape(seq_len, HEADS, HD)
    v = qkv[:, 2*HIDDEN:3*HIDDEN].reshape(seq_len, HEADS, HD)
    q, k = apply_rope(q, k, rope_cos, rope_sin, seq_len)
    ctx = attention(q, k, v, seq_len)                    # [seq, heads, hd]
    attn_out = ctx.reshape(seq_len, HIDDEN) @ layer_w["o"].T  # [seq, hidden]
    hidden = hidden + attn_out

    # ── MLP ──
    normed = rms_norm(hidden, layer_w["pln"])
    gu = normed @ layer_w["gu"].T                        # [seq, 16384]
    gate = gu[:, :INTER]
    up   = gu[:, INTER:]
    gate = gate * (1.0 / (1.0 + np.exp(-gate)))         # SiLU
    mlp_out = (gate * up) @ layer_w["down"].T            # [seq, hidden]
    hidden = hidden + mlp_out
    return hidden


def forward(tok_ids, weights, num_layers, rope_cos, rope_sin):
    """Full Phi-3 forward pass.  Returns logits [vocab]."""
    seq_len = len(tok_ids)
    embed = weights["model.embed_tokens.weight"]         # [vocab, hidden]
    hidden = embed[tok_ids]                              # [seq, hidden]

    for i in range(num_layers):
        pfx = f"model.layers.{i}"
        lw = {
            "iln":  weights[f"{pfx}.input_layernorm.weight"],
            "pln":  weights[f"{pfx}.post_attention_layernorm.weight"],
            "qkv":  weights[f"{pfx}.self_attn.qkv_proj.weight"],
            "o":    weights[f"{pfx}.self_attn.o_proj.weight"],
            "gu":   weights[f"{pfx}.mlp.gate_up_proj.weight"],
            "down": weights[f"{pfx}.mlp.down_proj.weight"],
        }
        hidden = forward_layer(hidden, lw, rope_cos, rope_sin, seq_len)

    # Final norm + LM head
    hidden = rms_norm(hidden, weights["model.norm.weight"])
    last_h = hidden[-1]                                  # [hidden]
    logits = last_h @ weights["lm_head.weight"].T        # [vocab]
    return logits


# =============================================================================
# Ramanujan rj helpers  (for --rj demo mode)
# =============================================================================

def _find_java() -> str:
    jh = os.environ.get("JAVA_HOME", "")
    if jh:
        c = os.path.join(jh, "bin", "java")
        if os.path.isfile(c):
            return c
    return "java"

def _rj_cmd() -> list:
    if shutil.which("rj"):
        return ["rj"]
    ws = os.environ.get("RAMANUJAN_WS", "")
    if ws:
        jar = os.path.join(ws, "developer-console-1.0-SNAPSHOT-fat.jar")
        if os.path.exists(jar):
            return [_find_java(), "-Xmx4g", "-jar", jar]
    return ["rj"]


def _write_flat_csv(name: str, arr: np.ndarray, out_dir: str) -> str:
    """Write a 1-D float array as a single-row CSV. Returns path."""
    path = os.path.join(out_dir, f"{name}.csv")
    flat = arr.flatten().astype(np.float32)
    with open(path, "w") as f:
        f.write(",".join(f"{v:.8f}" for v in flat))
    return path


def run_rj_gpu_demo(hidden_np, norm_w, rope_cos_flat, rope_sin_flat,
                    q_flat, k_flat, v_flat, seq_len):
    """
    Run one pre-attention GPU phase through Ramanujan rj as a demonstration.
    Sends small arrays (~2 MB), runs RMS-norm + RoPE + attention on rj,
    reads back a few verification values.
    """
    tmp = os.path.join(SCRIPT_DIR, "_rj_tmp")
    os.makedirs(tmp, exist_ok=True)

    kernel = os.path.join(SCRIPT_DIR, "inference_kernel.py")
    if not os.path.exists(kernel):
        print("  [rj] inference_kernel.py not found – skipping rj demo")
        return

    # Write small CSV inputs (bare filenames – rj derives var names from them)
    csv_names = []
    for name, arr in [
        ("hidden_in", hidden_np[:seq_len].flatten()),
        ("norm_w",    norm_w),
        ("rope_cos",  rope_cos_flat),
        ("rope_sin",  rope_sin_flat),
        ("q_in",      q_flat),
        ("k_in",      k_flat),
        ("v_in",      v_flat),
        ("params",    np.array([seq_len, 0, 0, 0], dtype=np.float32)),
    ]:
        _write_flat_csv(name, arr, tmp)
        csv_names.append(f"{name}.csv")

    # rj uses filename (not path) as variable name, so we must pass bare names
    # and set cwd to the directory containing them
    cmd = _rj_cmd() + [kernel] + csv_names
    queries = "var actual_seq\narr concat 0\narr concat 1\nexit\n"

    try:
        proc = subprocess.run(cmd, input=queries, capture_output=True,
                              text=True, timeout=120, cwd=tmp)
        out = proc.stdout + "\n" + (proc.stderr or "")
        if "actual_seq" in out:
            print(f"  [rj] ✓ Ramanujan GPU kernel executed successfully")
            for line in out.split("\n"):
                line = line.strip()
                if "actual_seq" in line or "concat" in line:
                    print(f"  [rj]   {line}")
        elif proc.stderr:
            print(f"  [rj] Kernel error: {proc.stderr[:200]}")
        else:
            print(f"  [rj] No output (kernel may still be compiling)")
    except subprocess.TimeoutExpired:
        print("  [rj] Timed out (120s)")
    except FileNotFoundError:
        print("  [rj] rj runtime not found")


# =============================================================================
# Display helpers
# =============================================================================

def show_prediction(prompt, logits, tokenizer, top_k=10):
    best_id = int(np.argmax(logits))
    best_val = float(logits[best_id])
    token = tokenizer.decode_token(best_id)

    print(f"\n  Prompt       : {prompt}")
    print(f"  Predicted ID : {best_id}")
    print(f"  Predicted tok: {repr(token)}")
    print(f"  Max logit    : {best_val:.4f}")
    print(f"  Completion   : {prompt}{token}")

    # Top-k
    mx = logits.max()
    e = np.exp(logits - mx)
    probs = e / e.sum()
    top_idx = np.argsort(probs)[::-1][:top_k]
    print(f"\n  {'Rank':<5}  {'Token':<20}  {'ID':>6}  {'Prob':>8}")
    print("  " + "─" * 52)
    for rank, tid in enumerate(top_idx, 1):
        tok = repr(tokenizer.decode_token(int(tid)))
        p = probs[tid]
        bar = "█" * max(1, int(p * 40))
        print(f"  #{rank:<4}  {tok:<20}  {tid:6d}  {p*100:7.2f}%  {bar}")


# =============================================================================
# Main
# =============================================================================

def main():
    num_layers = NUM_LAYERS
    gen_tokens = 0
    use_chat = False
    use_rj = False
    prompts = []

    args = sys.argv[1:]
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--layers":   num_layers = int(args[i+1]); i += 2
        elif a == "--generate": gen_tokens = int(args[i+1]); i += 2
        elif a == "--chat":   use_chat = True; i += 1
        elif a == "--rj":     use_rj = True; i += 1
        else:                 prompts.append(a); i += 1

    # ── Load model weights ──
    print("Loading model weights …", flush=True)
    t0 = time.time()
    weights = load_weights(MODEL_DIR)
    print(f"  Weights loaded in {time.time()-t0:.1f}s\n")

    # ── RoPE tables ──
    rope_cos, rope_sin = build_rope_tables()

    # ── Tokenizer ──
    tokenizer = Tokenizer(MODEL_DIR)
    print(f"  Tokenizer ready ({len(tokenizer.id_to_token)} tokens)\n")

    # ── Check layer count ──
    max_avail = 0
    while f"model.layers.{max_avail}.input_layernorm.weight" in weights:
        max_avail += 1
    if num_layers > max_avail:
        print(f"  [WARN] Only {max_avail} layers in model, using {max_avail}")
        num_layers = max_avail

    def run_prompt(prompt_text):
        if use_chat:
            formatted = phi3_chat_prompt(prompt_text)
        else:
            formatted = prompt_text

        tok_ids = tokenizer.encode(formatted)
        if len(tok_ids) > MAX_SEQ:
            print(f"  [WARN] Truncating {len(tok_ids)} → {MAX_SEQ} tokens")
            tok_ids = tok_ids[:MAX_SEQ]
        print(f"  Tokens: {len(tok_ids)}  |  Layers: {num_layers}")

        t0 = time.time()
        logits = forward(tok_ids, weights, num_layers, rope_cos, rope_sin)
        dt = time.time() - t0
        print(f"  Forward pass: {dt:.2f}s")

        # Optional rj demo
        if use_rj:
            print("  Running Ramanujan GPU demo (layer 0 pre-attention) …")
            # Prepare rj inputs from the first layer
            pfx = "model.layers.0"
            normed = rms_norm(weights["model.embed_tokens.weight"][tok_ids],
                              weights[f"{pfx}.input_layernorm.weight"])
            qkv = normed @ weights[f"{pfx}.self_attn.qkv_proj.weight"].T
            sl = len(tok_ids)
            run_rj_gpu_demo(
                weights["model.embed_tokens.weight"][tok_ids],
                weights[f"{pfx}.input_layernorm.weight"],
                rope_cos[:sl].flatten(), rope_sin[:sl].flatten(),
                qkv[:, :HIDDEN].flatten(),
                qkv[:, HIDDEN:2*HIDDEN].flatten(),
                qkv[:, 2*HIDDEN:].flatten(),
                sl,
            )

        return logits, formatted

    # ── Single prompt ──
    if prompts:
        prompt = " ".join(prompts)
        if gen_tokens > 0:
            # Auto-regressive generation
            formatted = phi3_chat_prompt(prompt) if use_chat else prompt
            tok_ids = tokenizer.encode(formatted)
            if len(tok_ids) > MAX_SEQ:
                tok_ids = tok_ids[:MAX_SEQ]
            generated = []
            print(f"\n  Generating up to {gen_tokens} tokens …")
            print(f"  Output: ", end="", flush=True)
            for step in range(gen_tokens):
                if len(tok_ids) >= MAX_SEQ:
                    print(f"\n  [MAX_SEQ reached]")
                    break
                logits = forward(tok_ids, weights, num_layers, rope_cos, rope_sin)
                best = int(np.argmax(logits))
                if best == EOS_ID:
                    print(" <|end|>")
                    break
                print(tokenizer.decode_token(best), end="", flush=True)
                generated.append(best)
                tok_ids.append(best)
            print(f"\n  Generated {len(generated)} tokens")
        else:
            logits, formatted = run_prompt(prompt)
            show_prediction(formatted, logits, tokenizer)
        return

    # ── Interactive ──
    print("╔══════════════════════════════════════════════════════════╗")
    print("║  Phi-3-mini-4k-instruct · NumPy + Ramanujan Inference  ║")
    print("╚══════════════════════════════════════════════════════════╝")
    print("Type a prompt and press Enter. 'q' to quit.\n")
    while True:
        try:
            prompt = input("Prompt> ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nGoodbye."); break
        if prompt.lower() in ("q", "quit", "exit"):
            break
        if not prompt:
            continue
        logits, formatted = run_prompt(prompt)
        show_prediction(formatted, logits, tokenizer)
        print()


if __name__ == "__main__":
    main()

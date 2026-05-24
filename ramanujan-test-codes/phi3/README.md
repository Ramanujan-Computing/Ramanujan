# Phi-3-mini-4k-instruct on Ramanujan

Runs Microsoft's Phi-3-mini-4k-instruct entirely on the Ramanujan
runtime. The Python side handles tokenization and weight conversion; everything
else — prefill, KV-cache build, and autoregressive decoding — executes as
Ramanujan GPU kernels.

## Quick start

```bash
# 1. Convert weights (one-time, ~20 min, needs ~15 GB disk)
python3 convert_to_csv_4bit.py

# 2. Generate binary sidecars (one-time, ~5 min — avoids JVM OOM)
python3 generate_bin_sidecars.py

# 3. Run inference
python3 run_phi3_4bit.py "What is the capital of France?" --n-tokens 80
```

## Weight conversion

### `convert_to_csv_4bit.py`

Reads the two safetensors shards
(`model-00001-of-00002.safetensors`, `model-00002-of-00002.safetensors`) and
writes everything to `phi3_weights_csv/`.

**What it produces:**

| File(s) | Description |
|---|---|
| `wte.npy` | Full embedding table (32064 × 3072 float32) — loaded by Python for prompt embedding |
| `wte_1.csv`, `wte_2.csv` | Same table split at token 16000 — used by the kernel for next-token embedding |
| `lm_head_1.csv`, `lm_head_2.csv` | LM head weights, same split (not tied to embeddings) |
| `ln_f_g.csv` | Final RMSNorm scale |
| `cos_cache.csv`, `sin_cache.csv` | RoPE cache [4096 × 48], precomputed at export time |
| `l{i}_{stem}.csv` | Per-layer weights for all 32 layers (10 files each, see below) |

**Per-layer stems** (i = 0 … 31):

```
l{i}_qkv_packed   l{i}_qkv_scales
l{i}_o_packed     l{i}_o_scales
l{i}_gate_up_packed  l{i}_gate_up_scales
l{i}_down_packed  l{i}_down_scales
l{i}_ln1_g        l{i}_ln2_g
```

**4-bit quantization scheme:**

Each weight matrix is quantized with symmetric per-row absmax scaling:

```
scale  = max(|row|) / 7.0          # float32 per output row
q      = round(w / scale)           # clipped to [-8, 7]
stored = q + 8                      # unsigned [0, 15], 4 bits/value
```

Six 4-bit values are packed into one float32 using positional notation
(powers of 16: 1, 16, 256, 4096, 65536, 1048576). The kernel unpacks them at
runtime on the GPU. Input features are zero-padded to a multiple of 6 before
packing.

### `generate_bin_sidecars.py`

Converts every `.csv` in the weights directory to a raw little-endian float32
binary (`.bin`). The JVM fast-path in `ExecutorImpl.createJson()` checks for a
same-name `.bin` and, when present, replaces the full CSV string with a tiny
dimension stub — avoiding the heap OOM that large weight CSVs would otherwise
cause.

```bash
python3 generate_bin_sidecars.py [--weights-dir phi3_weights_csv] [--force]
```

Skips files where the `.bin` is already newer than the `.csv`. Run with
`--force` to regenerate all.

## Transformer architecture

Phi-3-mini-4k-instruct is a 3.8 B-parameter decoder-only transformer:

| Dimension | Value |
|---|---|
| Hidden size | 3072 |
| Layers | 32 |
| Attention heads (Q) | 32 |
| KV heads | 32 (MHA, no GQA) |
| Head dimension | 96 |
| Intermediate size | 8192 (each of gate/up branch) |
| Vocabulary | 32064 |
| Max context | 4096 (RoPE cache); kernel cap 1024 |
| Normalization | RMSNorm (ε = 1e-5) |
| Activation | SwiGLU (SiLU gate × up projection) |
| Position encoding | RoPE, base 10000, rotary dim 48 |

### Per-layer execution in the kernel

Each of the 32 layers runs the same sequence of GPU kernels:

```
RMSNorm(hidden) → h_ln1

4-bit matmul: h_ln1 × QKV_packed → qkv_buf   [seq × 9216]
RoPE on Q and K, write K/V into per-layer KV cache

Causal attention (prefill: seq × seq; decode: 1 × seq):
  scores = Q · K^T / sqrt(96), masked, softmax
  attn_out = scores · V

4-bit matmul: attn_out × O_packed → h_attn_buf
hidden += h_attn_buf                           (residual)

RMSNorm(hidden) → h_ln2
4-bit matmul: h_ln2 × gate_up_packed → h_ff_buf  [seq × 16384]
SiLU(h_ff_buf[:8192]) * h_ff_buf[8192:]       (SwiGLU)
4-bit matmul: h_ff_buf × down_packed → h_out_buf
hidden += h_out_buf                            (residual)
```

### Uber loop: prefill + autoregressive decode

The kernel `phi3_transformer_stack_4bit.py` runs both phases in a single
invocation:

1. **Prefill** — processes all prompt tokens at once (all rows of the hidden
   matrix). Builds the KV cache for each layer.
2. **Decode loop** — runs `n_tokens` iterations. Each iteration embeds the
   last token, runs the full 32-layer stack on just that one row, computes
   logits, runs argmax, stores the token, and increments the sequence counter.

KV caches are sized at 1024 × 3072 floats per layer. Total prompt + generated
tokens must not exceed 1024.

## Running inference

### Prerequisites

- Java 8 (Corretto or equivalent); JAVA_HOME must point to it
- Built Ramanujan fat JAR (see repo README); RAMANUJAN_FAT_JAR or the default
  path `~/Desktop/ws/developer-console-1.0-SNAPSHOT-fat.jar`
- `transformers` installed for the HuggingFace tokenizer:
  `pip install transformers`
- Weights converted and `.bin` sidecars generated (see above)

### Local JVM

Starts a persistent Ramanujan JVM server on the local machine, runs the
kernel, then shuts the server down.

```bash
python3 run_phi3_4bit.py "Explain RoPE embeddings in one paragraph" \
    --n-tokens 120 \
    --weights-dir phi3_weights_csv
```

Override defaults with:

```bash
python3 run_phi3_4bit.py "..." \
    --java-home /path/to/jdk8 \
    --rj-ws /tmp/ramanujan_ws \
    --weights-dir /data/phi3_weights_csv
```

Environment variables are also accepted: `JAVA_HOME`, `RAMANUJAN_WS`,
`RAMANUJAN_FAT_JAR`.

### Homelab server

When a homelab Ramanujan server is already running, skip the local JVM
entirely:

```bash
python3 run_phi3_4bit.py "What is 17 * 23?" \
    --homelab \
    --homelab-url http://homelab.local:8888 \
    --n-tokens 40
```

`--homelab` POSTs to `/orchestrator/run` (blocks until the kernel finishes)
and then `/orchestrator/dump` for each output array. The remote server keeps
running after the script exits. `--java-home` and `--rj-ws` are ignored in
this mode.

### All flags

```
positional:
  prompt                      Input text

optional:
  --n-tokens INT              Tokens to generate (default: 40)
  --weights-dir PATH          Weight CSV directory (default: phi3_weights_csv)
  --java-home PATH            Path to JDK 8 home (default: $JAVA_HOME)
  --rj-ws PATH                Ramanujan workspace dir (default: $RAMANUJAN_WS or /tmp)
  --homelab                   Use a remote homelab server instead of local JVM
  --homelab-url URL           Homelab base URL (default: http://localhost:8888)
```

## File overview

| File | Purpose |
|---|---|
| `convert_to_csv_4bit.py` | Export + quantize safetensors weights to CSV |
| `generate_bin_sidecars.py` | Build `.bin` fast-path files from CSVs |
| `phi3_transformer_stack_4bit.py` | Ramanujan GPU kernel (prefill + decode loop) |
| `run_phi3_4bit.py` | Orchestrator: tokenize, embed, run kernel, decode output |

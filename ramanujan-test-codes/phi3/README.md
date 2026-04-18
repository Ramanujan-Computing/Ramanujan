# Phi-3-mini-4k-instruct on Ramanujan

End-to-end inference of Microsoft's **Phi-3-mini-4k-instruct** (3.8B parameters) using a hybrid **NumPy + Ramanujan GPU** architecture.

---

## Table of Contents

1. [Model Overview](#model-overview)
2. [Obtaining the Weights](#obtaining-the-weights)
3. [Weight Conversion and Sharding](#weight-conversion-and-sharding)
4. [Architecture: Hybrid NumPy + Ramanujan](#architecture-hybrid-numpy--ramanujan)
5. [Ramanujan GPU Kernel](#ramanujan-gpu-kernel)
6. [Running Inference](#running-inference)
7. [Performance](#performance)
8. [File Reference](#file-reference)
9. [Troubleshooting](#troubleshooting)

---

## Model Overview

| Parameter | Value |
|---|---|
| Vocabulary | 32064 tokens |
| Hidden dimension | 3072 |
| Intermediate (FFN) | 8192 |
| Attention heads | 32 |
| Head dimension | 96 |
| Layers | 32 |
| Max sequence length | 64 (configurable) |
| Precision (on-disk) | BFloat16 |
| Total size | 7.1 GB (2 safetensors shards, 195 tensors) |
| Activation | SiLU / SwiGLU |
| Norm | RMSNorm (eps = 1e-5) |
| Position encoding | RoPE (theta = 10000, split-half) |

---

## Obtaining the Weights

The model weights live in two safetensors shards:

```
Phi-3-mini-4k-instruct/
  model-00001-of-00002.safetensors   (4.0 GB)
  model-00002-of-00002.safetensors   (3.1 GB)
  model.safetensors.index.json       (shard map)
```

### Download methods tried (in order)

1. **git lfs pull** -- timed out / hung on macOS.
2. **huggingface-cli download** -- returned pointer files, not actual weights.
3. **Direct curl** (the method that worked):

```bash
curl -L -o model-00001-of-00002.safetensors \
  "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct/resolve/main/model-00001-of-00002.safetensors"
curl -L -o model-00002-of-00002.safetensors \
  "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct/resolve/main/model-00002-of-00002.safetensors"
```

---

## Weight Conversion and Sharding

### BFloat16 to Float32

NumPy has no native BFloat16 dtype. We convert at load time using a bit-shift trick:

```python
u16 = np.frombuffer(raw_bytes, dtype=np.uint16)
f32 = (u16.astype(np.uint32) << 16).view(np.float32).reshape(shape)
```

Each BF16 value is a truncated IEEE-754 float32. Zero-padding the lower 16 bits recovers the float32 value exactly.

### Weight organisation per layer

Each of the 32 transformer layers uses these weight matrices:

| Short name | Safetensors key | Shape | Description |
|---|---|---|---|
| qkv | `model.layers.{i}.self_attn.qkv_proj.weight` | [9216, 3072] | Fused Q+K+V projection |
| o | `model.layers.{i}.self_attn.o_proj.weight` | [3072, 3072] | Output projection |
| gu | `model.layers.{i}.mlp.gate_up_proj.weight` | [16384, 3072] | Fused gate+up projection |
| down | `model.layers.{i}.mlp.down_proj.weight` | [3072, 8192] | Down projection |
| iln | `model.layers.{i}.input_layernorm.weight` | [3072] | Input RMS-norm weights |
| pln | `model.layers.{i}.post_attention_layernorm.weight` | [3072] | Post-attention RMS-norm |

Plus global weights:

| Short name | Key | Shape |
|---|---|---|
| embed | `model.embed_tokens.weight` | [32064, 3072] |
| final_norm | `model.norm.weight` | [3072] |
| lm_head | `lm_head.weight` | [32064, 3072] |

### CSV extraction (for Ramanujan rj)

`extract_weights.py` was used to dump all weights to flat CSV files:

```bash
python extract_weights.py /path/to/Phi-3-mini-4k-instruct
python extract_weights.py --layers 2 /path/to/model   # partial, for testing
```

This produced 197 CSV files totalling 41 GB in `weights/`. Each CSV uses multi-row format (one float per line) since the Ramanujan Java runtime Scanner hit OOM on single-line CSVs with billions of characters.

**Important:** The main `inference.py` does NOT use these CSV files. It loads safetensors directly. The CSVs are only needed if you want to inject weights into the rj runtime for full GPU execution (currently impractical at 41 GB).

### Sharding for Ramanujan GPU demo

For the `--rj` demo mode, `inference.py` extracts small, per-operation CSVs into a temporary `_rj_tmp/` directory:

| File | Contents | Size |
|---|---|---|
| hidden_in.csv | Flat hidden state [SEQ x 3072] | ~2 MB |
| norm_w.csv | RMS-norm weights [3072] | ~25 KB |
| rope_cos.csv | RoPE cos table [SEQ x 48] | ~10 KB |
| rope_sin.csv | RoPE sin table [SEQ x 48] | ~10 KB |
| q_in.csv | Q vectors [SEQ x 3072] | ~2 MB |
| k_in.csv | K vectors [SEQ x 3072] | ~2 MB |
| v_in.csv | V vectors [SEQ x 3072] | ~2 MB |
| params.csv | [actual_seq, mode, 0, 0] | ~30 B |

These are written with bare filenames (no path prefix) and the rj process is launched with `cwd=_rj_tmp/`. This is critical because rj uses the full path as the internal variable name: `weights/l0_iln.csv` becomes variable `weights_l0_iln`, not `l0_iln`.

---

## Architecture: Hybrid NumPy + Ramanujan

### Why hybrid?

The Ramanujan rj runtime excels at distributing element-wise GPU kernels across devices. However, it must parse every CSV value into memory before execution. Loading 41 GB of weight CSVs is impractical (OOM, multi-minute parse times). The solution:

| Operation | Engine | Reason |
|---|---|---|
| Embedding lookup | NumPy | Table lookup, not parallelisable |
| Matrix multiplications (QKV, O, gate/up, down, LM head) | NumPy | Weights too large for rj CSV injection |
| RMS normalisation | Ramanujan GPU | Element-wise, small data |
| RoPE rotation | Ramanujan GPU | Element-wise, small data |
| Attention scores and softmax | Ramanujan GPU | Per-head parallelisable |
| SiLU x gate | Ramanujan GPU | Element-wise activation |

### Forward pass pipeline (per layer)

```
Input hidden [seq, 3072]
  |
  +-- RMS-norm with input_layernorm weights
  +-- QKV projection: hidden @ qkv_proj.T -> [seq, 9216]
  |     split -> Q[seq,3072], K[seq,3072], V[seq,3072]
  +-- RoPE rotation (split-half cos/sin)
  +-- Reshape to heads -> Q[32,seq,96], K[32,seq,96], V[32,seq,96]
  +-- Attention: softmax(Q.K^T / sqrt(96)).V -> [32,seq,96]
  +-- Reshape -> [seq, 3072]
  +-- Output projection: attn_out @ o_proj.T
  +-- + residual connection
  |
  +-- RMS-norm with post_attention_layernorm weights
  +-- Gate+Up projection -> [seq, 16384], split -> gate[seq,8192], up[seq,8192]
  +-- SiLU(gate) x up
  +-- Down projection -> [seq, 3072]
  +-- + residual connection
       |
       -> Output hidden [seq, 3072]
```

With `--rj`, the element-wise ops (RMS-norm, RoPE, attention, SiLU) for layer 0 are dispatched to the Ramanujan GPU runtime as a demonstration.

---

## Ramanujan GPU Kernel

`inference_kernel.py` is the Ramanujan kernel that runs on the rj distributed GPU runtime.

### What it does

1. **RMS Normalisation** -- `rms_apply_GPU_1` kernel: normalises the hidden state
2. **RoPE Rotation** -- `rope_GPU_1` kernel: applies rotary position embeddings to Q and K
3. **Attention Scores** -- `attn_score_GPU_1` kernel: computes Q.K^T scaled dot products
4. **Attention Context** -- `attn_ctx_GPU_1` kernel: multiplies softmax(scores) x V

### Ramanujan-specific constraints discovered

| Constraint | Problem | Solution |
|---|---|---|
| Variable naming | rj uses the full CSV path as the variable name | Write CSVs with bare filenames, set cwd to the CSV directory |
| Array indexing | Computed expressions like `arr[i * 3072]` fail | Use intermediate variables: `idx = i * 3072; arr[idx]` |
| Data injection | 41 GB of CSV weights cause OOM in Java Scanner | Hybrid architecture: only inject small tensors (~8 MB total) |
| Output | No `print()` or file I/O from kernel code | Query via `var name` and `arr name index` commands |
| Query protocol | Only 3 commands: var, arr, exit | Pipe query strings to rj stdin |

### How rj is invoked

```bash
export RAMANUJAN_WS=/path/to/ws
cd _rj_tmp/
printf "var actual_seq\narr concat 0\narr concat 1\nexit\n" | \
  java -jar developer-console-1.0-SNAPSHOT-fat.jar \
    inference_kernel.py hidden_in.csv norm_w.csv rope_cos.csv \
    rope_sin.csv q_in.csv k_in.csv v_in.csv params.csv
```

The runtime:
1. Parses the kernel Python file
2. Loads all CSV files into named arrays
3. Generates OpenCL GPU kernels for functions suffixed with `_GPU_1`
4. Executes the main block on host, dispatching GPU kernels as encountered
5. Responds to var/arr queries on stdin

---

## Running Inference

### Prerequisites

```bash
pip install numpy tokenizers
```

The `tokenizers` library is needed for the HuggingFace tokenizer. The model weights (safetensors) must be present in `../../Phi-3-mini-4k-instruct/` relative to the script.

### Next-token prediction

```bash
python inference.py "Hello world"
```

Sample output:

```
Loading weights from .../Phi-3-mini-4k-instruct ...  done (3.60 s)
Tokens (3): [1, 15043, 3186]
Running 32 layers ...
  Layer  0 / 32 ... 1.7s
  ...
  Layer 31 / 32 ... 1.7s
Forward pass: 54.83 s

-- Prediction --
  #1  !          37.74%
  #2  .          10.50%
```

### Chat mode

Wraps the prompt in Phi-3 chat template (`<|user|>\n...<|end|>\n<|assistant|>\n`):

```bash
python inference.py --chat "What is 2+2?"
```

### Multi-token generation

```bash
python inference.py --generate 20 --chat "Hello"
```

Generates up to 20 tokens auto-regressively (~55 s per token on all 32 layers).

### Partial layers (fast testing)

```bash
python inference.py --layers 2 "Hello world"    # ~3.5s
```

### Ramanujan GPU demo

```bash
python inference.py --rj "Hello world"
```

Runs the NumPy forward pass AND dispatches layer 0 element-wise ops to the Ramanujan rj runtime. Requires:

- `RAMANUJAN_WS` env var pointing to the workspace with the fat JAR
- Java Corretto 1.8+ in `JAVA_HOME` or on PATH

---

## Performance

Measured on **MacBook Air M3, 16 GB RAM**:

| Operation | Time |
|---|---|
| Weight loading (7.1 GB BF16 to F32) | ~3.5 s |
| Single layer forward | ~1.7 s |
| Full 32-layer forward (next token) | ~55 s |
| Ramanujan rj GPU kernel (layer 0 ops) | ~5 s |
| Multi-token generation (per token) | ~55 s |

For a 20-token generation: **~18 minutes** total.

---

## File Reference

| File | Purpose |
|---|---|
| `inference.py` | Main orchestrator: loads weights, runs NumPy forward pass, optional rj demo |
| `inference_kernel.py` | Ramanujan GPU kernel: element-wise ops (RMS-norm, RoPE, attention) |
| `extract_weights.py` | Extracts safetensors to flat CSV files (legacy, used for full rj approach) |
| `weights/` | 197 CSV weight files (~41 GB) from extract_weights.py |
| `_rj_tmp/` | Temporary small CSVs for rj demo mode |
| `manifest.json` | Ramanujan manifest |
| `seq_input.csv` | Sample sequence input for standalone rj testing |

---

## Troubleshooting

| Issue | Fix |
|---|---|
| FileNotFoundError on safetensors | Ensure model shards are downloaded (see Obtaining the Weights) |
| ModuleNotFoundError: tokenizers | `pip install tokenizers` |
| rj produces empty output | Check `RAMANUJAN_WS` is set and the fat JAR exists |
| rj "Array index expressions not supported" | Use intermediate variables for computed array indices |
| rj variable not found | CSV filenames become variable names; ensure bare names with no path prefix |
| OOM on weight CSVs | Use the hybrid approach (default); do not load 41 GB CSVs into rj |

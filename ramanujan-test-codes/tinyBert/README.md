# TinyBERT on Ramanujan GPU

A minimal BERT-style transformer trained and inferred entirely on the
**phone's GPU** via the Ramanujan distributed computing runtime.

---

## Files

| File | Purpose |
|---|---|
| `tinyBertTrain.py` | Ramanujan training script – runs on device GPU via `rj` |
| `extract_weights.py` | Orchestrates training and harvests all weight arrays → `weights.json` |
| `inference_kernel.py` | Static Ramanujan GPU script – weights and input arrive via CSV arguments |
| `inference.py` | Writes weight/input CSVs, calls `rj inference_kernel.py`, and parses GPU output |
| `weights.json` | *(generated)* Trained weights – produced by `extract_weights.py` |

---

## Quick Start

```bash
# Step 1 – train on phone GPU and save weights
python extract_weights.py

# Step 2 – run GPU inference interactively
python inference.py

# Step 2 (single-shot) – use '_' to mark the masked position
python inference.py "hello wor_d    !"
```

> **Prerequisite:** `rj` must be in your PATH.
> Run `bash install_ramanujan.sh` from the repo root, then `source ~/.zshrc`.

---

## The Dataset

### What it is

The training data is **synthetic**: 512 integers arranged as
**32 sequences of 16 tokens each**.

Each token value is simply its absolute position index modulo 128:

```
dataset[i] = i % 128
```

This creates four consecutive cycles of the full ASCII range (0 → 127):

```
Positions  0–127:   0,  1,  2,  3, …, 127
Positions  128–255: 0,  1,  2,  3, …, 127
Positions  256–383: 0,  1,  2,  3, …, 127
Positions  384–511: 0,  1,  2,  3, …, 127
```

### Sequences and labels

Viewed as 32 samples of 16 tokens, and always masking **position 8**:

| Sample | Tokens (ASCII codes) | Label (pos 8) |
|--------|----------------------|---------------|
| 0 | `[0, 1, 2, 3, 4, 5, 6, 7, ░8░, 9, 10, 11, 12, 13, 14, 15]` | **8** |
| 1 | `[16, 17, 18, …, 23, ░24░, 25, 26, …, 31]` | **24** |
| 2 | `[32, 33, …, 39, ░40░, 41, …, 47]` | **40** |
| … | … | … |
| 7 | `[112, 113, …, 119, ░120░, 121, …, 127]` | **120** |
| 8 | `[0, 1, 2, …, 7, ░8░, 9, …, 15]` | **8** (wraps) |

> `░N░` denotes the masked token, replaced with ID 0 during training.

### What the model learns

Each token is an ASCII character code (0–127).  
The 16-token windows are consecutive arithmetic sequences — the model
must learn that **the token at the masked position equals**
`(window_start + 8) % 128` given the eight surrounding context tokens.

This is non-trivial: the model has to capture the arithmetic offset
from context using attention, not just memorise a lookup table.  
After 12 training cycles the loss drops, confirming the attention and
FFN circuits learn the pattern.

### Why synthetic?

- **No external data dependency** – works out of the box.
- **Deterministic labels** – easy to verify correctness.
- **Non-trivial** – exercises the full attention + FFN path.
- **Replaceable** – swap in any character-level corpus and retrain.

---

## Architecture

| Hyperparameter | Value | Notes |
|---|---|---|
| `VOCAB` | 128 | ASCII range |
| `SEQ` | 16 | tokens per sample |
| `DM` | 32 | embedding / hidden dim |
| `DFF` | 64 | FFN inner dim |
| `NH` | 4 | attention heads |
| `DH` | 8 | dim per head (`DM / NH`) |
| `N_LAYERS` | 1 | single transformer block |
| `BATCH_SIZE` | 8 | samples per step (8 GPU threads) |
| `EPOCHS` | 3 | |
| `STEPS` | 4 | steps per epoch → 12 cycles total |

All array sizes are fixed at declaration (Ramanujan constraint).  
Weights are stored as flat 1-D arrays and indexed with explicit arithmetic.

### Weight tensor sizes

| Weight | Shape | Elements |
|---|---|---|
| `tok_emb` | `VOCAB × DM` | 4 096 |
| `pos_emb` | `SEQ × DM` | 512 |
| `Wq / Wk / Wv / Wo` | `DM × DM` | 1 024 each |
| `W1` | `DM × DFF` | 2 048 |
| `W2` | `DFF × DM` | 2 048 |
| `Wout` | `DM × VOCAB` | 4 096 |
| `b1 / b2 / bout` | vectors | 64 / 32 / 128 |
| **Total** | | **~17 K** |

---

## How inference uses the GPU

`inference.py` does **not** run the forward pass on the desktop.
Instead, for every prediction it:

1. Loads `weights.json`.
2. Writes one single-row CSV per weight array to the script directory
   (persistent across calls, reused on subsequent runs).
3. Writes `seq_input.csv` with the 16 token IDs and mask position.
4. Calls:
   ```
   rj inference_kernel.py \
      tok_emb.csv pos_emb.csv Wq.csv Wk.csv Wv.csv Wo.csv \
      W1.csv W2.csv Wout.csv b1.csv b2.csv bout.csv seq_input.csv
   ```
   — this sends the static kernel through `ExecuteInline.java` →
   `TranslateUtil` → DAG compilation → phone GPU execution, with
   each CSV becoming a named 2-D array in the kernel.
5. Pipes 128 `arr softmax_out N` queries into the interactive query
   console (provided by `ExecutorImpl.startQueryConsole()`).
6. Parses the responses and shows the top-k predicted characters.

The GPU forward pass consists of:

```
threadStart(t0) {
    forward_embed(input_ids, mask_pos)   ← embedding lookup
    forward_attention()                   ← 4-head self-attention + layer norm
    forward_ffn(mask_pos)                ← FFN (ReLU) + layer norm
    forward_classifier()                 ← linear + softmax over 128 classes
}
threadParallelismCycle(t0, 1) { }        ← single cycle, no re-spawn
```

`EXP` and `SQRT` inside `softmax_vec` and `layer_norm` are Ramanujan
GPU built-in intrinsics dispatched directly to the device.

---

## NLP Task: Masked Character Prediction

The task the model was trained on and can perform at inference time:

**Given a 16-character string with one position hidden, predict the
most likely character at that position.**

```
Input  :  hello wor▓d    !
Mask pos: 9

  #1    'd'    100   82.4%   ████████████████████████
  #2    'e'    101    6.1%   ██
  #3    'c'     99    3.2%   █
```

### Practical phone-GPU use-cases

| Use-case | Description |
|---|---|
| **Keyboard autocomplete** | Predict the next/current character on a soft keyboard — fully on-device, no cloud |
| **OCR post-correction** | Fill in ambiguous or low-confidence characters from surrounding context |
| **Password-free typo detection** | Flag characters that are unlikely given their neighbours |
| **Offline input assistance** | Works with no internet connection, preserving user privacy |

### Scaling up

To improve quality for real NLP, increase the architecture constants
at the top of `tinyBertTrain.py` and use a real character corpus:

```python
VOCAB  = 256   # extended ASCII / byte-level
SEQ    = 32    # longer context
DM     = 64    # wider embeddings
DFF    = 128
NH     = 8
EPOCHS = 20
STEPS  = 64
```

Then replace the synthetic dataset loop with your own text data,
re-run `python extract_weights.py`, and `inference.py` picks up the
new `weights.json` automatically.

---

## Ramanujan Language Constraints

The training and inference kernel honour the following rules:

- No `import`, no `class`, no `for` loop, no `**`, no `%` in expressions
- Only `while` loops; `if / else` only (no `elif`)
- All arrays are fixed-size at declaration
- Functions are pass-by-reference; the last parameter receives the return value
- GPU kernels named `<name>_GPU_<ndim>`; data arguments first, range last
- No nested function calls as arguments — use intermediate variables
- No array element as a function argument — assign to a local variable first
- Array indices must be simple variables or literals

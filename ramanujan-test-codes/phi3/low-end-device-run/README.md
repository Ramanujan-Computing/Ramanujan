# Phi-3 Low-End Device Run

This folder contains an isolated Phi-3 execution path for low-memory, 32-bit
Android devices such as the Redmi A3 (`armeabi-v7a`, PowerVR GE8320).

The standard files and weights in the parent `phi3/` directory are not modified
by this workflow.

## Why this variant exists

The PowerVR OpenCL driver failed to allocate the standard 16,000-row embedding
and LM-head buffers:

```text
Allocation size: 0x000BB80000
CL_MEM_OBJECT_ALLOCATION_FAILURE (-4)
```

Each failed allocation is `16000 * 3072 * 4` bytes, or 187.5 MiB. This variant
splits each vocabulary matrix into four smaller buffers:

| File | Token rows | Approximate size |
|---|---:|---:|
| `wte_1.csv`, `lm_head_1.csv` | 0-7,999 | 93.75 MiB |
| `wte_2.csv`, `lm_head_2.csv` | 8,000-15,999 | 93.75 MiB |
| `wte_3.csv`, `lm_head_3.csv` | 16,000-23,999 | 93.75 MiB |
| `wte_4.csv`, `lm_head_4.csv` | 24,000-32,063 | 94.5 MiB |

The transformer also streams these shards. Each LM-head shard is loaded,
dispatched over its vocabulary range, and released before the next shard is
loaded. WTE shards use the same sequence with guarded embedding kernels. This
keeps only one vocabulary-weight shard resident at a time; merely splitting the
files while passing all four arrays to one kernel would still require roughly
378 MiB of simultaneous GPU residency.

## Sequence capacity

This low-end transformer has a fixed capacity of 128 tokens across the prompt
and generated response. The chat client checks `prompt_tokens + N_TOKENS`
before submitting work and rejects requests above that limit.

The standard transformer reserves storage for 1024 tokens. On a 3 GB device,
that consumes about 768 MiB for 32 layers of K/V caches plus roughly 280 MiB of
shared scratch. The low-end 128-token layout uses about 96 MiB for K/V caches
and 25 MiB of shared scratch. Attention-score strides and allocation sizes are
also reduced to 128 tokens, so the compact buffers are not indexed with the
standard 1024-token layout.

## Files

- `convert_to_csv_4bit.py`: Generates the four-shard low-end weights.
- `generate_bin_sidecars.py`: Streams the generated CSVs into raw float32
  sidecars used by the homelab JVM fast path.
- `phi3_transformer_stack_4bit.py`: Transformer stack using the four-shard
  embedding and LM-head interfaces.
- `chat_phi3.py`: Homelab chat client wired to this folder's transformer and
  weights.
- `phi3_weights_csv/`: Generated output directory, created by the converter.

A separate transformer stack is required because GPU function parameters are
fixed during Ramanujan translation. The low-end stack accepts four embedding
and four LM-head arrays, while the standard parent stack accepts two of each.

## Generate low-end weights

The packed matrices use a K-major layout so adjacent GPU work-items read
adjacent values. Existing low-end weights generated before this layout was
introduced are incompatible and must be regenerated.

Run from any working directory:

```bash
python3 ramanujan-test-codes/phi3/low-end-device-run/convert_to_csv_4bit.py
```

Output is always written to:

```text
ramanujan-test-codes/phi3/low-end-device-run/phi3_weights_csv/
```

The converter does not delete, rename, split in place, or overwrite files in
the parent `phi3/phi3_weights_csv/` directory.

## Generate binary sidecars

The sidecars are required before starting chat. Without them, the homelab JVM
loads each large CSV into a Java `String` and can fail with
`OutOfMemoryError: Java heap space`.

```bash
python3 ramanujan-test-codes/phi3/low-end-device-run/generate_bin_sidecars.py
```

The generator reads each CSV incrementally and writes a sibling `.bin` file in
the low-end output directory. It does not load an entire CSV into memory and
does not access the parent `phi3/phi3_weights_csv/` directory. Existing current
sidecars are skipped; use `--force` only when regeneration is intended.

## Run chat

Before testing this optimization, rebuild the middleware translation module so
`PACKED_NIBBLE` is translated to OpenCL integer shift/mask operations:

```bash
cd middleware/translation
mvn clean install
```

Rebuild and reinstall the Android app because the native OpenCL compiler-option
selection also changed. Then start the homelab server and Android worker and
run:

```bash
python3 ramanujan-test-codes/phi3/low-end-device-run/chat_phi3.py
```

To use another low-end output directory:

```bash
python3 ramanujan-test-codes/phi3/low-end-device-run/chat_phi3.py \
  --weights-dir /path/to/low-end/phi3_weights_csv \
  --homelab-url http://localhost:8888
```

## Compatibility

The low-end chat, transformer, and generated weight shards must be used
together. Do not mix the four-shard low-end transformer with the standard
parent directory's two-shard weight files.

Changes to `phi3_transformer_stack_4bit.py` are submitted as source by the chat
client, so they do not require rebuilding the Android app. Native runtime
changes still require rebuilding and reinstalling the app.

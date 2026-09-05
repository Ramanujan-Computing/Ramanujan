# CSV Data Usage

[← Back to main README](../README.md)

## Direct CSV Population (High-Performance Data Loading)

When running `execute_inline` with CSV data files, the runtime now **bypasses the Python interpreter** for data population. Previously, every CSV value was converted to a Python assignment statement (`arr[r][c] = val`) and run through the full AST interpreter — for a 50-million-element CSV, that meant 50 million interpreted statements.

The new approach:
1. **Declaration only**: The CSV files generate minimal Python code — just the array declaration (e.g., `arr = [[0 for _ in range(3072)] for _ in range(9216)]`).
2. **Direct population**: After the interpreter creates the Array objects, values are injected directly via `Double.parseDouble()` + `ConcurrentHashMap.put()`, skipping the interpreter entirely.
3. **Parallel loading**: Multiple CSV files are loaded simultaneously using a thread pool.

This makes it practical to load gigabytes of weight data (e.g., 41 GB of neural network weights across 197 CSV files) in seconds rather than hours.

### Auto 1D/2D Detection

Single-row CSVs (one line of comma-separated values) are automatically declared as **1D arrays** (`[0 for _ in range(N)]`), while multi-row CSVs are declared as **2D arrays** (`[[0 for _ in range(cols)] for _ in range(rows)]`). The population step stores values accordingly:
- 1D: keys are `"0"`, `"1"`, `"2"`, ...
- 2D: keys are `"row_col"` format (`"0_0"`, `"0_1"`, ..., `"1_0"`, ...)

### CSV Filename to Array Name

CSV filenames are automatically converted to valid array names:
- Directory paths are stripped (`../weights/l0_iln.csv` → `l0_iln`)
- The `.csv` extension is removed
- Non-alphanumeric characters are replaced with `_`

This means the array name in your kernel code must match the CSV filename (without path and extension).

---

## `dump` Command (Array Extraction)

The `execute_inline` query console now supports a `dump` command for extracting array contents to CSV files:

```
dump <arrayName> [outputFile]
```

- **Without a file path**: prints comma-separated values to stdout
- **With a file path**: writes values to the specified CSV file

The command auto-detects whether the array is 1D or 2D:
- **1D arrays**: outputs a single line of comma-separated values
- **2D arrays**: outputs one row per line (standard CSV format)

### Example
```bash
# Run a kernel, then dump results
printf 'dump normed /tmp/normed_out.csv\nexit\n' | \
  java -Xmx4g -jar developer-console-fat.jar execute_inline kernel.py data.csv weights.csv

# Check output
cat /tmp/normed_out.csv
# 0.00288,-0.00078,0.00454,...
```

All query console commands:
- `var <name>` — print a scalar variable
- `arr <name> <index>` — print a single array element
- `dump <name> [file]` — dump entire array to CSV
- `exit` — end the session

---

## Phi-3 Reference Implementation

The `ramanujan-test-codes/phi3/` directory contains a complete implementation of **Microsoft Phi-3-mini-4k-instruct** (3.8B parameters) running entirely on Ramanujan's GPU runtime. This serves as a reference for running large neural networks on the platform.

### Key Files
| File | Purpose |
|------|---------|
| `extract_weights.py` | Converts safetensors model → 197 CSV weight files (41 GB) |
| `inference.py` | Hybrid NumPy + Ramanujan inference (CPU fallback) |
| `inference_rj.py` | **Full Ramanujan** inference — every layer runs on GPU |
| `layer_kernel.py` | Single transformer layer GPU kernel (RMS norm, QKV projection, RoPE, attention, FFN) |
| `embed_kernel.py` | Embedding lookup kernel |
| `head_kernel.py` | Final norm + LM head projection kernel |

### Architecture: Layer-by-Layer Streaming
Rather than loading all 41 GB of weights at once, the orchestrator (`inference_rj.py`) calls `rj` once per transformer layer, passing only that layer's weights (~1.3 GB) as CSV files. Between layers, results are extracted via the `dump` command and passed to the next invocation.

```
Token → [embed_kernel] → hidden
                            ↓
                     [layer_kernel × 32]  ← weights loaded per-layer
                            ↓
                     [head_kernel] → logits → argmax → next token
```

See `ramanujan-test-codes/phi3/README.md` for full details on weight extraction, model architecture, and usage instructions.




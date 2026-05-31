# PyTorch to Ramanujan Translation Guide: A Phi-3 Case Study

This document serves as an exhaustive technical guide for developers who want to port standard deep learning code written in **PyTorch** to run on the **Ramanujan JVM/GPU execution runtime**. 

Using **Microsoft's Phi-3-mini-4k-instruct** as a case study, we will dissect how high-level PyTorch layers and tensor operations are lowered into low-level, parallel-thread GPU kernel graphs inside Ramanujan.

---

## 1. Core Architectural Concepts of Ramanujan

Ramanujan is a custom high-performance execution engine. Instead of interpreting PyTorch graphs or running native Python execution loops, it compiles a specialized Python subset into highly optimized GPU (OpenCL/Metal/CUDA) execution paths.

When porting code to Ramanujan, you must shift your mental model from **tensor-centric object-oriented programming** to **flat-memory thread-parallel procedural programming**.

### A. The GPU Kernel Grid Suffixes (`_GPU_1` and `_GPU_2`)
The Ramanujan compiler parses Python functions and automatically translates them into parallel GPU execution kernels based on their naming suffixes:

*   **`_GPU_1` (1D Thread Grid)**: Spawns a 1D grid of thread execution contexts. The last argument in the call dictates the grid size, and the parameter itself is automatically bound to the thread's `global_id(0)`.
    *   *Example*: `rmsnorm_GPU_1(hidden, gamma, out, row)` where calling with `row = n_seq` spawns `n_seq` parallel threads. Thread `i` executes with `row = i`.
*   **`_GPU_2` (2D Thread Grid)**: Spawns a 2D grid of thread execution contexts. The last two arguments dictate the grid dimensions ($Rows \times Cols$), which map to `global_id(0)` and `global_id(1)`.
    *   *Example*: `matmul_4bit_GPU_2(A, W_packed, W_scales, C, kparams, row, col)` called with `row = n_seq` and `col = 9216` spawns a grid of `n_seq * 9216` threads. Each thread computes a single scalar output at coordinate `(row, col)`.

### B. Flat 1D Memory Allocations
Ramanujan does not support multi-dimensional tensors. All matrices, weights, and hidden states must be represented as flat 1D lists (representing continuous GPU buffer allocations). You must perform index arithmetic manually.
*   **Standard Layout**: A 2D matrix of shape `[M, N]` is flattened into a 1D array of size `M * N`. 
*   **Accessing coordinate `(r, c)`**: You must calculate the flat offset: `flat_index = r * N + c`.

### C. Uppercase Symbolic Math Built-ins
To bypass python interpreter overhead and tap directly into native GPU hardware acceleration, mathematical operations that cannot be easily auto-vectorized must use uppercase symbolic directives. The Ramanujan compiler translates these into fast mathematical hardware instructions:
*   `SQRT(val)` $\rightarrow$ Fast GPU square root ($\sqrt{val}$)
*   `EXP(val)` $\rightarrow$ Fast GPU exponential ($e^{val}$)
*   `FLOOR(val)` $\rightarrow$ Fast GPU floor rounding ($\lfloor val \rfloor$)
*   `GPU_SYNC(buffer)` $\rightarrow$ Blocks host execution until all GPU threads finish writing to the buffer.

### D. Procedural "Uber Loops" vs. Classes
Standard PyTorch layers subclass `torch.nn.Module` and maintain state inside class objects. Ramanujan kernels are entirely procedural.
*   **No classes are defined** in the kernel.
*   **State is global**: Input parameters, intermediate states, and weights are passed as global 1D lists loaded from external storage (CSV/binary).
*   ** Prefill & Decode are sequenced in an "Uber Loop"**: A single script performs both the multi-token prompt prefill (processing all token rows simultaneously) and the single-token autoregressive decoding loop in sequence.

---

## 2. Exhaustive Mapping: PyTorch to Ramanujan Kernels

Let’s analyze exactly how standard neural network operations map between the two execution environments.

### Case Study 1: RMSNorm (Root Mean Square Layer Normalization)

RMSNorm normalizes activation vectors by their root mean square value before applying a learnable scale parameter (`gamma`).

#### Standard PyTorch Implementation (`modeling_phi3.py`)
```python
class Phi3RMSNorm(nn.Module):
    def __init__(self, hidden_size, eps=1e-6):
        super().__init__()
        self.weight = nn.Parameter(torch.ones(hidden_size))
        self.variance_epsilon = eps

    def forward(self, hidden_states):
        input_dtype = hidden_states.dtype
        hidden_states = hidden_states.to(torch.float32)
        variance = hidden_states.pow(2).mean(-1, keepdim=True)
        hidden_states = hidden_states * torch.rsqrt(variance + self.variance_epsilon)
        return self.weight * hidden_states.to(input_dtype)
```

#### Ramanujan GPU Kernel Implementation (`phi3_transformer_stack_4bit.py`)
```python
def rmsnorm_GPU_1(hidden, gamma, out, row):
    base = 0
    hk = 0
    idx = 0

    # 1. Manually calculate the flat row offset in the 1D pointer hidden
    base = row * 3072
    s = 0.0
    k = 0
    
    # 2. Accumulate sum of squares across the hidden dimension (3072)
    while k < 3072:
        hk = base + k
        val = hidden[hk]
        s = s + val * val
        k = k + 1

    # 3. Compute the Root Mean Square with epsilon (1e-5)
    rms = s / 3072.0 + 0.00001
    SQRT(rms) # Hardware-accelerated GPU SQRT computation

    # 4. Normalize the activations and scale by gamma
    k = 0
    while k < 3072:
        idx = base + k
        out[idx] = (hidden[idx] / rms) * gamma[k]
        k = k + 1
```

> [!IMPORTANT]
> **Key Difference in Execution**: 
> In PyTorch, `.mean(-1)` runs a highly optimized C++/CUDA reduction kernel over the entire tensor batch. 
> In Ramanujan, the scheduler invokes `rmsnorm_GPU_1` as a **1D parallel grid** where each thread (uniquely identified by `row`) handles a single sequence token. The thread loops sequentially through the 3072 hidden features to calculate the reduction, ensuring high occupancy without inter-thread sync overhead.

---

### Case Study 2: SwiGLU Activation (MLP Block)

The MLP block in Phi-3 utilizes SwiGLU activations, which involve multiplying a SiLU-activated gated projection with an up-projection.

#### Standard PyTorch Implementation (`modeling_phi3.py`)
```python
class Phi3MLP(nn.Module):
    def __init__(self, config):
        super().__init__()
        self.gate_up_proj = nn.Linear(config.hidden_size, 2 * config.intermediate_size, bias=False)
        self.down_proj = nn.Linear(config.intermediate_size, config.hidden_size, bias=False)
        self.activation_fn = ACT2FN[config.hidden_act] # SiLU: x * sigmoid(x)

    def forward(self, hidden_states: torch.FloatTensor) -> torch.FloatTensor:
        # 1. Project to double intermediate dimension (2 * 8192 = 16384)
        up_states = self.gate_up_proj(hidden_states)
        
        # 2. Chunk into gate and up projections
        gate, up_states = up_states.chunk(2, dim=-1)
        
        # 3. Apply SiLU and multiply
        up_states = up_states * self.activation_fn(gate)
        
        # 4. Project down to hidden size (3072)
        return self.down_proj(up_states)
```

#### Ramanujan GPU Kernel Implementation (`phi3_transformer_stack_4bit.py`)
```python
def silu_GPU_2(h_ff_buf, row, col):
    idx = 0
    h_ff_buf_idx0 = 0

    # 1. Calculate thread coordinate flat offset in 2D grid
    idx = row * 16384 + col
    
    # 2. Extract gate value (first half of intermediate space: 0 to 8191)
    gate = h_ff_buf[idx]
    
    # 3. Extract up value (second half of intermediate space: 8192 to 16383)
    h_ff_buf_idx0 = idx + 8192
    up = h_ff_buf[h_ff_buf_idx0]

    # 4. Manual Sigmoid Calculation: sig = 1 / (1 + exp(-gate))
    neg_gate = 0.0 - gate
    EXP(neg_gate) # Hardware-accelerated GPU exponential (e^-gate)
    sig = 1.0 / (1.0 + neg_gate)

    # 5. SiLU multiplication: gate * sig(gate) * up
    res = gate * sig * up
    h_ff_buf[idx] = res # In-place write back to the gate portion
```

> [!NOTE]
> **Memory Layout Design**:
> In the PyTorch MLP forward pass, `gate_up_proj` produces a wide matrix of hidden states ($SeqLen \times 16384$), which is split dynamically using `torch.chunk`.
> In the Ramanujan kernel, the intermediate states are written into a single flat buffer `h_ff_buf` of size $1024 \times 16384$. The GPU kernel `silu_GPU_2` is launched as a **2D parallel grid** ($SeqLen \times 8192$). Each parallel thread processes exactly **one** coordinate element, performing the manual sigmoid math, multiplying with the corresponding up-projection, and writing the result in-place.

---

### Case Study 3: 4-Bit Quantized Matrix Multiplication

To fit large model weights on target memory architectures, Ramanujan implements a custom **symmetric 4-bit per-row absmax quantization scheme**. This is not natively present in standard PyTorch source files, but represents how model parameters are lowered for execution.

#### Dequantization Math
Standard weights are quantized as:
$$\text{scale} = \frac{\max(|\text{row}|)}{7.0}$$
$$q = \text{round}\left(\frac{w}{\text{scale}}\right) \in [-8, 7]$$
$$\text{stored} = q + 8 \in [0, 15] \text{ (unsigned 4-bit value)}$$

Six 4-bit values are packed into a single 32-bit float using powers of 16 ($1, 16, 256, 4096, 65536, 1048576$).

#### Ramanujan GPU Kernel Implementation (`phi3_transformer_stack_4bit.py`)
```python
def matmul_4bit_GPU_2(A, W_packed, W_scales, C, kparams, row, col):
    K = kparams[0]       # Input channel dimension (e.g. 3072)
    N = kparams[1]       # Output channel dimension (e.g. 9216)
    K_pack = kparams[2]  # Pack dimension (K // 6)

    # Local thread registers
    W_scales_idx0 = 0
    w_base = 0
    W_packed_idx0 = 0
    a_idx = 0
    packed = 0.0
    w0 = w1 = w2 = w3 = w4 = w5 = 0.0

    # 1. Fetch dequantization scale for this output column
    W_scales_idx0 = col
    scale = W_scales[W_scales_idx0]
    w_base = col * K_pack

    s = 0.0
    k_pack = 0
    
    # 2. Dot product accumulation loop over packed features
    while k_pack < K_pack:
        W_packed_idx0 = w_base + k_pack
        packed = W_packed[W_packed_idx0] # Read packed float containing 6 weights

        # 3. Unpack 6 4-bit weights sequentially using divisions and FLOOR
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

        # 4. Dequantize weights from unsigned [0, 15] to floating-point values
        w0 = (w0 - 8.0) * scale
        w1 = (w1 - 8.0) * scale
        w2 = (w2 - 8.0) * scale
        w3 = (w3 - 8.0) * scale
        w4 = (w4 - 8.0) * scale
        w5 = (w5 - 8.0) * scale

        # 5. Multiply-accumulate with 6 consecutive elements of the input vector A
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

    # 6. Write final scalar result into the output buffer
    c_idx = row * N + col
    C[c_idx] = s
```

---

## 3. Practical Conversion Checklist

If you want to port a PyTorch layer or model architecture to run on Ramanujan, follow this step-by-step developer pipeline:

### Step 1: Flatten All Tensors
Locate all tensors (`input`, `weight`, `bias`, `caches`) and represent them as flat 1D lists.
*   Rewrite index calls: change `tensor[batch, seq, head, dim]` to `tensor[batch * SEQ_HEAD_DIM + seq * HEAD_DIM + head * DIM + dim]`.

### Step 2: Vector-to-Grid Parallelization
Decide if your operations should execute in 1D parallel or 2D parallel:
*   Identify features that can run in parallel without dependency (e.g. sequence length, output channels).
*   Add the `_GPU_1` or `_GPU_2` suffix to your Python function name.
*   Assign grid dimensions using the final parameters of the function definition.
*   Replace PyTorch broad reduction operators (like `.sum()`, `.mean()`) with manual loops (`while`) operating inside a single parallel thread.

### Step 3: Lower Complex Bracket Expressions (Compiler Limit)
Ramanujan's compiler parses execution indices into structured Directed Acyclic Graphs (DAGs). Complex expressions within array brackets can fail parsing.
*   **DO NOT DO**: `A[row * K + k_pack * 6]`
*   **DO**: Lower it into temporary indexing variables first:
    ```python
    a_idx = row * K + k_pack * 6
    val = A[a_idx]
    ```
    *(You can run `fix_phi3.py` in the workspace to automatically apply this regex fix to your scripts)*.

### Step 4: Map Built-in Mathematical Operations
Audit your mathematical functions and swap standard python math operations for capitalized symbolic built-ins:
*   `math.sqrt` or `** 0.5` $\rightarrow$ `SQRT()`
*   `torch.exp` $\rightarrow$ `EXP()`
*   `math.floor` $\rightarrow$ `FLOOR()`

### Step 5: Flatten the Execution Stack into an Uber Loop
*   Remove all `class` and `nn.Module` declarations.
*   Define static scratch and caching space at the top of the file as pre-allocated flat 1D buffers.
*   Write your prefill phase (token block execution).
*   Sequence your decode phase inside a standard `while` generation loop (`while _step < n_tokens`).
*   Include host memory synchronizations (`GPU_SYNC()`) before passing generated tokens back to the host JVM application layer.

---

## 4. End-to-End Developer Conversion Guide

This section walks you through converting a real PyTorch model to Ramanujan from scratch. Follow these steps in order.

### Phase 0: Set Up Ramanujan

Before writing any kernel code, get the developer console running:

```sh
chmod +x install_ramanujan.sh
./install_ramanujan.sh   # sets RAMANUJAN_WS, downloads fat JAR, adds `rj` alias
source ~/.zshrc          # or ~/.bashrc
```

Verify the installation:
```sh
rj --version             # should print the fat-JAR version
```

For GPU acceleration, build the native runtime with OpenCL enabled:
```sh
cd ramanujan-native/native && mkdir build-gpu && cd build-gpu
cmake -DENABLE_GPU=ON .. && cmake --build .
```

---

### Phase 1: Audit Your PyTorch Model

Before touching code, answer these four questions about your PyTorch model:

| Question | Why it matters |
|---|---|
| Which layers are compute-heavy? | Those get GPU kernels (`_GPU_N`). |
| What are the tensor shapes at each step? | You need these to compute flat offsets manually. |
| Does your model use quantized weights? | Plan a CSV export + dequantize-at-runtime strategy. |
| Does inference run in two phases (prefill + decode)? | If so, you'll write an "uber loop" that handles both. |

---

### Phase 2: Know What Ramanujan Python Supports

Ramanujan parses a strict **subset** of Python via AST. Before porting, internalize these rules:

**Supported:**
- Variables (`x = 5`, `y = 3.14`) and 1D/2D/ND arrays (`arr = [0 for _ in range(n)]`)
- `while` loops, `if/else` (no `elif`)
- Arithmetic: `+`, `-`, `*`, `/`, augmented assignments (`+=`, etc.)
- Functions with `def`; arguments are passed **by reference** — mutations inside the function affect the caller's arrays
- GPU built-ins inside `_GPU_N` kernels: `EXP(x)`, `LOG(x)`, `SQRT(x)`, `FLOOR(x)`, `ATOMIC_ADD_F(arr, idx, delta)`
- `GPU_SYNC(array)` to drain the GPU queue before a host read

**Not supported (work around these):**
| Unsupported | Workaround |
|---|---|
| `class` / `nn.Module` | Global flat arrays + procedural functions |
| `for` loops | Rewrite as `while` |
| `elif` | Nest `if/else` |
| `import` statements | Pre-load weights externally; pass as arrays |
| `arr.append()`, `len()` | Pre-allocate fixed-size arrays |
| `obj.method()` | Free functions that take the array as a parameter |
| `and`/`or`/`not` in conditions | Nest `if` statements |
| Nested function calls `f(g(x))` | Assign `tmp = g(x)` first |
| Power `**` / modulo `%` | Implement with a loop or approximation |
| Strings | Use integer token IDs |

---

### Phase 3: Export Weights to CSV

Ramanujan loads weights from CSV files (or binary `.bin` sidecars for large models). Write a one-time Python export script using PyTorch or `safetensors`:

```python
import torch
import numpy as np

# Load your model
model = torch.load("model.pt", map_location="cpu")

# Export each weight matrix as a CSV
# - Naming: the CSV filename becomes the array name in your kernel
# - For 4-bit quantization, pack and scale here; the kernel dequantizes on the GPU
for name, param in model.named_parameters():
    arr = param.detach().float().numpy()
    safe_name = name.replace(".", "_")
    np.savetxt(f"weights/{safe_name}.csv", arr, delimiter=",")
```

**CSV naming rules:**
- Directory paths and `.csv` extension are stripped automatically
- Non-alphanumeric characters become `_`
- The resulting name **must match** the variable name used in your kernel code

For large weight files (>100 MB), generate binary sidecars to avoid JVM heap pressure:
```python
import numpy as np, glob, os

for csv_path in glob.glob("weights/*.csv"):
    arr = np.loadtxt(csv_path, delimiter=",", dtype=np.float32)
    bin_path = csv_path.replace(".csv", ".bin")
    arr.tofile(bin_path)
```

---

### Phase 4: Rewrite Each Layer as a GPU Kernel

For each PyTorch `nn.Module`, write a corresponding Ramanujan function following this template:

```python
# 1-D kernel template: one thread per row
def my_op_GPU_1(input_array, weight_array, output_array, row):
    base = row * HIDDEN_DIM   # manual flat offset for this thread's row
    s = 0.0
    k = 0
    while k < HIDDEN_DIM:
        val = input_array[base + k]
        # ... compute ...
        k = k + 1
    output_array[row] = s

# 2-D kernel template: one thread per (row, col) output element
def my_matmul_GPU_2(A, W, C, kparams, row, col):
    K = kparams[0]   # inner dimension
    N = kparams[1]   # number of columns in C
    s = 0.0
    k = 0
    while k < K:
        a_idx = row * K + k   # always use an intermediate variable for index arithmetic
        w_idx = col * K + k
        s = s + A[a_idx] * W[w_idx]
        k = k + 1
    c_idx = row * N + col
    C[c_idx] = s
```

**Key rules when writing kernels:**
1. The last `N` parameters of a `_GPU_N` function are the grid dimensions. Their call-site values become the NDRange sizes. Inside the kernel, they hold the thread's coordinate.
2. All index arithmetic **must** be lowered into intermediate variables before being used inside `[ ]`. The compiler rejects complex bracket expressions.
3. Replace `math.sqrt` → `SQRT(x)`, `math.exp` → `EXP(x)`, `math.floor` → `FLOOR(x)` (these mutate the variable in-place).
4. When multiple threads write to the same array slot (e.g., scatter operations), use `ATOMIC_ADD_F(arr, idx, delta)` instead of a plain assignment.

---

### Phase 5: Write Non-GPU Helper Functions

Lightweight host-side logic (tokenization helpers, argmax, softmax over a small vector) stays as plain Python functions callable from the kernel:

```python
# Plain function — runs on the host CPU
def host_argmax(logits, vocab_size):
    best = 0
    best_val = logits[0]
    i = 1
    while i < vocab_size:
        v = logits[i]
        if v > best_val:
            best_val = v
            best = i
        i = i + 1
    return best
```

Plain functions can also be called from inside a `_GPU_N` kernel as **device helpers** — the translator promotes them to OpenCL device functions. Device helpers must accept only scalar `float` arguments (no array pointers) and cannot be recursive.

---

### Phase 6: Assemble the Uber Loop

Replace PyTorch's training/inference loop with a single flat script:

```python
# --- Pre-allocate all scratch buffers ---
hidden   = [0 for _ in range(MAX_SEQ * HIDDEN_DIM)]
h_ln     = [0 for _ in range(MAX_SEQ * HIDDEN_DIM)]
qkv_buf  = [0 for _ in range(MAX_SEQ * 3 * HEAD_DIM * N_HEADS)]
out_buf  = [0 for _ in range(MAX_SEQ * HIDDEN_DIM)]

# --- Prefill: process all prompt tokens at once ---
embed_GPU_1(token_ids, wte, hidden, n_seq)   # embed each token in parallel
layer_idx = 0
while layer_idx < N_LAYERS:
    rmsnorm_GPU_1(hidden, ln_g, h_ln, n_seq)
    matmul_4bit_GPU_2(h_ln, qkv_packed, qkv_scales, qkv_buf, kp, n_seq, 3 * HEAD_DIM * N_HEADS)
    # ... remaining layer ops ...
    layer_idx = layer_idx + 1

# --- Decode: one token at a time ---
_step = 0
while _step < n_generate:
    embed_GPU_1(last_token_id, wte, hidden, 1)
    # ... 32 layers on a single row ...
    GPU_SYNC(logits)           # drain before host reads logit values
    next_token = host_argmax(logits, VOCAB_SIZE)
    token_ids[n_seq] = next_token
    n_seq = n_seq + 1
    _step = _step + 1
```

---

### Phase 7: Place `GPU_SYNC` Correctly

GPU kernels are **non-blocking** by default — the CPU queues the kernel and continues immediately. You only need to sync when the host CPU is about to read a value that a GPU kernel has written:

| When | Action |
|---|---|
| Reading a GPU-written array in a host `while` loop | `GPU_SYNC(array)` before the loop |
| Passing GPU output to a host helper function | `GPU_SYNC(array)` before the call |
| Dumping an array to CSV with the `dump` command | `GPU_SYNC(array)` before `dump` |
| Passing GPU output as input to the next GPU kernel | **No sync needed** — GPU→GPU is handled automatically |

Placing `GPU_SYNC` too eagerly (e.g., after every kernel call) kills throughput. Batch as many kernel dispatches as possible and sync only at host read-back boundaries.

---

### Phase 8: Run and Debug

Execute your kernel with the developer console:

```sh
# Single file, no external weights
rj my_kernel.py

# With CSV weight files
java -jar developer-console-fat.jar execute_inline my_kernel.py \
    weights/w1.csv weights/w2.csv ...

# Interactive dump session (inspect outputs without modifying the kernel)
printf 'dump hidden /tmp/hidden.csv\nexit\n' | \
  java -jar developer-console-fat.jar execute_inline my_kernel.py weights/*.csv
```

**Common errors and fixes:**

| Error | Cause | Fix |
|---|---|---|
| `CompilationException: complex bracket expression` | Index math inside `[ ]` | Extract to a named variable first |
| `CompilationException: recursive call detected` | Helper function calls itself | Unroll the recursion manually |
| Silently wrong outputs after GPU kernels | Missing `GPU_SYNC` before host read | Add `GPU_SYNC(array)` at the right point |
| JVM OOM on large CSV files | No binary sidecar present | Run `generate_bin_sidecars.py` first |
| `elif` / `for` / `and` parse error | Unsupported Python syntax | Apply the workarounds from Phase 2 |

---

### Quick Reference: PyTorch → Ramanujan Cheat Sheet

```
PyTorch                          Ramanujan
─────────────────────────────────────────────────────────────────
class Model(nn.Module)      →   flat global arrays + def functions
tensor[b, s, h, d]          →   tensor[b*S*H*D + s*H*D + h*D + d]
torch.exp(x)                →   EXP(x)  [mutates x in-place]
torch.sqrt(x)               →   SQRT(x) [mutates x in-place]
math.floor(x)               →   FLOOR(x) [mutates x in-place]
for i in range(n):          →   i = 0; while i < n: ... i += 1
elif:                       →   else: if ...:
a[i] = f(g(a[i]))           →   tmp = g(a[i]); a[i] = f(tmp)
tensor.mean(-1)             →   manual while loop inside the kernel
atomic scatter              →   ATOMIC_ADD_F(arr, idx, delta)
wait for GPU result         →   GPU_SYNC(array)
```

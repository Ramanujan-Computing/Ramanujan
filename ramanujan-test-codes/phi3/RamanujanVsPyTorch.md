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

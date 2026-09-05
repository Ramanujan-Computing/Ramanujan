# GPU Acceleration Support (OpenCL):

[← Back to main README](../README.md)

Ramanujan supports offloading compute-intensive work to the GPU via OpenCL. Any Python function
whose name matches the pattern `funcName_GPU_N` (where `N` is a positive integer) is automatically
compiled to an OpenCL C kernel during translation and dispatched to the GPU at runtime.

## Writing a GPU function

The function name encodes the number of NDRange dimensions:

```python
def funcName_GPU_N(dataArg1, dataArg2, ..., dataArgK, rangeDim1, ..., rangeDimN):
    # body – use rangeDimK as the per-work-item index
```

| Part | Role | What the translator does |
|---|---|---|
| `N` in `_GPU_N` | Number of NDRange dimensions (`work_dim`) | Read from the function name; no extra argument needed |
| `dataArg1 … dataArgK` | Data arrays (first `total_params − N` parameters) | Emitted as `__global float*` kernel parameters |
| `rangeDim1 … rangeDimN` | Work-item index variables (last `N` parameters) | Emitted as `int dim = get_global_id(K);` declarations; their **call-site values** become `global_work_size[]` for `clEnqueueNDRangeKernel` |

> **Requirement:** Data args must be arrays. The translator maps them to OpenCL buffers.

## Examples

### 1-D vector addition
```python
def vector_add_GPU_1(a, b, c, gid):
    c[gid] = a[gid] + b[gid]

# 1024-element arrays, 1 NDRange dimension
vector_add_GPU_1(a, b, c, 1024)
```
Generated OpenCL kernel:
```c
__kernel void vector_add(__global float* a, __global float* b, __global float* c) {
    int gid = get_global_id(0);
    c[gid] = (a[gid] + b[gid]);
}
```

### 2-D matrix kernel
```python
def matrix_add_GPU_2(a, b, c, row, col):
    c[row] = a[row] + b[col]

# 64 × 64 grid (2 NDRange dimensions)
matrix_add_GPU_2(a, b, c, 64, 64)
```

### Control flow inside a GPU function
`if/else` and `while` inside the function body are translated to their C equivalents:
```python
def relu_GPU_1(a, out, gid):
    if a[gid] > 0:
        out[gid] = a[gid]
    else:
        out[gid] = 0

relu_GPU_1(a, out, 512)
```

### Calling helper (device) functions from a GPU kernel

A GPU kernel can call ordinary (non-`_GPU_N`) Python functions that are defined in the same file.
The translator converts those helpers to OpenCL C **device functions** and prepends them before the
`__kernel` declaration so they are visible to the kernel body.

```python
# Plain Python helper – becomes a float device function in the generated OpenCL C
def scale2(x):
    r = x * 2
    return r

# GPU kernel – calls the helper for every work-item
def apply_scale_GPU_1(a, out, gid):
    v = a[gid]          # load array element into a local variable first
    out[gid] = scale2(v)

apply_scale_GPU_1(a, out, 1024)
```

Generated OpenCL C:
```c
float scale2(float x) {
    float r = (x * 2);
    return r;
}

__kernel void apply_scale(__global float* a, __global float* out) {
    int gid = get_global_id(0);
    float v = a[gid];
    out[gid] = scale2(v);
}
```

#### Helper function constraints

| Constraint | Detail |
|---|---|
| **Parameter types** | All helper parameters are treated as `float` scalars. Array pointers (`__global float*`) are **not** supported in helper functions. |
| **Return type** | Always `float`. |
| **No subscript as argument** | Array element expressions (`a[i]`) cannot be passed directly as arguments to a helper call. Assign the element to a local variable first: `v = a[gid]; out[gid] = scale2(v)`. |
| **No recursion** | A function may not call itself. The translator throws a `CompilationException` if a recursive call is detected at translation time. |
| **No GPU–kernel calls** | A helper (or the kernel) may not call another `_GPU_N` function. GPU kernels are dispatched via `clEnqueueNDRangeKernel` and cannot be invoked as device functions. |
| **Scope** | Only top-level module functions are eligible as helpers. Nested function definitions are not supported. |

#### Recursion guard

The translator enforces the no-recursion rule at **translation time**, not at runtime:

```python
# INVALID – will raise CompilationException during translation
def bad_GPU_1(a, gid):
    a[gid] = bad_GPU_1(a, gid)   # ❌ recursive GPU call

# INVALID – helper self-recursion is also rejected
def factorial(n):
    return n * factorial(n - 1)  # ❌ recursive helper

def kernel_GPU_1(a, gid):
    a[gid] = factorial(a[gid])
```

## GPU built-in functions

These special function names are recognised inside `_GPU_N` kernel bodies and
translated to OpenCL C intrinsics.  They are **not** available on the host; use
the corresponding Ramanujan host built-ins (`FLOOR`, `EXP`, etc.) outside GPU
functions.

### In-place scalar math (1 argument)

Each call mutates its argument in place: `FUNC(x)` → `x = func(x)` in the
generated OpenCL C.

| Python call | Generated OpenCL C | Notes |
|---|---|---|
| `EXP(x)` | `x = exp(x);` | Natural exponential |
| `LOG(x)` | `x = log(x);` | Natural logarithm |
| `SQRT(x)` | `x = sqrt(x);` | Square root |
| `FLOOR(x)` | `x = floor(x);` | Round toward −∞; result stays `float` |

**`FLOOR` example** — locate the grid cell for a particle position:
```python
def p2g_GPU_1(positions, params, gid):
    xp = positions[gid * 3]
    gxp = (xp - (-1.0)) * 10.0  # map to grid coords
    FLOOR(gxp)                   # gxp = floor(gxp) in OpenCL C
    i0 = gxp                     # integer grid index (stored as float)
```

### Packed 4-bit extraction (2 arguments)

```python
nibble = PACKED_NIBBLE(packed, index)
```

`PACKED_NIBBLE` numerically converts an exact integer-valued packed `float` to
OpenCL `uint`, shifts by `index * 4`, masks with `0xF`, and returns the nibble as
a `float`. Valid nibble indexes are 0 through 5 for Ramanujan's six-weights-per-
float Phi-3 format. Packed values must not exceed `0xFFFFFF`, which is exactly
representable in `float32`.

```c
((float)(((uint)packed >> ((uint)index * 4u)) & 15u))
```

This avoids floating-point divide and `floor()` during 4-bit dequantization.
It is an expression intrinsic and may be used on the right-hand side of an
assignment inside `_GPU_N` functions.

### Atomic float add (3 arguments)

```python
ATOMIC_ADD_F(arr, idx, delta)
```

Atomically adds `delta` (a `float`) to `arr[idx]` using a compare-and-swap
loop.  Required whenever multiple work-items may scatter into the same array
slot concurrently (e.g., particle-to-grid scattering in MPM).

OpenCL 1.2 has no native `atomic_add` for `float`.  The translator emits a
CAS loop on the int-reinterpreted bits using `atomic_cmpxchg`:

```c
/* Generated for: ATOMIC_ADD_F(arr, idx, delta) */
{
    __global volatile int* _aAddr = (__global volatile int*)(&arr[(int)(idx)]);
    int _aOld, _aNew;
    do {
        _aOld = *_aAddr;
        _aNew = as_int(as_float(_aOld) + (delta));
    } while (atomic_cmpxchg(_aAddr, _aOld, _aNew) != _aOld);
}
```

The `{}` block scope lets you call `ATOMIC_ADD_F` multiple times in the same
kernel without variable-name conflicts.

**Requirements:**
- The target array must be a `__global float*` data argument (not a local variable).
- Requires OpenCL 1.2 or later (`atomic_cmpxchg` on `__global int*` is a
  core 1.2 feature on all platforms including Apple Metal-backed OpenCL).

**`ATOMIC_ADD_F` example** — particle-to-grid mass scatter:
```python
def p2g_GPU_1(positions, g_mass, g_vel, params, gid):
    # ... compute weight w and grid node index gnode ...
    wm = w * params[0]                          # weighted mass
    ATOMIC_ADD_F(g_mass, gnode, wm)             # safe concurrent scatter
    ATOMIC_ADD_F(g_vel, gnode * 3,     wm * vx)
    ATOMIC_ADD_F(g_vel, gnode * 3 + 1, wm * vy)
    ATOMIC_ADD_F(g_vel, gnode * 3 + 2, wm * vz)
```

> **Note on P2G in MPM:** The above P2G kernel also requires `FLOOR` to locate
> grid nodes (see above).  Both `FLOOR` and `ATOMIC_ADD_F` are needed before
> P2G can fully run on GPU.

## Build prerequisites

| Platform | Requirement |
|---|---|
| macOS | OpenCL is part of the system framework – no extra install needed |
| Linux | `sudo apt install ocl-icd-opencl-dev opencl-headers` |
| Windows | Install GPU vendor drivers: NVIDIA CUDA Toolkit, AMD ROCm, or Intel OpenCL SDK |

## Runtime behaviour
- The OpenCL platform and device are initialised **once** on the first GPU call and reused for all subsequent calls.
- A GPU device is preferred; if none is available the runtime falls back to any OpenCL device (e.g., a CPU implementation).
- Each unique kernel source is **compiled and cached** on first invocation; repeated calls to the same GPU function reuse the cached `cl_kernel`.
- Data is staged `double → float` before upload and `float → double` after read-back (OpenCL kernels operate on `float`).
- If OpenCL initialisation fails at runtime a diagnostic is printed to `stderr` and execution returns immediately.
- The `GPU_ENABLED` macro must be set at compile time (via `-DGPU_ENABLED=ON`). Builds without it contain **no OpenCL code** and have no OpenCL runtime dependency.

## Explicit GPU synchronisation — `GPU_SYNC`

By default, GPU kernels dispatched with `_GPU_N` functions are **non-blocking**: the OpenCL
command is queued but the CPU continues immediately.  This allows many kernel launches to
be batched together without the CPU stalling after every one — which is the key to high GPU
throughput.

However, **any time the host (CPU) side needs to read back a value that a GPU kernel has
written**, you must explicitly drain the GPU queue for that array first.  The built-in
`GPU_SYNC` does exactly that.

```python
GPU_SYNC(array)
```

`GPU_SYNC(array)` issues a **blocking** `clEnqueueReadBuffer` for the given array, flushing
all previously enqueued GPU work and copying the updated data back to the host buffer.  It
is a no-op for arrays that are not GPU-backed (e.g., host-only arrays).

### When to use `GPU_SYNC`

| Situation | Action |
|---|---|
| Reading an array element in a Python host `while` loop after a GPU kernel has written it | Call `GPU_SYNC(array)` once before the loop |
| Passing a GPU-written array to a host function or `exec` call | Call `GPU_SYNC(array)` before the call |
| `dump array /path` after GPU kernel(s) wrote it | Call `GPU_SYNC(array)` before `dump` |
| Using a GPU-written array only as input to the next GPU kernel (no host read) | **No `GPU_SYNC` needed** — GPU→GPU is handled automatically |

### Example — batched transformer stack

```python
# 120+ GPU kernels dispatched with no CPU stalls …
layernorm_GPU_1(hidden, ln_g, ln_b, h_ln, n_seq)
matmul_bias_GPU_2(h_ln, c_attn_w, c_attn_b, qkv, kp, n_seq, 2304)
# … more kernels …
matmul_bias_GPU_2(h_ff, c_fc_proj_w, c_fc_proj_b, h_out_buf, kp, n_seq, 768)

# CPU needs to read `hidden` and `h_out_buf` in the next loop → sync first
GPU_SYNC(hidden)
GPU_SYNC(h_out_buf)
_i = 0
while _i < n_seq * 768:
    hidden[_i] = hidden[_i] + h_out_buf[_i]
    _i = _i + 1
```

Without the `GPU_SYNC` calls, `hidden` and `h_out_buf` would still hold **stale** values from
before the last GPU kernels ran, producing silently wrong results.

### Selective result return — `RETURN`

By default, after execution completes `arrChangeMap()` reports **every** array that was modified
(comparing each element against its pre-execution snapshot).  For large models with hundreds of
weight arrays this means the JNI result map can contain far more data than the caller needs.

The `RETURN` built-in lets user code explicitly name the arrays that should appear in the result.
If `RETURN` is called at any point during execution, **only** the listed arrays are included in
`arrChangeMap()`; all other modified arrays are silently dropped.  If `RETURN` is never called
the behaviour is unchanged — all modified arrays are returned.

```python
RETURN(arr1, arr2, ..., arrN)
```

`RETURN` accepts any number of array arguments and marks each with an internal flag at runtime.
It is a no-op for the computation itself (it does not stop execution or modify any values).

### Example

```python
hidden = [0 for _ in range(768)]
kv_cache = [0 for _ in range(4096)]
weights = [0 for _ in range(131072)]  # large weight buffer

# ... kernel calls that write to all three arrays ...

# Only return the outputs the caller actually needs
RETURN(hidden, kv_cache)
```

Without `RETURN`, `weights` and every other modified array would be serialised back through JNI
even though the caller only needs `hidden` and `kv_cache`.

### Behaviour summary

| Script contains `RETURN(...)`? | What `arrChangeMap()` returns |
|---|---|
| No | All modified arrays with their changed indexes (unchanged default) |
| Yes | Only the listed arrays, still reporting only changed indexes |

### `RETURN` vs `GPU_SYNC`

`GPU_SYNC` and `RETURN` are independent and complementary:

| | Purpose | When to use |
|---|---|---|
| `GPU_SYNC(arr)` | Flush the GPU queue and read `arr` back to the CPU | Before the host reads a GPU-written array |
| `RETURN(arr1, ...)` | Filter which arrays are included in the final result | To reduce serialisation overhead when only a subset of arrays is needed |

## Explicit GPU memory release — `RELEASE_MEM` and `LOAD_MEM`

Every array a `_GPU_N` kernel touches gets its own GPU buffer, and buffers are never freed
automatically while the process is running. For large models (e.g. dozens of transformer
layers, each with several 4-bit packed weight/scale arrays plus K/V caches) this means **all**
per-layer buffers stay resident on the GPU simultaneously. On memory-constrained devices
(e.g. ~5 GB unified memory) this can exhaust GPU/unified memory and cause an OOM crash.

`RELEASE_MEM` and `LOAD_MEM` give user code explicit control over when a buffer actually
occupies GPU memory:

```python
RELEASE_MEM(array)   # Frees the array's GPU buffer immediately (host data is untouched)
LOAD_MEM(array)       # (Re)allocates the GPU buffer and uploads the current host data
```

- **`RELEASE_MEM(array)`** calls `clReleaseMemObject` on the array's buffer and clears it, so the
  memory is returned to the driver right away. The host-side (CPU) copy of the array is never
  touched, so the data itself is not lost.
- **`LOAD_MEM(array)`** allocates a fresh GPU buffer for the array from its current host data.
  It is the correct counterpart to `RELEASE_MEM` — plain `GPU_LOAD` only writes into an
  *already-existing* buffer, so it cannot bring back an array that has been released.

> **Mandatory, not optional.** A `_GPU_N` kernel dispatch performs **no** implicit buffer
> allocation or upload of its own — it only does `clSetKernelArg` + `clEnqueueNDRangeKernel`
> against whatever GPU buffer the array already has. This means:
> - **`LOAD_MEM(array)` is required** before the *first* time an array is passed as a data
>   argument to any `_GPU_N` kernel, and again after any `RELEASE_MEM(array)` on it. Skipping
>   this is not silently "slow" — it is a hard error: the kernel logs
>   `missing LOAD_MEM(array) before first use` and the dispatch is skipped entirely (the
>   kernel's outputs are left unchanged).
> - **`GPU_LOAD(array)` is required** any time host (CPU) code mutates an array that already has
>   a live GPU buffer (e.g. `l_idx_arr[0] = l_idx`) and the *next* kernel dispatch needs to see
>   that new value. `GPU_LOAD` only writes into an existing buffer — it never allocates one — so
>   it must not be used as a substitute for `LOAD_MEM` on an array that hasn't been loaded yet
>   (or was just released).

### When to use these

`RELEASE_MEM` is intended for **immutable** arrays (4-bit packed weights, scale tables, RoPE
cos/sin tables, etc.). Never release an array that a GPU kernel *writes* to (activation
buffers, KV caches, etc.) without first reading its current value back with `GPU_SYNC` —
`RELEASE_MEM` does not sync, so releasing a GPU-resident write target discards whatever the
GPU hasn't yet written back to the host copy.

> **Release/reload has a cost — every call is buffer churn.** `RELEASE_MEM` +
> `LOAD_MEM` on the same array is a `clReleaseMemObject` +
> `clCreateBuffer` pair, not a full data copy, for weight arrays uploaded with
> `CL_MEM_USE_HOST_PTR` (see `isBinaryLoaded` in the "Direct CSV Population" section above) —
> on unified-memory devices (e.g. Apple Silicon) this is cheap since no bytes actually move.
> It is still real per-call overhead, though, so only cycle an array through
> `RELEASE_MEM`/`LOAD_MEM` when you specifically need to cap peak GPU memory (e.g. to fit a
> multi-layer model on a memory-constrained device); don't do it reflexively for arrays that
> are about to be reused within the same layer or the same kernel dispatch.

### Pattern 1 — release once, at the very end

If you don't need to cap peak GPU memory mid-run, the simplest use is to release every large
buffer exactly once, right before the process is about to end (or before the very last use of
a single-pass computation), instead of releasing/reloading inside a hot loop:

```python
# ... 32 transformer layers, prefill + decode loop, all reusing the same
# per-layer weight/cache buffers on every iteration ...

# Generation is finished — nothing on the GPU is needed anymore before the
# host reads back `generated_tokens`. Release every large buffer once,
# instead of thrashing release/reload inside the decode loop.
RELEASE_MEM(l0_qkv_packed)
RELEASE_MEM(l0_qkv_scales)
RELEASE_MEM(l0_k_cache)
RELEASE_MEM(l0_v_cache)
# ... one RELEASE_MEM call per large buffer ...

GPU_SYNC(generated_tokens)
```

### Pattern 2 — per-layer streaming (only one layer's weights resident at a time)

When peak GPU memory itself is the constraint (e.g. a multi-layer transformer that would
otherwise keep every layer's weights resident simultaneously), `LOAD_MEM` an immutable
layer's weights right before that layer's kernels run, and `RELEASE_MEM` them again
immediately after — in **every** place that layer is used, including inside a repeated decode
loop. Because weight arrays are read-only inputs (never a GPU write target) and are typically
`isBinaryLoaded` (zero-copy `CL_MEM_USE_HOST_PTR`), the repeated release/reload is just cheap
buffer-object churn, not a data copy, and at any instant only one layer's weights occupy GPU
memory:

```python
# ── Layer N ──
LOAD_MEM(lN_qkv_packed)
LOAD_MEM(lN_qkv_scales)
LOAD_MEM(lN_o_packed)
LOAD_MEM(lN_o_scales)
LOAD_MEM(lN_gate_up_packed)
LOAD_MEM(lN_gate_up_scales)
LOAD_MEM(lN_down_packed)
LOAD_MEM(lN_down_scales)
rmsnorm_GPU_1(h_state, lN_ln1_g, h_ln1, n_seq)
matmul_4bit_GPU_2(h_ln1, lN_qkv_packed, lN_qkv_scales, qkv_buf, kp_qkv, n_seq, 9216)
# ... rest of layer N's kernels ...
residual_add_GPU_2(h_state, h_out_buf, n_seq, 3072)
RELEASE_MEM(lN_qkv_packed)
RELEASE_MEM(lN_qkv_scales)
RELEASE_MEM(lN_o_packed)
RELEASE_MEM(lN_o_scales)
RELEASE_MEM(lN_gate_up_packed)
RELEASE_MEM(lN_gate_up_scales)
RELEASE_MEM(lN_down_packed)
RELEASE_MEM(lN_down_scales)
```

Note that KV caches (`lN_k_cache`/`lN_v_cache`) are deliberately **not** part of this
per-layer cycle — they are write targets updated by every layer every step, so they must stay
GPU-resident across the whole run (they are only released once, at the very end, alongside the
other shared buffers per Pattern 1). See
[`ramanujan-test-codes/phi3/phi3_transformer_stack_4bit.py`](../ramanujan-test-codes/phi3/phi3_transformer_stack_4bit.py)
for the full worked example across all 32 layers, in both the prefill pass and the decode loop.

## `GPU_SYNC` vs the previous implicit sync model

Before `GPU_SYNC` was introduced, every `_GPU_N` call automatically issued a blocking
`clEnqueueReadBuffer` + `clFinish` after the kernel, preventing any batching.  The overhead
measured ~11% of total inference time on macOS (visible as `IOKit → IOGPU → clFinish` in
profiler traces).  With the explicit model, the GPU queue is drained **only** at the
necessary points, and all other kernel dispatches remain asynchronous.



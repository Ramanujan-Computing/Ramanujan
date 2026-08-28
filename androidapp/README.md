# Ramanujan Android App

On-device worker that runs the Ramanujan interpreter (Phi-3 4-bit) with GPU
(OpenCL) acceleration on Android.

## Bugfix: "Bis Bis" garbage output on Android (Adreno)

### Symptom
The same interpreter and kernels produced correct text on the macOS homelab
worker but a constant garbage token ("Bis Bis") on Android, regardless of the
prompt.

### Root cause
The GPU math was correct all along. The real bug was **truncated weight files
in the on-device cache**.

Weight tensors are downloaded from the server and cached at
`getCacheDir()/local_rj_bin_<md5>.bin`. Three flaws in
[`WorkerService.java`](app/src/main/java/in/ramanujan/app/WorkerService.java)
combined to persist partial files:

1. **`downloadFile()` wrote directly to the final cache path.** Any interruption
   — a dropped connection, the download thread pool being shut down mid-flight,
   or the app being killed — left a partial `.bin` at its final name.
2. **No `Content-Length` verification.** A truncated HTTP body ends the read at
   EOF without throwing, silently producing a short file.
3. **The cache-hit check was `exists() && length() > 0`**, so any non-empty file
   was treated as complete and served forever.

The native loader
([`ArrayValue.cpp`](../ramanujan-native/native/ruleEngineObject/dataContainer/array/ArrayValue.cpp))
maps `totalSize` floats and zero-pads whatever the file is missing, so a short
file becomes a partly-zero weight tensor. For the largest tensor (`gate_up`,
32 MB) roughly 40% of values were zero, which corrupted the SwiGLU/down
projections, exploded the residual stream over 32 layers, and collapsed the
logits to a single constant token.

### Fix
In [`WorkerService.java`](app/src/main/java/in/ramanujan/app/WorkerService.java):

- **Atomic download.** `downloadFile()` now writes to a unique temp file
  (`<dest>.part-<uuid>`), verifies the received byte count equals the server's
  `Content-Length`, `flush()` + `fd.sync()`s to stable storage, then atomically
  `renameTo()`s the final path. A partial download can never appear at the real
  cache name again; the temp file is deleted on any failure.
- **Size-validated cache hits.** The cache check now computes
  `expectedBytes = product(dimensions) * 4` and only reuses a cached file whose
  length matches exactly. Any wrong-size (leftover partial) file is treated as a
  cache miss and re-downloaded, which **self-heals** truncated files already on
  the device — no manual cache clear needed.

### Related note (native loader)
[`ArrayValue.cpp`](../ramanujan-native/native/ruleEngineObject/dataContainer/array/ArrayValue.cpp)
keeps a **file-backed** `mmap` for weights (anonymous region over `totalSize`
plus a read-only `MAP_FIXED` file overlay). These pages are reclaimable page
cache, which keeps the ~2.7 GB of Phi-3 weights within the memory budget of a
~3.6 GB device. Android creates GPU buffers with `CL_MEM_COPY_HOST_PTR`, whose
CPU-side copy faults these pages in normally, so no explicit prefault is needed.
(An earlier attempt that `pread`-loaded weights into dirty anonymous memory
pinned all weights as non-reclaimable RAM and caused an OOM; the file-backed
mapping avoids that.)

### Verifying
```sh
adb logcat -s RamanujanGPU | grep -E "Re-downloading|ArrayValue] LOAD"
```
A healthy load shows every weight file with `fileSize == product(dim) * 4` and
`fcount == totalSize`; corrupt cache entries log a one-time "Re-downloading" line
before being refetched.

# MPM Anymal on Ramanujan

A scaled-down port of Newton's [`mpm_anymal`](https://github.com/newton-physics/newton/blob/main/newton/examples/mpm/example_mpm_anymal.py)
example, where every per-frame physics calculation runs on **Ramanujan's
OpenCL GPU runtime** instead of on NVIDIA Warp / MuJoCo. Newton is used
purely as the viewer to play back the trajectory Ramanujan produced.

> **Credit:** The original `mpm_anymal` simulation, the gait, and the
> visual conventions used here come from the Newton physics engine
> (Disney Research, Google DeepMind, NVIDIA — under the Linux Foundation).
> See `ramanujan/NOTICE.md` for the full credit and thanks.

---

## What's in this directory

| File | Role |
|---|---|
| `mpm_anymal_kernel.py` | Ramanujan kernel. Three OpenCL kernels (`apply_gravity_GPU_1`, `apply_feet_GPU_1`, `integrate_GPU_1`) plus host-side foot kinematics. Runs entirely under `rj` / the developer-console fat JAR. |
| `run_mpm_anymal.py` | Python orchestrator. Loops frames, calls Ramanujan, collects positions/velocities/foot transforms, then hands them to Newton's `ViewerGL` for playback. **No physics here.** |
| `README.md` | This file. |

All physics — gravity, ground bounce, four-foot collision push, integration — happens
inside `mpm_anymal_kernel.py`. The orchestrator only does file I/O, subprocess
invocation, and viewer wiring.

---

## What it simulates

* 256 sand particles in an 8×8×4 grid spawned by the kernel itself on frame 0.
* A four-legged kinematic walker (a stand-in for ANYmal C) doing a diagonal
  trot (LF + RH lift together, RF + LH lift together) while the body translates
  forward in `+y`.
* Each foot is a sphere of radius `FOOT_RADIUS_PARAM` that pushes any particle
  it overlaps; particles damp, bounce off the ground, and integrate via Euler.

This is **not** a faithful MPM solver — there's no grid transfer / APIC step —
but it captures the spirit of `mpm_anymal`: a quadruped trampling through a
particle medium, with the heavy per-particle work running on the GPU.

---

## Why a simplified version

Newton's full `mpm_anymal` example imports PyTorch (for the trained walking
policy), NVIDIA Warp (for kernels), MuJoCo (for the rigid-body solver), and
NumPy, and parses URDF for the ANYmal C robot. Ramanujan's translator
supports a strict subset of Python (no imports, no classes, no `for` loops,
no power/modulo, no strings — see
[`ramanujan/README.md`](../../README.md#python-support-in-development)),
so a 1:1 port isn't possible today. The simplification keeps the user-facing
shape of the demo while staying inside what Ramanujan can compile.

---

## Running it

### 1. Install Newton (for the viewer only)

```bash
pip install "newton[examples]"
```

### 2. Build Ramanujan with GPU support

From the repository root:

```bash
cd ramanujan-native/native
mkdir -p build-gpu && cd build-gpu
cmake -DENABLE_GPU=ON ..
cmake --build .
```

Then build the developer-console fat JAR (`mvn clean install` from the
repo root).

### 3. Point the orchestrator at the JAR

```bash
export RAMANUJAN_FAT_JAR=/absolute/path/to/developer-console-1.0-SNAPSHOT-fat.jar
```

(Alternatively, install the `rj` alias via `ramanujan/install_ramanujan.sh`
and the orchestrator will pick it up automatically once you set
`RAMANUJAN_FAT_JAR`; the alias itself uses `execute`, but the dump-based
flow needs `execute_inline`.)

### 4. Run

```bash
# Interactive OpenGL viewer
python3 run_mpm_anymal.py --frames 200

# Headless USD output
python3 run_mpm_anymal.py --frames 200 --viewer usd --output-path mpm_anymal.usd
```

---

## Data flow per frame

```
positions.csv   ─┐
velocities.csv  ─┼─►  rj execute_inline mpm_anymal_kernel.py
params.csv      ─┘                │
                                  │     ┌── apply_gravity_GPU_1
                                  ├──►──┼── apply_feet_GPU_1
                                  │     └── integrate_GPU_1
                                  ▼
                       dump positions  ─►  out_positions_<frame>.csv
                       dump velocities ─►  out_velocities_<frame>.csv
                       dump feet       ─►  out_feet_<frame>.csv
                                  ▼
                  Newton ViewerGL.log_state(state)   (playback only)
```

The orchestrator never reads `positions[i]` and computes anything from it —
the values flow straight from a Ramanujan-produced CSV into the viewer.

---

## Constraints honored

* All physics runs inside Ramanujan (host-side `while` loops + `_GPU_N`
  OpenCL kernels). The orchestrator is pure I/O and viewer plumbing.
* GPU kernels follow the `funcName_GPU_N` translator contract — N work-item
  dimensions, array data args, no recursive or nested-GPU calls.
* No `for`, `**`, `%`, `and`/`or`, `elif`, imports, or strings inside the
  kernel script.
* Builtins (`SIN`) are used host-side only, since the helper-function
  constraints in the kernel translator forbid arbitrary builtin calls
  inside `_GPU_N` bodies.

# MPM Snow Ball on Ramanujan

A port of Newton's [`mpm_snow_ball`](https://github.com/newton-physics/newton/tree/main/newton/examples/mpm)
example, where **every per-frame physics calculation runs on Ramanujan's
OpenCL GPU runtime** instead of on NVIDIA Warp. Newton is used purely as the
viewer to play back the trajectory Ramanujan produced.

> **Credit:** The original `mpm_snow_ball` simulation and the visual
> conventions used here come from the Newton physics engine
> (Disney Research, Google DeepMind, NVIDIA — under the Linux Foundation).
> See `ramanujan/NOTICE.md` for the full credit and thanks.

---

## What's in this directory

| File | Role |
|---|---|
| `mpm_snow_ball_kernel.py` | Ramanujan kernel. Two OpenCL kernels (`apply_gravity_GPU_1`, `integrate_GPU_1`) plus host-side sphere initialisation. Runs entirely under `rj` / the developer-console fat JAR. |
| `run_mpm_snow_ball.py` | Python orchestrator. Loops frames, calls Ramanujan, collects positions/velocities, optionally saves to disk, then plays back in Newton's viewer. **No physics here.** |
| `view_mpm_snow_ball.py` | Standalone viewer. Opens previously saved frame data (`.pkl` files) in Newton without recomputing. |
| `README.md` | This file. |

---

## What it simulates

* A configurable number of snow particles (default 257, up to ~60 000) arranged
  in a sphere using a cubic lattice. Physical ball diameter is **~0.30 m**
  regardless of particle count; resolution (particle spacing) scales instead.
* The ball is thrown at **3 m/s** in the +y direction toward a flat vertical
  wall at `y = 0`. It starts at `(0, −0.8, 1.2)` m — ~16 frames until impact.
* On impact: particles spread outward with near-zero restitution (snow splats,
  doesn't bounce), then fall under gravity and settle on the ground plane.

Physics kernels on GPU (Ramanujan OpenCL):

| Kernel | Work | Description |
|---|---|---|
| `apply_gravity_GPU_1` | N particles | Adds `gravity × dt` to each `vz` |
| `integrate_GPU_1` | N particles | Euler step + wall collision (y) + ground collision (z) + damping |

Frame 0 is host-side only: places particles in a sphere, assigns initial +y velocity.

---

## Key differences from `mpm_anymal`

| | `mpm_anymal` | `mpm_snow_ball` |
|---|---|---|
| Particle layout | Flat rectangular bed | Sphere (inscribed in cubic lattice) |
| Moving objects | 4 kinematic feet + torso | None (static wall) |
| Restitution | Medium (sand) | Very low (snow splats) |
| Outputs dumped | positions, velocities, feet | positions, velocities |
| Body count in viewer | 5 (4 feet + torso, animated) | 1 (wall, static) |

---

## Running it

### 1. Create and activate a Python virtual environment

```bash
cd /path/to/ramanujan_oss
python3 -m venv .venv
source .venv/bin/activate
```

### 2. Install viewer dependencies

```bash
pip install "warp-lang" "newton[examples]" "ast2json"
```

### 3. Build Ramanujan with GPU support

```bash
cd ramanujan-native/native
mkdir -p build-gpu && cd build-gpu
cmake -DGPU_ENABLED=ON ..
cmake --build .
```

Then build the developer-console fat JAR:

```bash
cd /path/to/ramanujan_oss
mvn clean install
```

### 4. Run the simulation

```bash
cd ramanujan-test-codes/mpm_snow_ball

# Default — 257 particles, interactive OpenGL viewer
python3 run_mpm_snow_ball.py --frames 200

# Higher resolution
python3 run_mpm_snow_ball.py --frames 200 --num-particles 1000
python3 run_mpm_snow_ball.py --frames 200 --num-particles 5000

# Headless USD output
python3 run_mpm_snow_ball.py --frames 200 --viewer usd --output-path snow_ball.usda

# Null viewer — GPU physics only, useful for benchmarking
python3 run_mpm_snow_ball.py --frames 200 --viewer null
```

#### Saving and replaying frame data

```bash
# Save frames (no viewer opens)
python3 run_mpm_snow_ball.py --frames 200 --num-particles 1000 --save-frames

# View saved frames later (no recomputation)
python3 view_mpm_snow_ball.py frames_260503_143025.pkl
```

#### Homelab mode (distributed workers)

Same as `mpm_anymal` — start the homelab server, connect Android workers, then:

```bash
python3 run_mpm_snow_ball.py --frames 200 --homelab
python3 run_mpm_snow_ball.py --frames 200 --homelab --homelab-url http://192.168.1.42:8888
```

---

## Particle count and performance

| `--num-particles` | Actual (sphere) | Grid side | Approx. time / frame |
|---|---|---|---|
| 256 (default) | 251 | 9³ | ~0.75 s |
| 1 000 | 895 | 13³ | ~0.9 s |
| 5 000 | 5 497 | 23³ | ~1.2 s |
| 50 000 | 50 733 | 47³ | ~2–3 s |

The sphere count rarely matches the requested count exactly — `compute_grid` picks the odd
cube side whose inscribed sphere is closest to the requested value.

> **Platform limit ~57 000 particles:** Arrays larger than ~180 000 floats are
> loaded via a binary fast-path that is currently unavailable to the `dump`
> command. Keep `--num-particles` at or below **~57 000** (side=49³, 57 747 particles)
> until this is resolved.

---

## Data flow per frame

```
positions.csv   ─┐
velocities.csv  ─┼──►  rj mpm_snow_ball_kernel.py
params.csv      ─┘                │
                                  │     ┌── apply_gravity_GPU_1   (N work items)
                                  ├──►──┤
                                  │     └── integrate_GPU_1       (N work items)
                                  ▼
                       dump positions  ──►  out_positions_<frame>.csv
                       dump velocities ──►  out_velocities_<frame>.csv
                                  ▼
                  Newton ViewerGL.log_state(state)   (playback only)
```

---

## Constraints honored

* All physics runs inside Ramanujan (`_GPU_N` OpenCL kernels + host-side sphere init).
* GPU kernels follow the `funcName_GPU_N` translator contract.
* No `for`, `**`, `%`, `and`/`or`, `elif`, imports, or strings inside the kernel.
* Grid dimensions, spacing, and particle count are passed via `params.csv` so
  the same kernel binary handles any particle count without recompilation.

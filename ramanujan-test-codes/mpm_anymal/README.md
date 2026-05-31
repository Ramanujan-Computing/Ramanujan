# MPM Anymal on Ramanujan

A scaled-down port of Newton's [`mpm_anymal`](https://github.com/newton-physics/newton/blob/main/newton/examples/mpm/example_mpm_anymal.py)
example, where **every per-frame physics calculation runs on Ramanujan's
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
| `run_mpm_anymal.py` | Python orchestrator. Loops frames, calls Ramanujan, collects positions/velocities/foot transforms, optionally saves to disk, then plays back in Newton's viewer. **No physics here.** |
| `view_mpm_anymal.py` | Standalone viewer. Opens previously saved frame data (`.pkl` files) in Newton without recomputing. Lets you replay trajectories anytime. |
| `README.md` | This file. |

All physics — gravity, ground bounce, four-foot collision push, integration — happens
inside `mpm_anymal_kernel.py`. The orchestrator only does file I/O, subprocess
invocation, and viewer wiring.

---

## What it simulates

* A configurable number of sand particles (default 256, up to 1 million+) in a
  flat square bed spawned by the kernel on frame 0. Grid dimensions and spacing
  are computed automatically so the bed is always ~1 m × 1 m.
* A four-legged kinematic walker (a stand-in for ANYmal C) doing a diagonal
  trot (LF + RH lift together, RF + LH lift together) while the body translates
  forward in `+y`. The **torso box** moves with the robot in the viewer.
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

### 1. Create and activate a Python virtual environment

```bash
cd /path/to/ramanujan_oss
python3 -m venv .venv
source .venv/bin/activate   # macOS / Linux
# .venv\Scripts\activate    # Windows
```

### 2. Install viewer dependencies and Ramanujan's Python AST tooling

```bash
pip install "warp-lang" "newton[examples]" "ast2json"
```

`warp-lang` and `newton` are only needed for visualization.
**The core GPU physics runs entirely on Ramanujan** — no Newton/Warp involvement
in the simulation itself.

### 3. Build Ramanujan with GPU support

From the repository root:

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

### 4. (Optional) Set environment variables

The orchestrator looks for the JAR at:
```
ramanujan-oss/ramanujan/developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar
```

Override if needed:
```bash
export RAMANUJAN_FAT_JAR=/absolute/path/to/developer-console-1.0-SNAPSHOT-fat.jar
export JAVA_HOME=/path/to/jdk          # must match JDK used to build the native lib
export RAMANUJAN_WS=/path/to/writable  # directory where libnative.dylib is staged
```

### 5. Run the simulation

```bash
# Default — 256 particles, interactive OpenGL viewer
python3 run_mpm_anymal.py --frames 200

# Scale up particle count (GPU handles all particles in parallel on Ramanujan)
python3 run_mpm_anymal.py --frames 200 --num-particles 10000
python3 run_mpm_anymal.py --frames 200 --num-particles 100000
python3 run_mpm_anymal.py --frames 50  --num-particles 1000000   # 1 M particles

# Headless USD output
python3 run_mpm_anymal.py --frames 200 --viewer usd --output-path mpm_anymal.usd

# Null viewer — GPU physics only, no visualization (useful for benchmarking)
python3 run_mpm_anymal.py --frames 200 --viewer null
```

**All physics — gravity, collision, particle integration — executes on GPU through
Ramanujan's OpenCL runtime.** The orchestrator is pure I/O: it writes CSVs,
invokes `rj`, reads results, and pipes them to the viewer. No computation happens
in Python.

#### Saving and replaying frame data

Compute frames once and save them to disk for later viewing:

```bash
# Save frames to frames_YYMMDD_HHMMSS.pkl (no viewer opens)
python3 run_mpm_anymal.py --frames 50 --num-particles 10000 --save-frames

# View the saved frames anytime later (no recomputation!)
python3 view_mpm_anymal.py frames_260426_143025.pkl

# Export saved frames to USD
python3 view_mpm_anymal.py frames_260426_143025.pkl --viewer usd --output-path mpm_anymal.usda
```

The `.pkl` file contains all frame data (positions, velocities, foot transforms),
grid metadata, and timing information. This is useful for:
- **Sharing trajectories** — send the `.pkl` file to colleagues; they can view it
  without running the physics
- **Comparing runs** — compute once with different parameters, save results, and
  A/B view them
- **Batch processing** — compute while you work on other things, then view later
- **Archival** — keep a record of interesting simulations

#### Particle count and performance

| `--num-particles` | Grid | Approx. time / frame |
|---|---|---|
| 256 (default) | 8 × 8 × 4 | ~0.75 s |
| 10 000 | 50 × 50 × 4 | ~1 s |
| 60 000 | 122 × 122 × 4 | ~2–3 s |

The GPU kernel work scales with particle count; the per-frame bottleneck at large
counts is CSV I/O between the Ramanujan JVM and the orchestrator.

> **Current platform limit — ~60 000 particles:**
> When an input array exceeds ~180 000 float values, Ramanujan's translator switches
> to a binary fast-path for loading (logged as `Binary-populated array`). Arrays
> loaded via the binary path are currently unavailable to the `dump` command —
> the console returns "Array not found or empty" and the orchestrator gets an empty
> result. Until this is fixed in the Ramanujan platform the practical maximum is
> **~60 000 particles** (180 000 floats per array). The `--num-particles` argument
> accepts any value, but values above ~60 000 will produce empty output frames.

---

## GPU Physics on Ramanujan

**Every physics calculation happens on GPU via Ramanujan's OpenCL backend:**

- **Frame 0 initialization**: Particle grid layout computed on Ramanujan host-side;
  grid dimensions and spacing read from the `params` CSV so any particle count works
  without recompiling the kernel.
- **Gravity kernel** (`apply_gravity_GPU_1`): All N particles updated in parallel on GPU.
- **Foot collision kernel** (`apply_feet_GPU_1`): Each particle checks 4 feet,
  accumulates impulses — fully parallel on GPU.
- **Integration kernel** (`integrate_GPU_1`): Euler step + ground collision + damping
  — fully parallel on GPU.

The orchestrator (`run_mpm_anymal.py`) is **zero-compute**:
- Writes input CSVs (positions, velocities, 15-element params)
- Invokes `rj execute_inline` to dispatch the Ramanujan kernel
- Reads output CSVs via `dump` commands
- Replays trajectory in Newton's viewer (no physics)

---

## Viewer

The Newton `ViewerGL` opens after all frames are computed. The viewer loops the
animation continuously — close the window to exit.

**Controls:**
- Drag to rotate camera
- Scroll to zoom
- Right-click drag to pan

**What you see:**
- Sand particles (rendered as spheres)
- 4 foot boxes trampling through the sand
- **Torso box** moving forward with the robot body

---

## Data flow per frame

```
positions.csv   ─┐
velocities.csv  ─┼─►  rj execute_inline mpm_anymal_kernel.py
params.csv      ─┘                │
                                  │     ┌── apply_gravity_GPU_1   (N work items)
                                  ├──►──┼── apply_feet_GPU_1      (N work items)
                                  │     └── integrate_GPU_1       (N work items)
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

## Homelab Mode: Distributed Worker Execution

By default, `run_mpm_anymal.py` runs physics on a single machine using a persistent local JVM server.
You can also run it in **homelab mode**, where a long-running homelab server orchestrates GPU work
across multiple worker machines (e.g., Android phones, other desktops), and the Python orchestrator
submits frames via HTTP.

### Homelab Architecture

```
                 Homelab Server (once)      Worker Machines
                 (java -jar homelab)        (Android + ramanujan-device-common)
                        |                            ^
                        |                            |
                 /pings/open (poll)    /task/complete (results)
                        |<---------workers--------->|
                        |
                        ^
                        | HTTP /orchestrator/run    (blocking until done)
                        | HTTP /orchestrator/dump   (read output arrays)
                        |
                   Orchestrator (per-frame)
                  (run_mpm_anymal.py --homelab)
```

The homelab server is a **persistent process** that:
- Accepts frame compilation requests from the orchestrator via HTTP
- Distributes DAG elements to connected workers  
- Merges results as workers complete tasks
- Writes CSV output files that the orchestrator reads

The orchestrator (Python script) is **stateless and per-frame**:
- Sends one `/orchestrator/run` request per frame (blocks until all workers finish)
- Sends `/orchestrator/dump` requests to retrieve output arrays as CSV files
- No JVM startup per frame; no local compute

### Starting a Homelab Server

Run the developer-console in homelab mode on a dedicated machine (or your laptop):

```bash
# Port defaults to 8888; optionally override
java -jar developer-console-1.0-SNAPSHOT-fat.jar homelab 8888
```

You will see output like:
```
HOMELAB_READY
HOMELAB_ADDRESS http://192.168.1.42:8888
```

The homelab server stays running and listens for worker connections and orchestrator commands from stdin.

### Connecting Android Worker Devices

1. **Build the Android app** (if not already built):
   ```bash
   cd androidapp && ./gradlew assembleDebug
   ```
   Install to your Android device(s).

2. **Start the app**:
   - The app shows **Device IP** at the top (e.g., `192.168.1.100`)
   - In the **Server URL** field, enter the homelab server address:
     ```
     http://192.168.1.42:8888
     ```
   - Tap **Start Workers** — the app spawns one orchestration thread per CPU core,
     all connecting to your homelab server.

3. **View logs**:
   - Tap **Show Logs** to see GPU execution info:
     ```
     [GPU] 1 GPU kernel(s) detected: [integrate_GPU_1]
     [GPU] run latency: 245 ms (kernels: [integrate_GPU_1])
     ```

### Running the Orchestrator Against the Homelab Server

Once the homelab server is running (in Terminal 1) and workers are connected:

```bash
# Connect to homelab server running locally on port 8888 (default)
cd ramanujan-test-codes/mpm_anymal
python3 run_mpm_anymal.py --frames 200 --homelab

# Or explicitly specify homelab server URL (e.g., different machine)
python3 run_mpm_anymal.py --frames 200 --homelab --homelab-url http://192.168.1.42:8888
```

The orchestrator will:
1. POST to `/orchestrator/run` with kernel path and input CSV paths (blocking until all workers finish)
2. POST to `/orchestrator/dump` for each output array; server writes CSV files locally
3. Read the CSV output files and display progress
4. Playback in Newton viewer as usual

**No JVM is started by the orchestrator.** All frame computation happens on the homelab server (and its connected workers).

### Homelab Workflow Example

#### Step 1: Start the Homelab Server (once, stays running)

**Terminal 1** (desktop machine 192.168.1.42):
```bash
java -jar developer-console-1.0-SNAPSHOT-fat.jar homelab 8888
```

Output:
```
HOMELAB_READY
HOMELAB_ADDRESS http://192.168.1.42:8888
[Homelab] HTTP server listening on :8888
```

The server now accepts:
- Worker connections via `/pings/open` and `/pings/heartbeat`
- Orchestrator requests via `/orchestrator/run` and `/orchestrator/dump`
- Task completion results via `/task/complete`

#### Step 2: Connect Android Workers

**Android Devices** (connected via WiFi to the same network):
1. Build and install the Android app: `cd androidapp && ./gradlew assembleDebug`
2. Open the app on each device
3. Device shows "Device IP: 192.168.1.50", "Device IP: 192.168.1.51", etc.
4. In the **Server URL** field, enter: `http://192.168.1.42:8888`
5. Tap **Start Workers** — the app spawns one orchestration thread per CPU core
6. Tap **Show Logs** to see GPU execution: `[GPU] run latency: 245 ms (kernels: [integrate_GPU_1])`

All workers begin polling the homelab server for tasks.

#### Step 3: Run the Orchestrator (frame-by-frame)

**Terminal 2** (same desktop or any machine on the network):
```bash
cd ramanujan-test-codes/mpm_anymal
python3 run_mpm_anymal.py --frames 50 --num-particles 10000 --homelab --homelab-url http://192.168.1.42:8888
```

The orchestrator:
- Writes input CSVs to a temp directory
- For each frame:
  - POSTs to `http://192.168.1.42:8888/orchestrator/run` (blocks until done)
  - POSTs to `/orchestrator/dump` to retrieve output CSVs
  - Reads the CSVs and continues
- After all frames, displays Newton viewer playback

**You can run multiple orchestrator instances in parallel** — they all submit work to the same homelab server, and workers execute all tasks in parallel.

### Performance Notes

- **Single machine (no homelab)**: ~0.75 s/frame (256 particles, 1 local JVM)
- **Homelab with 1 local worker**: ~1 s/frame (similar to single-machine, but via HTTP)
- **Homelab with 2 Android devices**: ~0.5 s/frame (divided across GPUs)
- **Homelab with 4+ devices**: scales with worker count and GPU availability
- **Multiple orchestrators**: can submit frames to the same homelab server in parallel; workers execute all tasks

> **Limitation**: Binary-loaded arrays (>180k floats) are unavailable to the `dump` command,
> so particle counts are capped at ~60k with current Ramanujan version. This affects both
> local and homelab modes equally.

### Homelab Server HTTP Endpoints

For orchestrator integration (used by `run_mpm_anymal.py --homelab`):

| Endpoint | Method | Body | Response | Purpose |
|---|---|---|---|---|
| `/orchestrator/run` | POST | `{"args": ["kernel.py", "csv1", ...]}` | `{"status": "SUCCESS\|ERROR"}` | Compile kernel, dispatch to workers, block until done |
| `/orchestrator/dump` | POST | `{"name": "arrayName", "path": "/local/path.csv"}` | `{"status": "SUCCESS\|ERROR"}` | Write array to local CSV file |

For worker connections (used by Android app):

| Endpoint | Method | Purpose |
|---|---|---|
| `/pings/open` | POST | Workers poll for task(s); server returns queued DAG element or null |
| `/pings/heartbeat` | POST | Workers ping to stay alive |
| `/task/complete` | POST | Workers submit result(s); server merges and counts down latch |

---

### Choosing Between Local and Homelab Mode

| Scenario | Mode | Command |
|---|---|---|
| **Testing locally on one machine** | Local | `python3 run_mpm_anymal.py --frames 200` |
| **Distributing across Android devices** | Homelab | Terminal 1: `java -jar developer-console.jar homelab`<br/>Terminal 2: `python3 run_mpm_anymal.py --frames 200 --homelab` |
| **Running multiple simulations in parallel** | Homelab | Start server once; run multiple orchestrators concurrently |
| **Running from a different machine** | Homelab | `python3 run_mpm_anymal.py --homelab --homelab-url http://server.ip:8888` |

**Homelab advantages:**
- Distributes GPU work across multiple devices
- Decouples orchestrator from compute — can run from anywhere
- Supports parallel orchestrator instances
- Server stays running; can reuse for multiple experiments

**Local mode advantages:**
- Simpler setup (no Android devices needed)
- Faster for single-machine testing
- No HTTP overhead

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
* Grid dimensions, spacing, and particle count are passed via the `params` CSV
  so the same kernel binary handles any particle count without recompilation.

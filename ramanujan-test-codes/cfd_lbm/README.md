# Lattice Boltzmann Method (LBM) CFD Simulation

This directory contains a highly optimized 2D fluid dynamics simulation written for the Ramanujan homelab platform. It simulates **fluid flow past a cylinder** to observe the classical [von Kármán vortex street](https://en.wikipedia.org/wiki/K%C3%A1rm%C3%A1n_vortex_street).

The simulation is implemented using the **D2Q9 Lattice Boltzmann Method (LBM)** with a BGK collision operator.

## Architecture & Design

To maximize performance and maintain state coherence, the simulation uses a **singleton kernel architecture**:
- **Zero Host-Device Overheads:** The entire simulation (15,000 time steps) runs inside a single GPU kernel invocation (`lbm_singleton.py`).
- **Live Memory:** All distribution functions (f_prev, f_next, f_post) stay alive in GPU memory for the entire run. This is critical because the tiny perturbations required to trigger asymmetric vortex shedding at this Reynolds number get destroyed if the state is round-tripped through CSV files between batches.
- **In-Kernel Frame Capture:** A dedicated GPU kernel (`copy_frame_GPU_1`) captures velocity slices every 100 steps into a pre-allocated mega-buffer, which is dumped to the host only once at the very end.

## Physics Parameters

- **Grid Size**: 300 x 50 cells
- **Reynolds Number (Re)**: 80
- **Time Steps**: 15,000 total (Frames are captured from steps 5,000 to 14,900 after the flow is fully established).
- **Collision Operator**: BGK (Bhatnagar-Gross-Krook)
- **Obstacle**: Bounce-back (no-slip) circular cylinder of radius 5, centered at (60, 25).
- **Boundary Conditions**: 
  - Inlet (x=0): Zou/He velocity Dirichlet (u=0.04, v=0)
  - Outlet (x=299): Zero-gradient outflow
  - Walls (y=0, y=49): Periodic wrapping
- **Symmetry Breaking**: A tiny upward velocity perturbation is applied above the cylinder during initialization to seed the instability and trigger vortex shedding, as GPU `float32` rounding noise alone is too perfectly symmetric.

## Files

- **`lbm_singleton.py`**: The Ramanujan Python kernel. It strictly adheres to Ramanujan's GPU compilation semantics (e.g., all array indices must be pre-computed into single variables, loops must be `while` constructs, no recursion).
- **`run_lbm_singleton.py`**: The orchestrator script. It submits the singleton kernel to the homelab worker, waits for it to finish, and dumps the velocity fields.
- **`visualize_lbm.py`**: Python script using `matplotlib` to render the velocity magnitude and vorticity (curl) fields into an animated GIF.
- **`lbm_output/`**: Directory where the raw CSV outputs and the final `simulation_video.gif` are saved.

*(Note: `lbm_ramanujan.py` and `run_lbm_simulation.py` are legacy files from an earlier sharded implementation that suffered from state-loss between batches).*

## How to Run

1. **Start the simulation:**
   ```bash
   python3 run_lbm_singleton.py --homelab-url http://localhost:8888
   ```
   *This submits the job to the homelab worker. It will take a few minutes to complete all 15,000 steps. Once done, it will download `u_frames.csv` and `v_frames.csv` to the `lbm_output/` directory.*

2. **Generate the animation:**
   ```bash
   python3 visualize_lbm.py
   ```
   *This reads the dumped frames and renders `lbm_output/simulation_video.gif` showing the velocity and vorticity fields.*

#!/usr/bin/env python3
# =============================================================================
# MPM Anymal on Ramanujan: orchestrator + Newton viewer playback
# =============================================================================
#
# Inspired by Newton's `mpm_anymal` example (Disney Research / Google
# DeepMind / NVIDIA — see NOTICE.md for full credit). The full example
# couples a trained PyTorch policy with implicit MPM sand on top of NVIDIA
# Warp; this scaled-down version runs all per-frame physics on Ramanujan's
# OpenCL GPU runtime instead, while Newton's viewer is used purely to
# render the Ramanujan-produced trajectory.
#
# This file does NO physics. It only:
#   1. Writes input CSVs (positions/velocities/params) for each frame.
#   2. Invokes the Ramanujan developer-console (`rj`) on
#      `mpm_anymal_kernel.py`, which runs the GPU kernels.
#   3. Reads back the per-frame state from the `dump` console command.
#   4. Feeds the recorded states into Newton's ViewerGL/ViewerUSD/ViewerNull
#      for playback. State data is passed through unmodified.
#
# Quick start:
#   # 1. Create venv and install deps:
#   #      python3 -m venv .venv && source .venv/bin/activate
#   #      pip install "warp-lang" "newton[examples]" "ast2json"
#   # 2. Build Ramanujan with -DENABLE_GPU=ON (see README.md)
#   # 3. Run:
#   #      python3 run_mpm_anymal.py --frames 200
#   #      python3 run_mpm_anymal.py --frames 200 --num-particles 1000000
# =============================================================================

import argparse
import datetime
import math
import os
import shutil
import subprocess
import sys
import tempfile

KERNEL_NAME = "mpm_anymal_kernel.py"

# Physics constants (independent of particle count)
FOOT_HALF_EXTENT = 0.06      # half-size of the visual foot box
FOOT_RADIUS_PARAM = 0.10     # collision radius the kernel uses
FOOT_STRENGTH = 6.0          # impulse magnitude per intersecting foot
GAIT_SPEED = 0.40            # m/s forward
STEP_HEIGHT = 0.08
GRAVITY_Z = -9.81
DT = 1.0 / 60.0

# Robot body visual dimensions
TORSO_HX = 0.14              # torso half-extent x (width)
TORSO_HY = 0.22              # torso half-extent y (length)
TORSO_HZ = 0.07              # torso half-extent z (height)
TORSO_Z  = 0.28              # torso centre height above ground

# Ramanujan environment (must use correct Java version)
JAVA_HOME = os.environ.get("JAVA_HOME",
    "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home")
RJ_JAR = os.environ.get("RAMANUJAN_FAT_JAR", None)
RJ_WS = os.environ.get("RAMANUJAN_WS", "/tmp")


def log(msg=""):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


def compute_grid(num_particles):
    """Return (grid_nx, grid_ny, grid_nz, spacing, origin_x, origin_y, origin_z,
    actual_num_particles, particle_radius) for a flat square bed of ~num_particles."""
    grid_nz = 4
    side = max(1, int(math.sqrt(num_particles / grid_nz)))
    grid_nx = side
    grid_ny = side
    actual = grid_nx * grid_ny * grid_nz
    # Scale spacing so the bed is always ~1 m × 1 m regardless of particle count
    spacing = 1.0 / side
    origin_x = -0.5
    origin_y = -0.5
    origin_z = 0.005
    particle_radius = spacing * 0.35
    return grid_nx, grid_ny, grid_nz, spacing, origin_x, origin_y, origin_z, actual, particle_radius


# ─────────────────────────────────────────────────────────────────────────────
#  CSV pass-through helpers (no calculations, just I/O)
# ─────────────────────────────────────────────────────────────────────────────

def write_csv_1d(path, values):
    with open(path, "w") as f:
        f.write(",".join(repr(float(v)) for v in values))
        f.write("\n")


def read_csv_1d(path):
    with open(path, "r") as f:
        text = f.read().strip()
    if not text:
        return []
    vals = [float(tok) for tok in text.split(",") if tok]
    # Ramanujan CSV writer drops trailing zeros; pad feet array to 12 if needed
    while len(vals) < 12 and "feet" in os.path.basename(path):
        vals.append(0.0)
    return vals


# ─────────────────────────────────────────────────────────────────────────────
#  Ramanujan invocation
# ─────────────────────────────────────────────────────────────────────────────

def resolve_rj_command():
    java_bin = os.path.join(JAVA_HOME, "bin", "java")

    if RJ_JAR and os.path.isfile(RJ_JAR):
        return [java_bin, "-Xmx4g", "-jar", RJ_JAR]

    here = os.path.dirname(os.path.abspath(__file__))
    candidate = os.path.normpath(os.path.join(
        here, "..", "..", "developer-console", "target",
        "developer-console-1.0-SNAPSHOT-fat.jar"))
    if os.path.isfile(candidate):
        return [java_bin, "-Xmx4g", "-jar", candidate]

    return None


def run_kernel_one_frame(rj_cmd, kernel_path, work_dir, frame_num,
                         positions, velocities, grid_params):
    """Run mpm_anymal_kernel.py for one frame.
    grid_params: (grid_nx, grid_ny, grid_nz, spacing, ox, oy, oz, num_particles)
    Returns (new_positions, new_velocities, foot_positions).
    """
    grid_nx, grid_ny, grid_nz, spacing, ox, oy, oz, num_particles = grid_params

    pos_csv = os.path.join(work_dir, "positions.csv")
    vel_csv = os.path.join(work_dir, "velocities.csv")
    par_csv = os.path.join(work_dir, "params.csv")
    out_pos  = os.path.join(work_dir, f"out_positions_{frame_num}.csv")
    out_vel  = os.path.join(work_dir, f"out_velocities_{frame_num}.csv")
    out_feet = os.path.join(work_dir, f"out_feet_{frame_num}.csv")

    write_csv_1d(pos_csv, positions)
    write_csv_1d(vel_csv, velocities)
    write_csv_1d(par_csv, [
        DT,
        GRAVITY_Z,
        FOOT_RADIUS_PARAM,
        FOOT_STRENGTH,
        GAIT_SPEED,
        STEP_HEIGHT,
        float(frame_num),
        float(num_particles),
        float(grid_nx),
        float(grid_ny),
        float(grid_nz),
        float(spacing),
        float(ox),
        float(oy),
        float(oz),
    ])

    dump_script = (
        f"dump positions {out_pos}\n"
        f"dump velocities {out_vel}\n"
        f"dump feet {out_feet}\n"
        f"exit\n"
    )

    cmd = list(rj_cmd) + [
        "execute_inline", kernel_path, pos_csv, vel_csv, par_csv,
    ]

    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME
    env["RAMANUJAN_WS"] = RJ_WS

    try:
        subprocess.run(
            cmd,
            input=dump_script,
            text=True,
            check=True,
            capture_output=True,
            env=env,
        )
    except subprocess.CalledProcessError as exc:
        sys.stderr.write(
            f"Ramanujan invocation failed at frame {frame_num}\n"
            f"  cmd: {' '.join(cmd)}\n"
            f"  stdout: {exc.stdout}\n"
            f"  stderr: {exc.stderr}\n")
        raise

    return (
        read_csv_1d(out_pos),
        read_csv_1d(out_vel),
        read_csv_1d(out_feet),
    )


# ─────────────────────────────────────────────────────────────────────────────
#  Newton viewer (no physics — draws what Ramanujan produced)
# ─────────────────────────────────────────────────────────────────────────────

def build_model_and_viewer(viewer_kind, output_path, initial_positions,
                           initial_feet, num_particles, particle_radius):
    import warp as wp
    import newton

    builder = newton.ModelBuilder(up_axis=newton.Axis.Z)
    builder.add_ground_plane()

    # Particle bed — positions from Ramanujan frame-0 output
    for i in range(num_particles):
        px = initial_positions[i * 3 + 0]
        py = initial_positions[i * 3 + 1]
        pz = initial_positions[i * 3 + 2]
        builder.add_particle(
            pos=wp.vec3(px, py, pz),
            vel=wp.vec3(0.0, 0.0, 0.0),
            mass=0.05,
            radius=particle_radius,
        )

    # 4 foot boxes
    foot_body_ids = []
    for i in range(4):
        fx = initial_feet[i * 3 + 0] if i * 3     < len(initial_feet) else 0.0
        fy = initial_feet[i * 3 + 1] if i * 3 + 1 < len(initial_feet) else 0.0
        fz = initial_feet[i * 3 + 2] if i * 3 + 2 < len(initial_feet) else 0.0
        body = builder.add_body(
            xform=wp.transform(wp.vec3(fx, fy, fz), wp.quat_identity()),
            mass=0.0,
        )
        builder.add_shape_box(body=body,
                              hx=FOOT_HALF_EXTENT,
                              hy=FOOT_HALF_EXTENT,
                              hz=FOOT_HALF_EXTENT)
        foot_body_ids.append(body)

    # Torso box — body centre derived from feet positions each frame
    body_y0 = initial_feet[1] - 0.30 if len(initial_feet) >= 2 else 0.0
    torso_body = builder.add_body(
        xform=wp.transform(wp.vec3(0.0, body_y0, TORSO_Z), wp.quat_identity()),
        mass=0.0,
    )
    builder.add_shape_box(body=torso_body, hx=TORSO_HX, hy=TORSO_HY, hz=TORSO_HZ)

    model = builder.finalize()

    if viewer_kind == "gl":
        viewer = newton.viewer.ViewerGL()
    elif viewer_kind == "usd":
        if not output_path:
            raise SystemExit("--viewer usd requires --output-path")
        viewer = newton.viewer.ViewerUSD(output_path=output_path)
    elif viewer_kind == "null":
        viewer = newton.viewer.ViewerNull()
    else:
        raise SystemExit(f"unknown viewer kind: {viewer_kind}")

    viewer.set_model(model)
    if hasattr(viewer, "show_particles"):
        viewer.show_particles = True

    return model, viewer


def playback(model, viewer, frames, num_particles):
    import numpy as np
    import warp as wp

    state = model.state()
    pos_dtype  = state.particle_q.dtype if state.particle_q is not None else wp.vec3
    body_dtype = state.body_q.dtype     if state.body_q    is not None else wp.transform

    log("Pre-building frame arrays for smooth playback...")
    num_frames = len(frames)
    pos_arrays        = []
    body_xform_arrays = []

    for positions, _vels, feet in frames:
        pos_array = np.asarray(positions, dtype=np.float32).reshape(-1, 3)
        pos_arrays.append(wp.array(pos_array, dtype=pos_dtype))

        # 4 feet + 1 torso = 5 body transforms
        body_xforms = []
        for i in range(4):
            fx = feet[i * 3 + 0] if i * 3     < len(feet) else 0.0
            fy = feet[i * 3 + 1] if i * 3 + 1 < len(feet) else 0.0
            fz = feet[i * 3 + 2] if i * 3 + 2 < len(feet) else 0.0
            body_xforms.append(wp.transform(wp.vec3(fx, fy, fz), wp.quat_identity()))

        # Torso: centre-y derived from front-left foot y (feet[1] = body_y + 0.30)
        torso_y = feet[1] - 0.30 if len(feet) >= 2 else 0.0
        body_xforms.append(wp.transform(wp.vec3(0.0, torso_y, TORSO_Z), wp.quat_identity()))

        body_xform_arrays.append(wp.array(body_xforms, dtype=body_dtype))

    log("Playback started — close the viewer window to exit.")
    f_idx = 0
    while viewer.is_running():
        wp.copy(state.particle_q, pos_arrays[f_idx])
        wp.copy(state.body_q, body_xform_arrays[f_idx])

        viewer.begin_frame(f_idx * DT)
        viewer.log_state(state)
        viewer.end_frame()

        f_idx = (f_idx + 1) % num_frames

    if hasattr(viewer, "close"):
        viewer.close()


# ─────────────────────────────────────────────────────────────────────────────
#  Main
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--frames", type=int, default=200,
                        help="Number of physics frames to simulate.")
    parser.add_argument("--num-particles", type=int, default=256,
                        help="Target particle count (rounded to grid_nx² × 4).")
    parser.add_argument("--viewer", choices=["gl", "usd", "null"], default="gl")
    parser.add_argument("--output-path", default=None,
                        help="Required when --viewer usd")
    parser.add_argument("--keep-tmp", action="store_true",
                        help="Don't delete the per-frame CSVs.")
    args = parser.parse_args()

    grid_nx, grid_ny, grid_nz, spacing, ox, oy, oz, num_particles, particle_radius = \
        compute_grid(args.num_particles)

    log(f"JAVA_HOME:      {JAVA_HOME}")
    log(f"RAMANUJAN_WS:   {RJ_WS}")
    log(f"Particles:      {num_particles}  ({grid_nx}×{grid_ny}×{grid_nz}, spacing={spacing:.4f} m)")
    log(f"Particle radius:{particle_radius:.4f} m  (viewer only)")

    rj_cmd = resolve_rj_command()
    if rj_cmd is None:
        sys.stderr.write(
            "Could not locate the Ramanujan developer-console JAR.\n"
            "Set RAMANUJAN_FAT_JAR=/abs/path/to/"
            "developer-console-1.0-SNAPSHOT-fat.jar and re-run.\n")
        sys.exit(1)

    here       = os.path.dirname(os.path.abspath(__file__))
    kernel_path = os.path.join(here, KERNEL_NAME)
    work_dir   = tempfile.mkdtemp(prefix="mpm_anymal_rj_")
    log(f"work dir: {work_dir}")

    grid_params = (grid_nx, grid_ny, grid_nz, spacing, ox, oy, oz, num_particles)

    positions  = [0.0] * (num_particles * 3)
    velocities = [0.0] * (num_particles * 3)

    frames = []
    for frame_num in range(args.frames):
        positions, velocities, feet = run_kernel_one_frame(
            rj_cmd, kernel_path, work_dir, frame_num,
            positions, velocities, grid_params)
        frames.append((positions, velocities, feet))
        if frame_num % 10 == 0:
            log(f"  frame {frame_num}/{args.frames}")

    log(f"Ramanujan finished {len(frames)} frames; opening Newton viewer.")

    model, viewer = build_model_and_viewer(
        args.viewer, args.output_path,
        frames[0][0], frames[0][2],
        num_particles, particle_radius)

    try:
        playback(model, viewer, frames, num_particles)
    finally:
        if not args.keep_tmp:
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()

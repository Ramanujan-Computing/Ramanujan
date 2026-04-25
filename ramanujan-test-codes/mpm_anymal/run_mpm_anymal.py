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
#   # 1. Install Newton (for the viewer): pip install "newton[examples]"
#   # 2. Build & install Ramanujan with -DENABLE_GPU=ON, then either set the
#   #    `rj` alias (see ramanujan/install_ramanujan.sh) or set
#   #    RAMANUJAN_FAT_JAR=/path/to/developer-console-1.0-SNAPSHOT-fat.jar
#   # 3. Run:
#   #      python3 run_mpm_anymal.py --frames 200
#   #      python3 run_mpm_anymal.py --frames 200 --viewer usd \
#   #              --output-path mpm_anymal.usd
# =============================================================================

import argparse
import datetime
import os
import shutil
import subprocess
import sys
import tempfile

KERNEL_NAME = "mpm_anymal_kernel.py"
NUM_PARTICLES = 256          # must match the kernel constant
PARTICLE_RADIUS = 0.018      # for viewer rendering only
FOOT_HALF_EXTENT = 0.06      # half-size of the visual foot box
FOOT_RADIUS_PARAM = 0.10     # collision radius the kernel uses
FOOT_STRENGTH = 6.0          # impulse magnitude per intersecting foot
GAIT_SPEED = 0.40            # m/s forward
STEP_HEIGHT = 0.08
GRAVITY_Z = -9.81
DT = 1.0 / 60.0


def log(msg=""):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


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
    return [float(tok) for tok in text.split(",") if tok]


# ─────────────────────────────────────────────────────────────────────────────
#  Ramanujan invocation
# ─────────────────────────────────────────────────────────────────────────────

def resolve_rj_command():
    """Return the command prefix used to launch the Ramanujan dev console.

    Honors RAMANUJAN_FAT_JAR if set, otherwise looks for the `rj` alias
    installed by ramanujan/install_ramanujan.sh, otherwise falls back to a
    `java -jar` invocation rooted at the local repo build output.
    """
    fat_jar = os.environ.get("RAMANUJAN_FAT_JAR")
    if fat_jar and os.path.isfile(fat_jar):
        return ["java", "-Xmx2g", "-jar", fat_jar]

    rj = shutil.which("rj")
    if rj:
        # rj wraps `java -jar developer-console.jar execute ...`. We need the
        # `execute_inline` mode for CSV+dump workflows, so we still prefer the
        # JAR path. Try to discover it from the alias contents.
        return None  # signal failure; user must export RAMANUJAN_FAT_JAR

    # Heuristic fallback: typical built location relative to this script
    here = os.path.dirname(os.path.abspath(__file__))
    candidate = os.path.normpath(os.path.join(
        here, "..", "..", "developer-console", "target",
        "developer-console-1.0-SNAPSHOT-fat.jar"))
    if os.path.isfile(candidate):
        return ["java", "-Xmx2g", "-jar", candidate]
    return None


def run_kernel_one_frame(rj_cmd, kernel_path, work_dir, frame_num,
                         positions, velocities):
    """Run mpm_anymal_kernel.py for one frame. Returns
    (new_positions, new_velocities, foot_positions)."""

    pos_csv = os.path.join(work_dir, "positions.csv")
    vel_csv = os.path.join(work_dir, "velocities.csv")
    par_csv = os.path.join(work_dir, "params.csv")
    out_pos = os.path.join(work_dir, f"out_positions_{frame_num}.csv")
    out_vel = os.path.join(work_dir, f"out_velocities_{frame_num}.csv")
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
    try:
        subprocess.run(
            cmd,
            input=dump_script,
            text=True,
            check=True,
            capture_output=True,
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
#  Newton viewer playback (no physics, just draws what Ramanujan produced)
# ─────────────────────────────────────────────────────────────────────────────

def build_model_and_viewer(viewer_kind, output_path, initial_positions,
                           initial_feet):
    import warp as wp
    import newton

    builder = newton.ModelBuilder(up_axis=newton.Axis.Z)
    builder.add_ground_plane()

    # Add the particle bed using Ramanujan's frame-0 layout. We hand each
    # position straight to add_particle so the orchestrator never positions
    # particles itself.
    for i in range(NUM_PARTICLES):
        px = initial_positions[i * 3 + 0]
        py = initial_positions[i * 3 + 1]
        pz = initial_positions[i * 3 + 2]
        builder.add_particle(
            pos=wp.vec3(px, py, pz),
            vel=wp.vec3(0.0, 0.0, 0.0),
            mass=0.05,
            radius=PARTICLE_RADIUS,
        )

    # Add a free-floating body per foot. We don't run any solver against
    # these — they're moved each frame from the kernel-produced foot
    # positions purely so the viewer can draw boxes where Ramanujan said
    # the feet were.
    foot_body_ids = []
    for i in range(4):
        fx = initial_feet[i * 3 + 0]
        fy = initial_feet[i * 3 + 1]
        fz = initial_feet[i * 3 + 2]
        body = builder.add_body(
            xform=wp.transform(wp.vec3(fx, fy, fz), wp.quat_identity()),
            mass=0.0,  # kinematic
        )
        builder.add_shape_box(
            body=body,
            hx=FOOT_HALF_EXTENT,
            hy=FOOT_HALF_EXTENT,
            hz=FOOT_HALF_EXTENT,
        )
        foot_body_ids.append(body)

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

    return model, viewer, foot_body_ids


def playback(model, viewer, foot_body_ids, frames):
    import numpy as np
    import warp as wp

    state = model.state()

    pos_dtype = state.particle_q.dtype if state.particle_q is not None else wp.vec3
    body_dtype = state.body_q.dtype if state.body_q is not None else wp.transform

    for f_idx, frame in enumerate(frames):
        positions, _vels, feet = frame

        # Particle positions: pure pass-through (Ramanujan computed these)
        pos_array = np.asarray(positions, dtype=np.float32).reshape(-1, 3)
        wp.copy(state.particle_q,
                wp.array(pos_array, dtype=pos_dtype, device=state.particle_q.device))

        # Foot transforms: identity rotation, translation from kernel output
        body_xforms = []
        for i in range(4):
            fx = feet[i * 3 + 0]
            fy = feet[i * 3 + 1]
            fz = feet[i * 3 + 2]
            body_xforms.append(
                wp.transform(wp.vec3(fx, fy, fz), wp.quat_identity()))
        wp.copy(state.body_q,
                wp.array(body_xforms, dtype=body_dtype, device=state.body_q.device))

        viewer.begin_frame(f_idx * DT)
        viewer.log_state(state)
        viewer.end_frame()

        if not viewer.is_running():
            break

    if hasattr(viewer, "close"):
        viewer.close()


# ─────────────────────────────────────────────────────────────────────────────
#  Main
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--frames", type=int, default=200,
                        help="Number of physics frames to simulate.")
    parser.add_argument("--viewer", choices=["gl", "usd", "null"],
                        default="gl")
    parser.add_argument("--output-path", default=None,
                        help="Required when --viewer usd")
    parser.add_argument("--keep-tmp", action="store_true",
                        help="Don't delete the per-frame CSVs.")
    args = parser.parse_args()

    rj_cmd = resolve_rj_command()
    if rj_cmd is None:
        sys.stderr.write(
            "Could not locate the Ramanujan developer-console JAR.\n"
            "Set RAMANUJAN_FAT_JAR=/abs/path/to/"
            "developer-console-1.0-SNAPSHOT-fat.jar and re-run.\n")
        sys.exit(1)

    here = os.path.dirname(os.path.abspath(__file__))
    kernel_path = os.path.join(here, KERNEL_NAME)
    work_dir = tempfile.mkdtemp(prefix="mpm_anymal_rj_")
    log(f"work dir: {work_dir}")

    # Frame 0: send empty buffers; the kernel populates the grid.
    positions = [0.0] * (NUM_PARTICLES * 3)
    velocities = [0.0] * (NUM_PARTICLES * 3)

    frames = []
    for frame_num in range(args.frames):
        positions, velocities, feet = run_kernel_one_frame(
            rj_cmd, kernel_path, work_dir, frame_num, positions, velocities)
        frames.append((positions, velocities, feet))
        if frame_num % 10 == 0:
            log(f"  frame {frame_num}/{args.frames}")

    log(f"Ramanujan finished {len(frames)} frames; opening Newton viewer.")

    model, viewer, foot_body_ids = build_model_and_viewer(
        args.viewer, args.output_path, frames[0][0], frames[0][2])

    try:
        playback(model, viewer, foot_body_ids, frames)
    finally:
        if not args.keep_tmp:
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()

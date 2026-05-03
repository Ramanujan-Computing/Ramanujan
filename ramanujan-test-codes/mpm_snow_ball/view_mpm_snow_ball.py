#!/usr/bin/env python3
# =============================================================================
# View saved MPM Snow Ball trajectory (frames stored by run_mpm_snow_ball.py)
# =============================================================================
#
# Usage:
#   python3 view_mpm_snow_ball.py frames_00123.pkl [--viewer gl|usd|null] [--output-path out.usda]
#
# The .pkl file is created by run_mpm_snow_ball.py --save-frames.

import argparse
import os
import pickle
import sys
import time

# Physics constants (must match run_mpm_snow_ball.py)
WALL_Y        = 0.0
WALL_HX       = 1.0
WALL_HY       = 0.025
WALL_HZ       = 1.5
WALL_VISUAL_Z = 1.0
DT            = 1.0 / 60.0


def main():
    parser = argparse.ArgumentParser(description="View saved MPM Snow Ball frames in Newton.")
    parser.add_argument("frames_file",
                        help="Pickle file created by run_mpm_snow_ball.py (e.g. frames_00123.pkl)")
    parser.add_argument("--viewer", choices=["gl", "usd", "null"], default="gl")
    parser.add_argument("--output-path", default=None,
                        help="Required when --viewer usd")
    args = parser.parse_args()

    if not os.path.isfile(args.frames_file):
        print(f"ERROR: file not found: {args.frames_file}", file=sys.stderr)
        sys.exit(1)

    try:
        with open(args.frames_file, "rb") as f:
            data = pickle.load(f)
    except Exception as e:
        print(f"ERROR: failed to load {args.frames_file}: {e}", file=sys.stderr)
        sys.exit(1)

    frames         = data.get("frames", [])
    num_particles  = data.get("num_particles", 0)
    particle_radius = data.get("particle_radius", 0.01)
    frame_times    = data.get("frame_times", [])

    if not frames:
        print("ERROR: no frames in file", file=sys.stderr)
        sys.exit(1)

    print(f"Loaded {len(frames)} frames ({num_particles} particles) from {args.frames_file}")
    if frame_times:
        avg = sum(frame_times) / len(frame_times)
        print(f"  Average frame time: {avg:.2f}s")

    import warp as wp
    import newton

    builder = newton.ModelBuilder(up_axis=newton.Axis.Z)
    builder.add_ground_plane()

    initial_positions = frames[0][0]
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

    wall_body = builder.add_body(
        xform=wp.transform(wp.vec3(0.0, WALL_Y, WALL_VISUAL_Z), wp.quat_identity()),
        mass=0.0,
    )
    builder.add_shape_box(body=wall_body, hx=WALL_HX, hy=WALL_HY, hz=WALL_HZ)

    model = builder.finalize()

    if args.viewer == "gl":
        viewer = newton.viewer.ViewerGL()
    elif args.viewer == "usd":
        if not args.output_path:
            raise SystemExit("--viewer usd requires --output-path")
        viewer = newton.viewer.ViewerUSD(output_path=args.output_path)
    elif args.viewer == "null":
        viewer = newton.viewer.ViewerNull()

    viewer.set_model(model)
    if hasattr(viewer, "show_particles"):
        viewer.show_particles = True

    import numpy as np

    state = model.state()
    pos_dtype  = state.particle_q.dtype if state.particle_q is not None else wp.vec3
    body_dtype = state.body_q.dtype     if state.body_q    is not None else wp.transform

    wall_xform = wp.transform(wp.vec3(0.0, WALL_Y, WALL_VISUAL_Z), wp.quat_identity())
    wall_array = wp.array([wall_xform], dtype=body_dtype)

    print("Pre-building frame arrays for smooth playback...")
    pos_arrays = []
    for positions, _vels in frames:
        arr = np.asarray(positions, dtype=np.float32).reshape(-1, 3)
        pos_arrays.append(wp.array(arr, dtype=pos_dtype))

    print("Playback started — close the viewer window to exit.")
    target_fps = 60
    frame_duration = 1.0 / target_fps
    f_idx = 0
    while viewer.is_running():
        wp.copy(state.particle_q, pos_arrays[f_idx])
        wp.copy(state.body_q, wall_array)

        viewer.begin_frame(f_idx * DT)
        viewer.log_state(state)
        viewer.end_frame()

        time.sleep(frame_duration)
        f_idx = (f_idx + 1) % len(frames)

    if hasattr(viewer, "close"):
        viewer.close()


if __name__ == "__main__":
    main()

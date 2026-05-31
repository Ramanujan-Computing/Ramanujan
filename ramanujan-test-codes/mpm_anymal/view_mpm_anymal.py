#!/usr/bin/env python3
# =============================================================================
# View saved MPM Anymal trajectory (frames stored by run_mpm_anymal.py)
# =============================================================================
#
# Usage:
#   python3 view_mpm_anymal.py frames_00123.pkl [--viewer gl|usd|null] [--output-path out.usda]
#
# The .pkl file is created automatically by run_mpm_anymal.py with --save-frames.
# This script extracts frames, grid metadata, and opens Newton viewer for playback.

import argparse
import pickle
import sys
import os
import time

def main():
    parser = argparse.ArgumentParser(description="View saved MPM Anymal frames in Newton.")
    parser.add_argument("frames_file", help="Pickle file created by run_mpm_anymal.py (e.g. frames_00123.pkl)")
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

    frames = data.get("frames", [])
    num_particles = data.get("num_particles", 0)
    particle_radius = data.get("particle_radius", 0.01)
    frame_times = data.get("frame_times", [])
    
    if not frames:
        print("ERROR: no frames in file", file=sys.stderr)
        sys.exit(1)

    print(f"Loaded {len(frames)} frames ({num_particles} particles) from {args.frames_file}")
    if frame_times:
        avg_time = sum(frame_times) / len(frame_times)
        print(f"  Average frame time: {avg_time:.2f}s")

    # Physics constants (must match run_mpm_anymal.py)
    FOOT_HALF_EXTENT = 0.06
    FOOT_STRENGTH = 6.0
    STEP_HEIGHT = 0.08
    TORSO_HX = 0.14
    TORSO_HY = 0.22
    TORSO_HZ = 0.07
    TORSO_Z = 0.28
    DT = 1.0 / 60.0

    # Build and view (same logic as run_mpm_anymal.py)
    import warp as wp
    import newton

    builder = newton.ModelBuilder(up_axis=newton.Axis.Z)
    builder.add_ground_plane()

    # Particle bed
    for i in range(num_particles):
        px = frames[0][0][i * 3 + 0]
        py = frames[0][0][i * 3 + 1]
        pz = frames[0][0][i * 3 + 2]
        builder.add_particle(
            pos=wp.vec3(px, py, pz),
            vel=wp.vec3(0.0, 0.0, 0.0),
            mass=0.05,
            radius=particle_radius,
        )

    # 4 feet + 1 torso
    foot_body_ids = []
    for i in range(4):
        feet = frames[0][2]
        fx = feet[i * 3 + 0] if i * 3     < len(feet) else 0.0
        fy = feet[i * 3 + 1] if i * 3 + 1 < len(feet) else 0.0
        fz = feet[i * 3 + 2] if i * 3 + 2 < len(feet) else 0.0
        body = builder.add_body(
            xform=wp.transform(wp.vec3(fx, fy, fz), wp.quat_identity()),
            mass=0.0,
        )
        builder.add_shape_box(body=body,
                              hx=FOOT_HALF_EXTENT,
                              hy=FOOT_HALF_EXTENT,
                              hz=FOOT_HALF_EXTENT)
        foot_body_ids.append(body)

    feet = frames[0][2]
    body_y0 = feet[1] - 0.30 if len(feet) >= 2 else 0.0
    torso_body = builder.add_body(
        xform=wp.transform(wp.vec3(0.0, body_y0, TORSO_Z), wp.quat_identity()),
        mass=0.0,
    )
    builder.add_shape_box(body=torso_body, hx=TORSO_HX, hy=TORSO_HY, hz=TORSO_HZ)

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

    # Playback
    import numpy as np

    state = model.state()
    pos_dtype  = state.particle_q.dtype if state.particle_q is not None else wp.vec3
    body_dtype = state.body_q.dtype     if state.body_q    is not None else wp.transform

    print("Pre-building frame arrays for smooth playback...")
    pos_arrays        = []
    body_xform_arrays = []

    for positions, _vels, feet in frames:
        pos_array = np.asarray(positions, dtype=np.float32).reshape(-1, 3)
        pos_arrays.append(wp.array(pos_array, dtype=pos_dtype))

        body_xforms = []
        for i in range(4):
            fx = feet[i * 3 + 0] if i * 3     < len(feet) else 0.0
            fy = feet[i * 3 + 1] if i * 3 + 1 < len(feet) else 0.0
            fz = feet[i * 3 + 2] if i * 3 + 2 < len(feet) else 0.0
            body_xforms.append(wp.transform(wp.vec3(fx, fy, fz), wp.quat_identity()))

        torso_y = feet[1] - 0.30 if len(feet) >= 2 else 0.0
        body_xforms.append(wp.transform(wp.vec3(0.0, torso_y, TORSO_Z), wp.quat_identity()))

        body_xform_arrays.append(wp.array(body_xforms, dtype=body_dtype))

    print("Playback started — close the viewer window to exit.")
    f_idx = 0
    target_fps = 60  # Half of original 60 FPS
    frame_time = 1.0 / target_fps
    while viewer.is_running():
        wp.copy(state.particle_q, pos_arrays[f_idx])
        wp.copy(state.body_q, body_xform_arrays[f_idx])

        viewer.begin_frame(f_idx * DT)
        viewer.log_state(state)
        viewer.end_frame()

        time.sleep(frame_time)
        f_idx = (f_idx + 1) % len(frames)

    if hasattr(viewer, "close"):
        viewer.close()


if __name__ == "__main__":
    main()

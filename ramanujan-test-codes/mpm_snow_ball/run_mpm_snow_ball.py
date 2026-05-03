#!/usr/bin/env python3
# =============================================================================
# MPM Snow Ball on Ramanujan: orchestrator + Newton viewer playback
# =============================================================================
#
# Inspired by Newton's `mpm_snow_ball` example. A spherical ball of snow
# particles is thrown at a flat vertical wall. All per-frame physics runs on
# Ramanujan's OpenCL GPU runtime; Newton's viewer is used purely to render the
# Ramanujan-produced trajectory.
#
# This file does NO physics. It only:
#   1. Writes input CSVs (positions/velocities/params) for each frame.
#   2. Invokes the Ramanujan developer-console (`rj`) on
#      `mpm_snow_ball_kernel.py`, which runs the GPU kernels.
#   3. Reads back per-frame state from the `dump` console command.
#   4. Feeds the recorded states into Newton's ViewerGL/ViewerUSD/ViewerNull
#      for playback. State data is passed through unmodified.
#
# Quick start:
#   # 1. Create venv and install deps:
#   #      python3 -m venv .venv && source .venv/bin/activate
#   #      pip install "warp-lang" "newton[examples]" "ast2json"
#   # 2. Build Ramanujan with -DGPU_ENABLED=ON (see README.md)
#   # 3. Run:
#   #      python3 run_mpm_snow_ball.py --frames 200
#   #      python3 run_mpm_snow_ball.py --frames 200 --num-particles 5000
# =============================================================================

import argparse
import datetime
import math
import os
import pickle
import shutil
import subprocess
import sys
import tempfile
import time
import threading

KERNEL_NAME = "mpm_snow_ball_kernel.py"

# Physics constants
THROW_SPEED_Y      = 3.0         # m/s initial velocity toward wall (+y)
WALL_Y             = 0.0         # wall position along y axis
WALL_FRICTION      = 0.35        # lateral velocity retained at wall (MPM BC)
GROUND_FRICTION    = 0.70        # lateral velocity retained at floor (MPM BC)
GRAVITY_Z          = -9.81
DT                 = 1.0 / 60.0
SNOW_DENSITY       = 300.0       # kg/m³  (light compacted snow)

# Ball spawn parameters
BALL_START_X = 0.0
BALL_START_Y = -0.8   # 0.8 m behind wall — reaches wall in ~0.27 s
BALL_START_Z = 1.2    # 1.2 m off ground — drops ~0.35 m during approach

# Wall visual dimensions (viewer only — physics uses WALL_Y plane)
WALL_HX        = 1.0    # half-extent x
WALL_HY        = 0.025  # half-extent y (thin slab)
WALL_HZ        = 1.5    # half-extent z
WALL_VISUAL_Z  = 1.0    # centre height of the wall box in the viewer

# Ramanujan environment
JAVA_HOME = os.environ.get("JAVA_HOME",
    "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home")
RJ_JAR = os.environ.get("RAMANUJAN_FAT_JAR", None)
RJ_WS  = os.environ.get("RAMANUJAN_WS", "/tmp")


def log(msg=""):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


# ─────────────────────────────────────────────────────────────────────────────
#  Persistent JVM server (avoids JVM startup per frame)
# ─────────────────────────────────────────────────────────────────────────────

class RjServer:
    def __init__(self, java_home, rj_ws):
        self.java_home = java_home
        self.rj_ws = rj_ws
        self.proc = None
        self._stderr_thread = None

    def start(self, timeout=60):
        java_bin = os.path.join(self.java_home, "bin", "java")
        rj_jar = os.environ.get("RAMANUJAN_FAT_JAR",
            "/Users/pranav/Desktop/ws/developer-console-1.0-SNAPSHOT-fat.jar")

        cmd = [java_bin, "-Xmx4g", "-XX:+UseG1GC", "-jar", rj_jar, "server"]
        log("Starting persistent JVM server (local mode)...")
        env = os.environ.copy()
        env["JAVA_HOME"] = self.java_home
        env["RAMANUJAN_WS"] = self.rj_ws

        self.proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
            env=env,
        )
        self._stderr_thread = threading.Thread(target=self._drain_stderr, daemon=True)
        self._stderr_thread.start()

        deadline = time.time() + timeout
        while time.time() < deadline:
            line = self.proc.stdout.readline()
            if not line:
                log("ERROR: JVM exited before SERVER_READY")
                sys.exit(1)
            if line.rstrip() == "SERVER_READY":
                log("JVM local server ready")
                return
        log("ERROR: timeout waiting for SERVER_READY")
        sys.exit(1)

    def _drain_stderr(self):
        for line in self.proc.stderr:
            sys.stderr.write("[JVM] " + line)

    def run_kernel(self, kernel_py, csv_args, dump_vars, timeout=300):
        args_str = " ".join([kernel_py] + csv_args)
        self.proc.stdin.write(f"run {args_str}\n")
        self.proc.stdin.flush()

        deadline = time.time() + timeout
        while time.time() < deadline:
            line = self.proc.stdout.readline()
            if not line:
                log(f"ERROR: JVM stdout closed during {os.path.basename(kernel_py)}")
                sys.exit(1)
            line = line.rstrip()
            if line == "KERNEL_DONE":
                break
            if line.startswith("KERNEL_ERROR"):
                log(f"ERROR: {line}")
                sys.exit(1)
        else:
            log(f"TIMEOUT: no KERNEL_DONE after {timeout}s")
            sys.exit(1)

        for name, path in dump_vars.items():
            self.proc.stdin.write(f"dump {name} {path}\n")
            self.proc.stdin.flush()
            ddl = time.time() + 30
            while time.time() < ddl:
                dline = self.proc.stdout.readline()
                if not dline:
                    log(f"ERROR: JVM closed during dump {name}")
                    sys.exit(1)
                if dline.rstrip().startswith("Dumped"):
                    break

    def shutdown(self):
        if self.proc:
            self.proc.stdin.write("quit\n")
            self.proc.stdin.flush()
            self.proc.wait(timeout=10)
            log("JVM server shutdown")


class RjHomelabClient:
    def __init__(self, homelab_url):
        self.homelab_url = homelab_url.rstrip("/")

    def start(self, timeout=10):
        import urllib.request
        try:
            urllib.request.urlopen(f"{self.homelab_url}/pings/heartbeat", timeout=timeout)
            log(f"Connected to homelab server at {self.homelab_url}")
        except Exception as e:
            log(f"ERROR: Cannot reach homelab server at {self.homelab_url}: {e}")
            sys.exit(1)

    def run_kernel(self, kernel_py, csv_args, dump_vars, timeout=300):
        import json
        import urllib.request

        run_body = json.dumps({"args": [kernel_py] + csv_args}).encode()
        run_req = urllib.request.Request(
            f"{self.homelab_url}/orchestrator/run",
            data=run_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            urllib.request.urlopen(run_req, timeout=timeout)
        except Exception as e:
            raise RuntimeError(f"Homelab /orchestrator/run failed: {e}")

        for name, path in dump_vars.items():
            dump_body = json.dumps({"name": name, "path": path}).encode()
            dump_req = urllib.request.Request(
                f"{self.homelab_url}/orchestrator/dump",
                data=dump_body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            try:
                urllib.request.urlopen(dump_req, timeout=30)
            except Exception as e:
                raise RuntimeError(f"Homelab /orchestrator/dump {name} failed: {e}")

    def shutdown(self):
        pass


# ─────────────────────────────────────────────────────────────────────────────
#  Sphere particle grid
# ─────────────────────────────────────────────────────────────────────────────

def _count_sphere(side):
    """Count lattice points inside the inscribed sphere of a side^3 cube."""
    half = side / 2.0
    R_sq = (half - 0.5) ** 2
    count = 0
    for iz in range(side):
        for iy in range(side):
            for ix in range(side):
                dx = ix - half + 0.5
                dy = iy - half + 0.5
                dz = iz - half + 0.5
                if dx * dx + dy * dy + dz * dz < R_sq:
                    count += 1
    return count


def compute_grid(num_particles):
    """Return grid params for a spherical ball of ~num_particles.

    Returns (grid_nx, grid_ny, grid_nz, spacing, cx, cy, cz, actual, particle_radius).
    Physical ball diameter is fixed at ~0.30 m; resolution scales with particle count.
    Picks the odd cube side whose inscribed sphere count is closest to num_particles.
    """
    side = max(3, int((6.0 * num_particles / math.pi) ** (1.0 / 3.0)))
    if side % 2 == 0:
        side += 1
    # Advance to first side where sphere count >= num_particles
    while _count_sphere(side) < num_particles:
        side += 2
    # Compare with one step lower — keep whichever is closer to the target
    if side > 3:
        side_lo, count_lo = side - 2, _count_sphere(side - 2)
        count_hi = _count_sphere(side)
        if abs(count_lo - num_particles) < abs(count_hi - num_particles):
            side = side_lo

    actual = _count_sphere(side)

    # Physical diameter ~0.30 m regardless of resolution (density varies)
    diameter = 0.30
    spacing  = diameter / max(side - 1, 1)
    particle_radius = spacing * 0.45

    return (side, side, side, spacing,
            BALL_START_X, BALL_START_Y, BALL_START_Z,
            actual, particle_radius)


# ─────────────────────────────────────────────────────────────────────────────
#  CSV helpers
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

def run_kernel_one_frame(rj_server, kernel_path, work_dir, frame_num,
                         positions, velocities, grid_params):
    """Run mpm_snow_ball_kernel.py for one frame.
    grid_params: (grid_nx, grid_ny, grid_nz, spacing, cx, cy, cz, num_particles)
    Returns (new_positions, new_velocities).
    """
    grid_nx, grid_ny, grid_nz, spacing, cx, cy, cz, num_particles = grid_params
    particle_mass = SNOW_DENSITY * spacing ** 3

    pos_csv = os.path.join(work_dir, "positions.csv")
    vel_csv = os.path.join(work_dir, "velocities.csv")
    par_csv = os.path.join(work_dir, "params.csv")
    out_pos = os.path.join(work_dir, f"out_positions_{frame_num}.csv")
    out_vel = os.path.join(work_dir, f"out_velocities_{frame_num}.csv")

    write_csv_1d(pos_csv, positions)
    write_csv_1d(vel_csv, velocities)
    write_csv_1d(par_csv, [
        DT,
        GRAVITY_Z,
        THROW_SPEED_Y,
        WALL_Y,
        WALL_FRICTION,
        GROUND_FRICTION,
        float(frame_num),
        float(num_particles),
        float(grid_nx),
        float(grid_ny),
        float(grid_nz),
        float(spacing),
        float(cx),
        float(cy),
        float(cz),
        float(particle_mass),   # params[15]  — NEW for MPM P2G/G2P
    ])

    dump_vars = {
        "positions":  out_pos,
        "velocities": out_vel,
    }

    try:
        rj_server.run_kernel(kernel_path, [pos_csv, vel_csv, par_csv], dump_vars)
    except Exception as exc:
        log(f"ERROR: kernel execution failed at frame {frame_num}: {exc}")
        raise

    return read_csv_1d(out_pos), read_csv_1d(out_vel)


# ─────────────────────────────────────────────────────────────────────────────
#  Newton viewer (no physics — draws what Ramanujan produced)
# ─────────────────────────────────────────────────────────────────────────────

def build_model_and_viewer(viewer_kind, output_path, initial_positions,
                           num_particles, particle_radius):
    import warp as wp
    import newton

    builder = newton.ModelBuilder(up_axis=newton.Axis.Z)
    builder.add_ground_plane()

    # Snowball particles from Ramanujan frame-0 output
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

    # Static wall at y = WALL_Y
    wall_body = builder.add_body(
        xform=wp.transform(wp.vec3(0.0, WALL_Y, WALL_VISUAL_Z), wp.quat_identity()),
        mass=0.0,
    )
    builder.add_shape_box(body=wall_body, hx=WALL_HX, hy=WALL_HY, hz=WALL_HZ)

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

    # Wall is static — precompute its transform once
    wall_xform = wp.transform(wp.vec3(0.0, WALL_Y, WALL_VISUAL_Z), wp.quat_identity())
    wall_array = wp.array([wall_xform], dtype=body_dtype)

    log("Pre-building frame arrays for smooth playback...")
    pos_arrays = []
    for positions, _vels in frames:
        arr = np.asarray(positions, dtype=np.float32).reshape(-1, 3)
        pos_arrays.append(wp.array(arr, dtype=pos_dtype))

    log("Playback started — close the viewer window to exit.")
    f_idx = 0
    while viewer.is_running():
        wp.copy(state.particle_q, pos_arrays[f_idx])
        wp.copy(state.body_q, wall_array)

        viewer.begin_frame(f_idx * DT)
        viewer.log_state(state)
        viewer.end_frame()

        f_idx = (f_idx + 1) % len(frames)

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
                        help="Target particle count (rounded to sphere inscribed in odd^3 cube).")
    parser.add_argument("--viewer", choices=["gl", "usd", "null"], default="gl")
    parser.add_argument("--output-path", default=None,
                        help="Required when --viewer usd")
    parser.add_argument("--keep-tmp", action="store_true",
                        help="Don't delete the per-frame CSVs.")
    parser.add_argument("--homelab", action="store_true",
                        help="Connect to an existing homelab server instead of starting a local JVM.")
    parser.add_argument("--homelab-url", default="http://localhost:8888",
                        help="Homelab server URL (default http://localhost:8888).")
    parser.add_argument("--save-frames", action="store_true",
                        help="Save frames to a .pkl file and skip viewer.")
    args = parser.parse_args()

    grid_nx, grid_ny, grid_nz, spacing, cx, cy, cz, num_particles, particle_radius = \
        compute_grid(args.num_particles)

    log(f"JAVA_HOME:       {JAVA_HOME}")
    log(f"RAMANUJAN_WS:    {RJ_WS}")
    log(f"Particles:       {num_particles}  ({grid_nx}^3 cube, sphere inscribed)")
    log(f"Spacing:         {spacing:.4f} m  →  ball diameter ≈ {spacing * (grid_nx - 1):.3f} m")
    log(f"Particle radius: {particle_radius:.4f} m  (viewer only)")
    log(f"Ball start:      y={cy:.2f} m  z={cz:.2f} m  throw={THROW_SPEED_Y} m/s → wall at y={WALL_Y}")

    if args.homelab:
        rj_server = RjHomelabClient(args.homelab_url)
    else:
        rj_server = RjServer(JAVA_HOME, RJ_WS)
    rj_server.start()

    here        = os.path.dirname(os.path.abspath(__file__))
    kernel_path = os.path.join(here, KERNEL_NAME)
    work_dir    = tempfile.mkdtemp(prefix="mpm_snow_ball_rj_")
    log(f"work dir: {work_dir}")

    grid_params = (grid_nx, grid_ny, grid_nz, spacing, cx, cy, cz, num_particles)

    positions  = [0.0] * (num_particles * 3)
    velocities = [0.0] * (num_particles * 3)

    frames = []
    frame_times = []
    try:
        for frame_num in range(args.frames):
            t0 = time.time()
            positions, velocities = run_kernel_one_frame(
                rj_server, kernel_path, work_dir, frame_num,
                positions, velocities, grid_params)
            frames.append((positions, velocities))
            frame_time = time.time() - t0
            frame_times.append(frame_time)
            if frame_num % 10 == 0:
                avg = sum(frame_times) / len(frame_times)
                log(f"  frame {frame_num}/{args.frames}  (latest={frame_time:.2f}s, avg={avg:.2f}s)")
    finally:
        rj_server.shutdown()

    if args.save_frames:
        timestamp = datetime.datetime.now().strftime("%y%m%d_%H%M%S")
        pkl_file = f"frames_{timestamp}.pkl"
        data = {
            "frames": frames,
            "num_particles": num_particles,
            "particle_radius": particle_radius,
            "frame_times": frame_times,
            "grid_params": grid_params,
        }
        with open(pkl_file, "wb") as f:
            pickle.dump(data, f)
        log(f"Saved {len(frames)} frames to {pkl_file}")
        log(f"To view later: python3 view_mpm_snow_ball.py {pkl_file}")
        return

    log(f"Ramanujan finished {len(frames)} frames; opening Newton viewer.")

    model, viewer = build_model_and_viewer(
        args.viewer, args.output_path,
        frames[0][0],
        num_particles, particle_radius)

    try:
        playback(model, viewer, frames, num_particles)
    finally:
        if not args.keep_tmp:
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()

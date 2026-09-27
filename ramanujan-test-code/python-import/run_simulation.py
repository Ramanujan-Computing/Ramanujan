#!/usr/bin/env python3
"""
Python runner and verifier for the multi-file N-body orbital simulation.
Runs either directly in standard Python or inspects Ramanujan simulation results.
"""
import sys
import os

# Add local directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import math_utils as mu
import physics as phys
import integrator
from diagnostics import (
    compute_total_kinetic_energy,
    compute_total_potential_energy,
    compute_system_momentum,
    compute_center_of_mass,
)

def run_simulation(verbose=True):
    n_bodies = 4
    G = 1.0
    eps = 0.05
    dt = 0.01
    total_steps = 50

    pos_x = [0 for _ in range(4)]
    pos_y = [0 for _ in range(4)]
    vel_x = [0 for _ in range(4)]
    vel_y = [0 for _ in range(4)]
    acc_x = [0 for _ in range(4)]
    acc_y = [0 for _ in range(4)]
    old_acc_x = [0 for _ in range(4)]
    old_acc_y = [0 for _ in range(4)]
    mass = [0 for _ in range(4)]

    # Body 0: Central star
    mass[0] = 500.0
    pos_x[0] = 0.0
    pos_y[0] = 0.0
    vel_x[0] = 0.0
    vel_y[0] = 0.0

    # Body 1: Planet A
    mass[1] = 1.0
    pos_x[1] = 10.0
    pos_y[1] = 0.0
    vel_x[1] = 0.0
    vel_y[1] = 7.071

    # Body 2: Planet B
    mass[2] = 2.0
    pos_x[2] = 0.0
    pos_y[2] = -20.0
    vel_x[2] = -5.0
    vel_y[2] = 0.0

    # Body 3: Comet C
    mass[3] = 0.1
    pos_x[3] = -15.0
    pos_y[3] = 15.0
    vel_x[3] = 3.5
    vel_y[3] = -3.5

    integrator.compute_accelerations(pos_x, pos_y, mass, acc_x, acc_y, n_bodies, G, eps)

    init_ke = compute_total_kinetic_energy(mass, vel_x, vel_y, n_bodies)
    init_pe = compute_total_potential_energy(pos_x, pos_y, mass, n_bodies, G, eps)
    init_total_energy = init_ke + init_pe
    init_px, init_py = compute_system_momentum(mass, vel_x, vel_y, n_bodies)
    init_cmx, init_cmy = compute_center_of_mass(mass, pos_x, pos_y, n_bodies)

    if verbose:
        print("=== Initial Simulation State ===")
        print(f"Total Bodies: {n_bodies}")
        print(f"Initial KE: {init_ke:.6f}, PE: {init_pe:.6f}, Total E: {init_total_energy:.6f}")
        print(f"Initial Momentum: ({init_px:.6f}, {init_py:.6f})")
        print(f"Initial Center of Mass: ({init_cmx:.6f}, {init_cmy:.6f})")

    step = 0
    while step < total_steps:
        k = 0
        while k < n_bodies:
            old_acc_x[k] = acc_x[k]
            old_acc_y[k] = acc_y[k]
            k = k + 1

        integrator.update_positions(pos_x, pos_y, vel_x, vel_y, acc_x, acc_y, n_bodies, dt)
        integrator.compute_accelerations(pos_x, pos_y, mass, acc_x, acc_y, n_bodies, G, eps)
        integrator.update_velocities(vel_x, vel_y, old_acc_x, old_acc_y, acc_x, acc_y, n_bodies, dt)
        step = step + 1

    final_ke = compute_total_kinetic_energy(mass, vel_x, vel_y, n_bodies)
    final_pe = compute_total_potential_energy(pos_x, pos_y, mass, n_bodies, G, eps)
    final_total_energy = final_ke + final_pe
    final_px, final_py = compute_system_momentum(mass, vel_x, vel_y, n_bodies)
    final_cmx, final_cmy = compute_center_of_mass(mass, pos_x, pos_y, n_bodies)

    diff_e = final_total_energy - init_total_energy
    energy_drift = mu.abs_val(diff_e)

    if verbose:
        print(f"\n=== Final Simulation State ({total_steps} steps) ===")
        print(f"Final KE: {final_ke:.6f}, PE: {final_pe:.6f}, Total E: {final_total_energy:.6f}")
        print(f"Energy Drift: {energy_drift:.6f}")
        print(f"Final Momentum: ({final_px:.6f}, {final_py:.6f})")
        print(f"Final Center of Mass: ({final_cmx:.6f}, {final_cmy:.6f})")
        print("\nBody Positions:")
        for i in range(n_bodies):
            print(f"  Body {i}: pos=({pos_x[i]:.4f}, {pos_y[i]:.4f}), vel=({vel_x[i]:.4f}, {vel_y[i]:.4f})")

    return {
        "init_total_energy": init_total_energy,
        "final_total_energy": final_total_energy,
        "energy_drift": energy_drift,
        "final_px": final_px,
        "final_py": final_py,
        "pos_x": pos_x,
        "pos_y": pos_y,
        "vel_x": vel_x,
        "vel_y": vel_y,
    }

def run_via_homelab(homelab_url="http://localhost:8888"):
    import json
    import urllib.request

    here = os.path.dirname(os.path.abspath(__file__))
    main_py = os.path.join(here, "main.py")
    math_utils_py = os.path.join(here, "math_utils.py")
    physics_py = os.path.join(here, "physics.py")
    integrator_py = os.path.join(here, "integrator.py")
    diagnostics_py = os.path.join(here, "diagnostics.py")

    args = [main_py, math_utils_py, physics_py, integrator_py, diagnostics_py]
    print(f"Connecting to homelab server at {homelab_url}...")
    try:
        urllib.request.urlopen(f"{homelab_url}/pings/heartbeat", timeout=5)
    except Exception as e:
        print(f"ERROR: Cannot reach homelab server at {homelab_url}: {e}", file=sys.stderr)
        return False

    print("Submitting multi-file simulation to homelab orchestrator (/orchestrator/run)...")
    req_data = json.dumps({"args": args}).encode("utf-8")
    run_req = urllib.request.Request(
        f"{homelab_url}/orchestrator/run",
        data=req_data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    t0 = os.times().elapsed if hasattr(os, "times") else 0
    with urllib.request.urlopen(run_req, timeout=120) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        print(f"Homelab run status: {res.get('status')}")

    # Query final positions and variables
    out_dir = os.path.join(here, "output")
    os.makedirs(out_dir, exist_ok=True)
    pos_x_csv = os.path.join(out_dir, "pos_x.csv")
    pos_y_csv = os.path.join(out_dir, "pos_y.csv")

    for arr_name, arr_path in [("pos_x", pos_x_csv), ("pos_y", pos_y_csv)]:
        dump_req = urllib.request.Request(
            f"{homelab_url}/orchestrator/dump",
            data=json.dumps({"name": arr_name, "path": arr_path}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(dump_req, timeout=30) as d_resp:
            d_res = json.loads(d_resp.read().decode("utf-8"))
            print(f"Dumped {arr_name} -> {arr_path}: {d_res.get('status')}")

    # Query variables
    for var_name in ["simulation_done", "energy_drift", "final_total_energy", "final_px", "final_py"]:
        var_req = urllib.request.Request(
            f"{homelab_url}/orchestrator/var",
            data=json.dumps({"name": var_name}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        try:
            with urllib.request.urlopen(var_req, timeout=30) as v_resp:
                v_res = json.loads(v_resp.read().decode("utf-8"))
                print(f"Variable {var_name} = {v_res.get('value')}")
        except Exception as e:
            print(f"Could not fetch variable {var_name}: {e}")

    return True

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Run N-body simulation via standard Python or Ramanujan Homelab")
    parser.add_argument("--homelab", action="store_true", help="Submit to homelab server")
    parser.add_argument("--homelab-url", default="http://localhost:8888", help="Homelab server URL")
    args = parser.parse_args()

    if args.homelab:
        run_via_homelab(args.homelab_url)
    else:
        run_simulation(verbose=True)

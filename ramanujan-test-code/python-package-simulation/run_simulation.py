"""
Runner script for the modular spacecraft subsystem package simulation.
Tests:
  1. Entrypoint named 'app.py' (instead of standard 'main.py').
  2. Package structure with duplicate basenames across directories:
     - subsystems/thermal/controller.py vs subsystems/power/controller.py
     - subsystems/thermal/sensor.py vs subsystems/power/sensor.py
  3. Transitive module imports across packages.

Can be run:
  - Directly with Python:
      python run_simulation.py
  - Via Ramanujan Homelab Server and Worker:
      python run_simulation.py --homelab --homelab-url http://localhost:8888
"""

import os
import sys

def run_direct():
    print("Running package simulation via direct Python interpreter...")
    # Add directory to sys.path so package imports work
    here = os.path.dirname(os.path.abspath(__file__))
    if here not in sys.path:
        sys.path.insert(0, here)

    import app
    print(f"Simulation completed successfully.")
    print(f"  simulation_done = {app.simulation_done}")
    print(f"  final_temp      = {app.final_temp:.4f} K (target: 300.0 K, drift: {app.temp_drift:.4f})")
    print(f"  final_volts     = {app.final_volts:.4f} V (target: 28.0 V, drift: {app.volts_drift:.4f})")
    print(f"  temp_history    = {[round(x, 2) for x in app.temp_history]}")
    print(f"  power_history   = {[round(x, 2) for x in app.power_history]}")
    return True

def run_via_homelab(homelab_url="http://localhost:8888"):
    import json
    import urllib.request

    here = os.path.dirname(os.path.abspath(__file__))
    app_py = os.path.join(here, "app.py")
    math_ops_py = os.path.join(here, "common", "math_ops.py")
    thermal_sensor_py = os.path.join(here, "subsystems", "thermal", "sensor.py")
    thermal_controller_py = os.path.join(here, "subsystems", "thermal", "controller.py")
    power_sensor_py = os.path.join(here, "subsystems", "power", "sensor.py")
    power_controller_py = os.path.join(here, "subsystems", "power", "controller.py")

    args = [
        app_py,
        math_ops_py,
        thermal_sensor_py,
        thermal_controller_py,
        power_sensor_py,
        power_controller_py,
    ]

    print(f"Connecting to homelab server at {homelab_url}...")
    try:
        urllib.request.urlopen(f"{homelab_url}/pings/heartbeat", timeout=5)
    except Exception as e:
        print(f"ERROR: Cannot reach homelab server at {homelab_url}: {e}", file=sys.stderr)
        return False

    print("Submitting package-style simulation with 'app.py' entrypoint to homelab (/orchestrator/run)...")
    req_data = json.dumps({"args": args}).encode("utf-8")
    run_req = urllib.request.Request(
        f"{homelab_url}/orchestrator/run",
        data=req_data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    with urllib.request.urlopen(run_req, timeout=120) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        print(f"Homelab run status: {res.get('status')}")

    out_dir = os.path.join(here, "output")
    os.makedirs(out_dir, exist_ok=True)
    temp_csv = os.path.join(out_dir, "temp_history.csv")
    power_csv = os.path.join(out_dir, "power_history.csv")

    for arr_name, arr_path in [("temp_history", temp_csv), ("power_history", power_csv)]:
        dump_req = urllib.request.Request(
            f"{homelab_url}/orchestrator/dump",
            data=json.dumps({"name": arr_name, "path": arr_path}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(dump_req, timeout=30) as d_resp:
            d_res = json.loads(d_resp.read().decode("utf-8"))
            print(f"Dumped {arr_name} -> {arr_path}: {d_res.get('status')}")

    # Query scalar variables
    variables = {}
    for var_name in ["simulation_done", "final_temp", "final_volts", "temp_drift", "volts_drift"]:
        var_req = urllib.request.Request(
            f"{homelab_url}/orchestrator/var",
            data=json.dumps({"name": var_name}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        try:
            with urllib.request.urlopen(var_req, timeout=30) as v_resp:
                v_res = json.loads(v_resp.read().decode("utf-8"))
                val = v_res.get("value")
                variables[var_name] = val
                print(f"Variable {var_name} = {val}")
        except Exception as e:
            print(f"Could not fetch variable {var_name}: {e}")

    # Verification assertions
    assert variables.get("simulation_done") == 1.0 or variables.get("simulation_done") == 1, \
        f"Expected simulation_done == 1.0, got {variables.get('simulation_done')}"
    assert variables.get("final_temp") is not None, "final_temp should not be None"
    assert variables.get("final_volts") is not None, "final_volts should not be None"

    print("\nAll verification assertions passed for package simulation via homelab!")
    return True

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Run spacecraft package simulation")
    parser.add_argument("--homelab", action="store_true", help="Submit to homelab server")
    parser.add_argument("--homelab-url", default="http://localhost:8888", help="Homelab server URL")
    cli_args = parser.parse_args()

    if cli_args.homelab:
        run_via_homelab(cli_args.homelab_url)
    else:
        run_direct()

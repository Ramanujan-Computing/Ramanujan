import argparse
import json
import os
import sys
import time
import urllib.request

class RjHomelabClient:
    def __init__(self, homelab_url):
        self.homelab_url = homelab_url.rstrip("/")

    def start(self, timeout=10):
        try:
            urllib.request.urlopen(f"{self.homelab_url}/pings/heartbeat", timeout=timeout)
            print(f"Connected to homelab server at {self.homelab_url}")
        except Exception as e:
            print(f"ERROR: Cannot reach homelab server at {self.homelab_url}: {e}", file=sys.stderr)
            sys.exit(1)

    def run_kernel(self, kernel_py, csv_args, dump_vars, timeout=3600):
        req_data = {"args": [kernel_py] + csv_args}
        run_body = json.dumps(req_data).encode()
        run_req = urllib.request.Request(
            f"{self.homelab_url}/orchestrator/run",
            data=run_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        print(f"Submitting {kernel_py} to the homelab server... This may take a while.")
        start_time = time.time()
        try:
            urllib.request.urlopen(run_req, timeout=timeout)
        except Exception as e:
            raise RuntimeError(f"Homelab /orchestrator/run failed: {e}")
        
        print(f"Simulation completed in {time.time() - start_time:.2f} seconds.")

        for name, path in dump_vars.items():
            print(f"Dumping {name} to {path}...")
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

def main():
    parser = argparse.ArgumentParser(description="Run LBM Simulation via Ramanujan Homelab")
    parser.add_argument("--homelab-url", default="http://localhost:8888", help="URL of the homelab server")
    args = parser.parse_args()

    client = RjHomelabClient(args.homelab_url)
    kernel_path = os.path.join(os.path.dirname(__file__), "lbm_ramanujan.py")
    
    out_dir = os.path.join(os.path.dirname(__file__), "lbm_output")
    os.makedirs(out_dir, exist_ok=True)
    
    u_frames   = []
    v_frames   = []
    rho_frames = []
    N_BATCHES  = 100   # 100 × 200 steps = 20 000 total — enough for Re=80 vortex shedding
    
    f_prev_csv   = os.path.join(out_dir, "f_prev.csv")
    f_next_csv   = os.path.join(out_dir, "f_next.csv")
    f_post_csv   = os.path.join(out_dir, "f_post.csv")
    density_csv  = os.path.join(out_dir, "density.csv")
    u_vel_csv    = os.path.join(out_dir, "u_vel.csv")
    v_vel_csv    = os.path.join(out_dir, "v_vel.csv")
    obstacle_csv = os.path.join(out_dir, "obstacle.csv")
    params_csv   = os.path.join(out_dir, "params.csv")

    print("Initializing working arrays to zero...")
    zeros_135k = ",".join(["0.0"] * 135000)
    zeros_15k  = ",".join(["0.0"] * 15000)
    with open(f_prev_csv,   "w") as f: f.write(zeros_135k)
    with open(f_next_csv,   "w") as f: f.write(zeros_135k)
    with open(f_post_csv,   "w") as f: f.write(zeros_135k)
    with open(density_csv,  "w") as f: f.write(zeros_15k)
    with open(u_vel_csv,    "w") as f: f.write(zeros_15k)
    with open(v_vel_csv,    "w") as f: f.write(zeros_15k)
    with open(obstacle_csv, "w") as f: f.write(zeros_15k)

    for frame in range(N_BATCHES):
        print(f"Running batch {frame+1}/{N_BATCHES} (steps {frame*200}–{(frame+1)*200})...")
        
        with open(params_csv, "w") as f:
            f.write(f"0.0,{1.0 if frame > 0 else 0.0}")
            
        csv_args = [
            f_prev_csv,
            params_csv,
            f_next_csv,
            f_post_csv,
            density_csv,
            u_vel_csv,
            v_vel_csv,
            obstacle_csv,
        ]
        
        dump_vars = {
            "f_prev":   f_prev_csv,
            "f_next":   f_next_csv,
            "f_post":   f_post_csv,
            "density":  density_csv,
            "u_vel":    u_vel_csv,
            "v_vel":    v_vel_csv,
            "obstacle": obstacle_csv,
        }
            
        client.run_kernel(kernel_path, csv_args, dump_vars)
        
        with open(u_vel_csv) as f:
            u_frames.extend([float(x) for x in f.read().strip().split(",") if x])
        with open(v_vel_csv) as f:
            v_frames.extend([float(x) for x in f.read().strip().split(",") if x])
        with open(density_csv) as f:
            rho_frames.extend([float(x) for x in f.read().strip().split(",") if x])


    print("Writing aggregated frames to CSV...")
    with open(os.path.join(out_dir, "u_frames.csv"), "w") as f:
        f.write(",".join(map(str, u_frames)))
    with open(os.path.join(out_dir, "v_frames.csv"), "w") as f:
        f.write(",".join(map(str, v_frames)))
    with open(os.path.join(out_dir, "rho_frames.csv"), "w") as f:
        f.write(",".join(map(str, rho_frames)))
        
    print(f"Done. Saved {N_BATCHES} frames to {out_dir}")

if __name__ == "__main__":
    main()

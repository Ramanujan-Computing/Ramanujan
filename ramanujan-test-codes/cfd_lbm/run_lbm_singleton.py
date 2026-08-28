"""
run_lbm_singleton.py
Submits lbm_singleton.py to the Ramanujan homelab worker in a single call,
waits for all 15 000 LBM steps to complete, then dumps the 100-frame result.
No inter-batch handoffs — state stays coherent the whole way through.
"""
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
            print(f"Connected to homelab at {self.homelab_url}")
        except Exception as e:
            print(f"ERROR: Cannot reach homelab at {self.homelab_url}: {e}", file=sys.stderr)
            sys.exit(1)

    def run_and_dump(self, kernel_py, dump_vars, timeout=7200):
        """Submit kernel, wait for completion, then dump requested arrays."""
        req_data = {"args": [kernel_py]}
        run_body = json.dumps(req_data).encode()
        run_req = urllib.request.Request(
            f"{self.homelab_url}/orchestrator/run",
            data=run_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        print(f"Submitting {os.path.basename(kernel_py)} …")
        print("  (15 000 LBM steps — this may take several minutes)")
        t0 = time.time()
        try:
            urllib.request.urlopen(run_req, timeout=timeout)
        except Exception as e:
            raise RuntimeError(f"/orchestrator/run failed: {e}")
        elapsed = time.time() - t0
        print(f"Kernel finished in {elapsed:.1f}s")

        for name, path in dump_vars.items():
            print(f"Dumping {name} → {path} …")
            body = json.dumps({"name": name, "path": path}).encode()
            req = urllib.request.Request(
                f"{self.homelab_url}/orchestrator/dump",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            try:
                urllib.request.urlopen(req, timeout=120)
            except Exception as e:
                raise RuntimeError(f"/orchestrator/dump {name} failed: {e}")
            print(f"  → saved ({os.path.getsize(path):,} bytes)")


def main():
    parser = argparse.ArgumentParser(
        description="Run singleton LBM simulation via Ramanujan homelab"
    )
    parser.add_argument(
        "--homelab-url", default="http://localhost:8888",
        help="URL of the homelab server (default: http://localhost:8888)"
    )
    args = parser.parse_args()

    here    = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(here, "lbm_output")
    os.makedirs(out_dir, exist_ok=True)

    client = RjHomelabClient(args.homelab_url)
    client.start()

    kernel = os.path.join(here, "lbm_singleton.py")
    dump_vars = {
        "u_frames": os.path.join(out_dir, "u_frames.csv"),
        "v_frames": os.path.join(out_dir, "v_frames.csv"),
    }

    client.run_and_dump(kernel, dump_vars)

    print(f"\nDone. Results written to {out_dir}/")
    print("Run  python3 visualize_lbm.py  to generate the animation.")


if __name__ == "__main__":
    main()

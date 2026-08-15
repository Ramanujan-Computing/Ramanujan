#!/usr/bin/env python3
"""
Interactive chat UI for Phi-3 via Ramanujan homelab server.
Maintains conversation history and sends full context each turn.
Max generated tokens per response: 40.
"""
import argparse
import contextlib
import io
import os
import shutil
import sys
import tempfile
import time

os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")
os.environ.setdefault("HF_HUB_DISABLE_IMPLICIT_TOKEN", "1")
os.environ.setdefault("HUGGINGFACE_HUB_VERBOSITY", "error")

import warnings
warnings.filterwarnings("ignore")

import numpy as np
with contextlib.redirect_stderr(io.StringIO()):
    from transformers import AutoTokenizer

N_TOKENS = 500


# ---------------------------------------------------------------------------
# Homelab client (copied from run_phi3_4bit.py, no local JVM needed)
# ---------------------------------------------------------------------------

class RjHomelabClient:
    def __init__(self, homelab_url):
        self.homelab_url = homelab_url.rstrip("/")

    def start(self, timeout=10):
        import urllib.request
        try:
            urllib.request.urlopen(f"{self.homelab_url}/pings/heartbeat", timeout=timeout)
        except Exception as e:
            print(f"ERROR: Cannot reach homelab server at {self.homelab_url}: {e}", file=sys.stderr)
            sys.exit(1)

    def run_kernel(self, kernel_py, csv_args, dump_vars, timeout=600):
        import json, urllib.request
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


# ---------------------------------------------------------------------------
# CSV helpers
# ---------------------------------------------------------------------------

def write_flat_csv(path, values):
    with open(path, "w") as f:
        f.write(",".join(
            str(x) if isinstance(x, int) else f"{x:.8g}" for x in values
        ) + "\n")


def read_flat_csv(path):
    with open(path) as f:
        text = f.read().strip()
    if not text:
        return []
    return [float(t) for t in text.split(",") if t]


# ---------------------------------------------------------------------------
# Single generation call
# ---------------------------------------------------------------------------

def generate_turn(messages, weights_dir, homelab_url, tokenizer):
    """Send `messages` (full history) to homelab, return decoded reply string."""
    formatted = tokenizer.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=True
    )
    input_ids = tokenizer.encode(formatted, add_special_tokens=False)
    n_seq = len(input_ids)

    kernel_path = os.path.join(
        os.path.dirname(__file__), "..", "phi3_transformer_stack_4bit.py"
    )
    kernel_path = os.path.abspath(kernel_path)
    work_dir = tempfile.mkdtemp(prefix="phi3_chat_")

    try:
        rj = RjHomelabClient(homelab_url)
        rj.start()

        wte = np.load(os.path.join(weights_dir, "wte.npy")).astype(np.float32)
        wte = wte.reshape(-1, 3072)

        hidden = wte[input_ids]
        write_flat_csv(os.path.join(work_dir, "hidden.csv"), hidden.flatten().tolist())
        write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq), float(N_TOKENS)])
        write_flat_csv(os.path.join(work_dir, "cur_n_seq_arr.csv"), [float(n_seq)])
        write_flat_csv(os.path.join(work_dir, "step_arr.csv"), [0.0])

        csv_args = [
            os.path.join(work_dir, "hidden.csv"),
            os.path.join(work_dir, "params.csv"),
            os.path.join(work_dir, "cur_n_seq_arr.csv"),
            os.path.join(work_dir, "step_arr.csv"),
            os.path.join(weights_dir, "wte_1.csv"),
            os.path.join(weights_dir, "wte_2.csv"),
            os.path.join(weights_dir, "lm_head_1.csv"),
            os.path.join(weights_dir, "lm_head_2.csv"),
            os.path.join(weights_dir, "ln_f_g.csv"),
            os.path.join(weights_dir, "cos_cache.csv"),
            os.path.join(weights_dir, "sin_cache.csv"),
        ]
        per_layer_stems = [
            "qkv_packed", "qkv_scales",
            "o_packed", "o_scales",
            "gate_up_packed", "gate_up_scales",
            "down_packed", "down_scales",
            "ln1_g", "ln2_g",
        ]
        for i in range(32):
            for stem in per_layer_stems:
                csv_args.append(os.path.join(weights_dir, f"l{i}_{stem}.csv"))

        out_csv = os.path.join(work_dir, "generated_tokens.csv")
        dump_vars = {"generated_tokens": out_csv}

        t0 = time.time()
        rj.run_kernel(kernel_path, csv_args, dump_vars)
        _ = t0  # timing available if needed

        gen_tokens = read_flat_csv(out_csv)
        out_ids = [int(t) for t in gen_tokens[:N_TOKENS]]
        return tokenizer.decode(out_ids, skip_special_tokens=True).strip()

    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


# ---------------------------------------------------------------------------
# Chat loop
# ---------------------------------------------------------------------------

def chat(weights_dir, homelab_url):
    tokenizer = AutoTokenizer.from_pretrained(
        "microsoft/Phi-3-mini-4k-instruct", trust_remote_code=True
    )
    weights_dir = os.path.abspath(weights_dir)
    history = []  # list of {"role": ..., "content": ...}

    print("\nPhi-3 Chat (homelab) — type 'quit' or 'exit' to stop.\n")

    while True:
        try:
            user_input = input("You: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nBye.")
            break

        if not user_input:
            continue
        if user_input.lower() in ("quit", "exit"):
            print("Bye.")
            break

        history.append({"role": "user", "content": user_input})

        try:
            reply = generate_turn(history, weights_dir, homelab_url, tokenizer)
        except Exception as e:
            print(f"ERROR: {e}", file=sys.stderr)
            history.pop()
            continue

        history.append({"role": "assistant", "content": reply})
        print(f"\nPhi-3: {reply}\n")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Interactive Phi-3 chat via homelab")
    parser.add_argument("--weights-dir", default="../phi3_weights_csv",
                        help="Path to phi3_weights_csv directory")
    parser.add_argument("--homelab-url", default="http://localhost:8888",
                        help="Homelab server base URL")
    args = parser.parse_args()

    chat(args.weights_dir, args.homelab_url)

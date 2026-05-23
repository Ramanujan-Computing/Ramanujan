#!/usr/bin/env python3
import argparse
import datetime
import os
import platform
import select
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import numpy as np
from transformers import AutoTokenizer

def log(msg=""):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)

class RjServer:
    def __init__(self, java_home: str, rj_ws: str):
        self.java_home = java_home
        self.rj_ws = rj_ws
        self.proc = None
        self._stderr_t = None

    def start(self, timeout: int = 60):
        java_bin = os.path.join(self.java_home, "bin", "java")
        rj_jar = os.environ.get(
            "RAMANUJAN_FAT_JAR",
            os.path.expanduser("~/Desktop/ws/developer-console-1.0-SNAPSHOT-fat.jar"),
        )
        cmd = [java_bin, "-Xmx14g", "-XX:+UseG1GC", "-jar", rj_jar, "server"]
        log(f"Starting Ramanujan JVM server ...")
        env = os.environ.copy()
        env["JAVA_HOME"]    = self.java_home
        env["RAMANUJAN_WS"] = self.rj_ws

        self.proc = subprocess.Popen(
            cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, bufsize=1, env=env,
        )
        self._stderr_t = threading.Thread(target=self._drain_stderr, daemon=True)
        self._stderr_t.start()

        deadline = time.time() + timeout
        while time.time() < deadline:
            line = self.proc.stdout.readline()
            if not line:
                log("ERROR: JVM exited before SERVER_READY")
                sys.exit(1)
            if line.rstrip() == "SERVER_READY":
                log("JVM server ready")
                return
        log("ERROR: timeout waiting for SERVER_READY")
        sys.exit(1)

    def _drain_stderr(self):
        for line in self.proc.stderr:
            sys.stderr.write("[JVM] " + line)

    def _readline_deadline(self, deadline: float) -> str:
        while True:
            remaining = deadline - time.time()
            if remaining <= 0: return None
            ready, _, _ = select.select([self.proc.stdout], [], [], min(remaining, 1.0))
            if ready: return self.proc.stdout.readline()

    def run_kernel(self, kernel_py: str, csv_args: list, dump_vars: dict, timeout: int = 300):
        args_str = " ".join([kernel_py] + csv_args)
        print(args_str)
        self.proc.stdin.write(f"run {args_str}\n")
        self.proc.stdin.flush()
        #time.sleep(10000);

        deadline = time.time() + timeout
        while True:
            line = self._readline_deadline(deadline)
            if line is None: raise RuntimeError(f"KERNEL_TIMEOUT after {timeout}s")
            if not line: raise RuntimeError(f"JVM closed during {os.path.basename(kernel_py)}")
            line = line.rstrip()
            if line == "KERNEL_DONE": break
            if line.startswith("KERNEL_ERROR"): raise RuntimeError(f"KERNEL_ERROR: {line}")

        for name, path in dump_vars.items():
            self.proc.stdin.write(f"dump {name} {path}\n")
            self.proc.stdin.flush()
            ddl = time.time() + 120
            while True:
                dline = self._readline_deadline(ddl)
                if dline is None: raise RuntimeError(f"TIMEOUT during dump {name}")
                if not dline: raise RuntimeError(f"JVM closed during dump {name}")
                if dline.rstrip().startswith("Dumped"): break

    def shutdown(self):
        if self.proc:
            try:
                self.proc.stdin.write("quit\n")
                self.proc.stdin.flush()
                self.proc.wait(timeout=10)
            except Exception:
                self.proc.kill()
            log("JVM server shut down")

def write_flat_csv(path: str, values):
    with open(path, "w") as f:
        f.write(",".join(map(lambda x: f"{x:.6g}", values)) + "\n")

def read_flat_csv(path: str) -> list:
    with open(path) as f:
        text = f.read().strip()
    if not text: return []
    return [float(t) for t in text.split(",") if t]

def generate(prompt, n_tokens, weights_dir, java_home, rj_ws):
    weights_dir = os.path.abspath(weights_dir)
    tokenizer = AutoTokenizer.from_pretrained(
        "microsoft/Phi-3-mini-4k-instruct", trust_remote_code=True
    )
    input_ids = tokenizer.encode(prompt, add_special_tokens=True)
    log(f"Prompt: {repr(prompt)}")
    log(f"Input token ids ({len(input_ids)}): {input_ids}")
    
    n_seq = len(input_ids)
    
    kernel_path = os.path.join(os.path.dirname(__file__), "phi3_transformer_stack_4bit.py")
    work_dir = tempfile.mkdtemp(prefix="phi3_rj_")
    log(f"Working directory: {work_dir}")
    
    try:
        rj_server = RjServer(java_home=java_home, rj_ws=rj_ws)
        rj_server.start()
        
        # Load embedding table directly in python to seed the hidden state
        # Loading from .npy is virtually instant, unlike .csv
        log("Reading WTE for prompt embed...")
        wte = np.load(os.path.join(weights_dir, "wte.npy")).astype(np.float32)
        wte = wte.reshape(-1, 3072)
        
        hidden = wte[input_ids]
        hidden_flat = hidden.flatten().tolist()
        
        write_flat_csv(os.path.join(work_dir, "hidden.csv"), hidden_flat)
        write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq), float(n_tokens)])
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
        # Per-layer weights (kernel expects variables named l{i}_<stem>; CSV
        # filename stems must match exactly).
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
        log("Executing Uber Loop GPU Kernel (Prefill + Autoregressive Decoding)...")
        rj_server.run_kernel(kernel_path, csv_args, dump_vars)
        log(f"Kernel complete in {time.time()-t0:.3f}s")
        
        gen_tokens = read_flat_csv(out_csv)
        # Token array comes back padded to 1024, but only n_tokens are populated
        out_ids = [int(t) for t in gen_tokens[:n_tokens]]
        
        log(f"Generated ids: {out_ids}")
        text = tokenizer.decode(out_ids)
        log(f"Output text:\n{text}")
        
    finally:
        if 'rj_server' in locals():
            rj_server.shutdown()
        shutil.rmtree(work_dir, ignore_errors=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("prompt", type=str)
    parser.add_argument("--n-tokens", type=int, default=40)
    parser.add_argument("--weights-dir", default="phi3_weights_csv")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME", "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home"))
    parser.add_argument("--rj-ws", default=os.environ.get("RAMANUJAN_WS", "/tmp"))
    args = parser.parse_args()
    
    generate(args.prompt, args.n_tokens, args.weights_dir, args.java_home, args.rj_ws)

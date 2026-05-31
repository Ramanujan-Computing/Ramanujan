#!/usr/bin/env python3
"""
GPT-2 124M inference orchestrator for Ramanujan.

Architecture:
  • Token embedding lookup  ─── Python / NumPy  (large vocab indices, exact doubles)
  • 12 × transformer blocks ─── Ramanujan        (GPU matmuls + host attention/norm)
  • Final layer norm + logits── Python / NumPy  (large vocab, exact doubles)

All 12 transformer blocks are executed in a SINGLE JVM kernel call via
transformer_stack.py, eliminating 11 of the 12 pipe round-trips (~89ms each).
All 168 weight arrays (12 layers × 14 tensors) are passed as CSV args; the
Ramanujan runtime auto-names them from their filename stems (l0_ln1_g, …).
The legacy layer_kernel.py / run_layer() path is kept for debugging.

Usage:
    python run_gpt2.py "Alan Turing theorized" --n-tokens 40 --weights-dir weights

Prerequisites:
    1.  pip install numpy regex requests tqdm
    2.  python download_weights.py          # downloads + converts to CSV/NPY
    3.  Build Ramanujan with GPU support and set RAMANUJAN_FAT_JAR + JAVA_HOME
"""
import argparse
import datetime
import math
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

# BPE tokeniser from picoGPT (encoder.py must be in the same directory)
sys.path.insert(0, os.path.dirname(__file__))
from encoder import get_encoder

# ---------------------------------------------------------------------------
# Constants — must match layer_kernel.py and download_weights.py
# ---------------------------------------------------------------------------
N_LAYER = 12
N_EMBD  = 768
N_HEAD  = 12
D_HEAD  = 64       # N_EMBD // N_HEAD
N_FF    = 3072     # 4 * N_EMBD
N_VOCAB = 50257
N_CTX   = 1024

KERNEL_NAME       = "layer_kernel.py"
STACK_KERNEL_NAME = "transformer_stack.py"

# ---------------------------------------------------------------------------
# Logging helper
# ---------------------------------------------------------------------------
def log(msg=""):
    ts = datetime.datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


# ---------------------------------------------------------------------------
# CSV helpers
# ---------------------------------------------------------------------------
def write_flat_csv(path: str, values):
    """Write a 1-D iterable as a single-line CSV (Ramanujan format)."""
    with open(path, "w") as f:
        f.write(",".join(repr(float(v)) for v in values))
        f.write("\n")


def read_flat_csv(path: str) -> list:
    with open(path) as f:
        text = f.read().strip()
    if not text:
        return []
    return [float(t) for t in text.split(",") if t]


def write_zeros_csv(path: str, n: int):
    """Write n zeros as a single-line CSV."""
    with open(path, "w") as f:
        f.write(",".join("0.0" for _ in range(n)))
        f.write("\n")


# ---------------------------------------------------------------------------
# Weight symlinking (or copy on Windows)
# ---------------------------------------------------------------------------
def _link_weight(src: str, dst: str):
    """Create a symlink dst → src (absolute).  Falls back to copy on Windows."""
    if os.path.exists(dst) or os.path.islink(dst):
        os.remove(dst)
    if platform.system() == "Windows":
        shutil.copy2(src, dst)
    else:
        os.symlink(os.path.abspath(src), dst)


def setup_layer_links(work_dir: str, weights_dir: str, layer_idx: int):
    """
    Sym-link the weights for `layer_idx` into work_dir under generic names
    so that the Ramanujan kernel sees variables named ln1_g, c_attn_w, etc.
    """
    p = os.path.join(weights_dir, f"l{layer_idx}")
    names = [
        "ln1_g", "ln1_b",
        "c_attn_w", "c_attn_b",
        "c_proj_w", "c_proj_b",
        "ln2_g", "ln2_b",
        "c_fc_w", "c_fc_b",
        "c_fc_proj_w", "c_fc_proj_b",
    ]
    for name in names:
        _link_weight(f"{p}_{name}.csv", os.path.join(work_dir, f"{name}.csv"))


# ---------------------------------------------------------------------------
# Persistent Ramanujan JVM server (avoids JVM start-up per layer)
# ---------------------------------------------------------------------------
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
        if not os.path.exists(rj_jar):
            raise FileNotFoundError(
                f"Ramanujan JAR not found: {rj_jar}\n"
                "Set RAMANUJAN_FAT_JAR env var or run install_ramanujan.sh"
            )

        cmd = [java_bin, "-Xmx4g", "-XX:+UseG1GC", "-jar", rj_jar, "server"]
        log(f"Starting Ramanujan JVM server …")
        env = os.environ.copy()
        env["JAVA_HOME"]    = self.java_home
        env["RAMANUJAN_WS"] = self.rj_ws

        self.proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
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
        """readline() that actually respects a wall-clock deadline via select."""
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                return None  # caller treats None as timeout
            ready, _, _ = select.select([self.proc.stdout], [], [], min(remaining, 1.0))
            if ready:
                line = self.proc.stdout.readline()
                return line  # empty string means EOF
            # select timed out but deadline not yet reached — loop

    def run_kernel(self, kernel_py: str, csv_args: list,
                   dump_vars: dict, timeout: int = 60):
        args_str = " ".join([kernel_py] + csv_args)
        print(args_str)
        self.proc.stdin.write(f"run {args_str}\n")
        self.proc.stdin.flush()

        deadline = time.time() + timeout
        while True:
            line = self._readline_deadline(deadline)
            if line is None:
                raise RuntimeError(f"KERNEL_TIMEOUT after {timeout}s")
            if not line:
                raise RuntimeError(f"JVM closed during {os.path.basename(kernel_py)}")
            line = line.rstrip()
            if line == "KERNEL_DONE":
                break
            if line.startswith("KERNEL_ERROR"):
                raise RuntimeError(f"KERNEL_ERROR: {line}")

        for name, path in dump_vars.items():
            self.proc.stdin.write(f"dump {name} {path}\n")
            self.proc.stdin.flush()
            ddl = time.time() + 60
            while True:
                dline = self._readline_deadline(ddl)
                if dline is None:
                    raise RuntimeError(f"TIMEOUT during dump {name}")
                if not dline:
                    raise RuntimeError(f"JVM closed during dump {name}")
                if dline.rstrip().startswith("Dumped"):
                    break

    def kill(self):
        if self.proc:
            try:
                self.proc.kill()
                self.proc.wait(timeout=5)
            except Exception:
                pass
            self.proc = None

    def restart(self):
        log("Killing stuck JVM and restarting …")
        self.kill()
        self.start()

    def shutdown(self):
        if self.proc:
            try:
                self.proc.stdin.write("quit\n")
                self.proc.stdin.flush()
                self.proc.wait(timeout=10)
            except Exception:
                self.kill()
            log("JVM server shut down")


# ---------------------------------------------------------------------------
# GPT-2 forward pass — one transformer block via Ramanujan
# ---------------------------------------------------------------------------
def run_layer(
    rj_server: RjServer,
    kernel_path: str,
    work_dir: str,
    weights_dir: str,
    layer_idx: int,
    n_seq: int,
    hidden_flat: list,
) -> list:
    """
    Run layer_kernel.py for `layer_idx` on `hidden_flat` (length n_seq*768).
    Returns the updated hidden state as a flat Python list.
    """
    # Write hidden state and params
    write_flat_csv(os.path.join(work_dir, "hidden.csv"), hidden_flat)
    write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq)])

    # Sym-link layer weight CSVs under generic names
    setup_layer_links(work_dir, weights_dir, layer_idx)

    # Build ordered CSV argument list — order must match the kernel's expected
    # array names (derived from filename stems by the Ramanujan runtime).
    # qkv_buf / h_attn_buf / h_ff_buf / h_out_buf are now declared as local
    # arrays inside layer_kernel.py — no longer passed as zero CSVs, which
    # previously caused ~290K zero entries to be serialised through JNI per call.
    csv_args = [
        os.path.join(work_dir, "hidden.csv"),
        os.path.join(work_dir, "params.csv"),
        os.path.join(work_dir, "ln1_g.csv"),
        os.path.join(work_dir, "ln1_b.csv"),
        os.path.join(work_dir, "c_attn_w.csv"),
        os.path.join(work_dir, "c_attn_b.csv"),
        os.path.join(work_dir, "c_proj_w.csv"),
        os.path.join(work_dir, "c_proj_b.csv"),
        os.path.join(work_dir, "ln2_g.csv"),
        os.path.join(work_dir, "ln2_b.csv"),
        os.path.join(work_dir, "c_fc_w.csv"),
        os.path.join(work_dir, "c_fc_b.csv"),
        os.path.join(work_dir, "c_fc_proj_w.csv"),
        os.path.join(work_dir, "c_fc_proj_b.csv"),
    ]

    out_hidden_csv = os.path.join(work_dir, f"out_hidden_l{layer_idx}.csv")
    dump_vars = {"hidden": out_hidden_csv}

    for attempt in range(3):
        try:
            rj_server.run_kernel(kernel_path, csv_args, dump_vars)
            return read_flat_csv(out_hidden_csv)
        except RuntimeError as e:
            log(f"  layer {layer_idx} attempt {attempt+1} failed: {e}")
            if attempt == 2:
                raise
            rj_server.restart()

    raise RuntimeError("unreachable")


# ---------------------------------------------------------------------------
# Batched 12-layer Ramanujan call — eliminates 11 pipe round-trips per token
# ---------------------------------------------------------------------------
def run_stack(
    rj_server: RjServer,
    stack_kernel_path: str,
    work_dir: str,
    weights_dir: str,
    n_seq: int,
    hidden_flat: list,
) -> list:
    """
    Run transformer_stack.py for all 12 layers in a single JVM kernel call.
    Passes hidden.csv + params.csv + 12×14 weight CSVs directly (no symlinks).
    Returns the updated hidden state as a flat Python list.
    """
    write_flat_csv(os.path.join(work_dir, "hidden.csv"), hidden_flat)
    write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq)])

    weight_names = [
        "ln1_g", "ln1_b",
        "c_attn_w", "c_attn_b",
        "c_proj_w", "c_proj_b",
        "ln2_g", "ln2_b",
        "c_fc_w", "c_fc_b",
        "c_fc_proj_w", "c_fc_proj_b",
    ]

    csv_args = [
        os.path.join(work_dir, "hidden.csv"),
        os.path.join(work_dir, "params.csv"),
    ]
    for layer_idx in range(N_LAYER):
        p = os.path.join(weights_dir, f"l{layer_idx}")
        for name in weight_names:
            csv_args.append(f"{p}_{name}.csv")

    out_hidden_csv = os.path.join(work_dir, "out_hidden_stack.csv")
    dump_vars = {"hidden": out_hidden_csv}

    for attempt in range(3):
        try:
            rj_server.run_kernel(stack_kernel_path, csv_args, dump_vars)
            return read_flat_csv(out_hidden_csv)
        except RuntimeError as e:
            log(f"  transformer_stack attempt {attempt+1} failed: {e}")
            if attempt == 2:
                raise
            rj_server.restart()

    raise RuntimeError("unreachable")


# ---------------------------------------------------------------------------
# Pure-NumPy GPT-2 helpers (embedding lookup, layer norm, logits)
# These handle the large-vocab operations (n_vocab=50257) that require
# exact double-precision indexing, not available in Ramanujan GPU kernels.
# ---------------------------------------------------------------------------

def embed(input_ids: list, wte: np.ndarray, wpe: np.ndarray) -> np.ndarray:
    """Token + position embedding.  Returns (n_seq, 768) float64 array."""
    ids = np.array(input_ids, dtype=np.int64)
    return wte[ids] + wpe[np.arange(len(ids))]   # (n_seq, 768)


def layer_norm_np(x: np.ndarray, g: np.ndarray, b: np.ndarray,
                  eps: float = 1e-5) -> np.ndarray:
    mean = x.mean(axis=-1, keepdims=True)
    var  = x.var(axis=-1,  keepdims=True)
    return (x - mean) / np.sqrt(var + eps) * g + b


def gpt2_head(
    hidden_flat: list,
    n_seq: int,
    wte: np.ndarray,
    ln_f_g: np.ndarray,
    ln_f_b: np.ndarray,
) -> int:
    """
    Apply final layer norm to the last token position and compute logits.
    Returns the greedy next-token id (integer index).

    Input `hidden_flat` is a flat list of n_seq * 768 doubles.
    Strings/characters are never used; only integer token indexes.
    """
    hidden = np.array(hidden_flat, dtype=np.float64).reshape(n_seq, N_EMBD)
    last   = hidden[-1]                              # (768,)
    normed = layer_norm_np(last, ln_f_g, ln_f_b)    # (768,)
    logits = normed @ wte.T                          # (50257,) — integer indexes
    return int(np.argmax(logits))


# ---------------------------------------------------------------------------
# Main generation loop
# ---------------------------------------------------------------------------
def generate(
    prompt: str,
    n_tokens: int,
    weights_dir: str,
    java_home: str,
    rj_ws: str,
):
    # ── Load weights (NumPy, once) ──────────────────────────────────────────
    log("Loading weights …")
    wte    = np.load(os.path.join(weights_dir, "wte.npy")).astype(np.float64)    # (50257, 768)
    wpe    = np.load(os.path.join(weights_dir, "wpe.npy")).astype(np.float64)    # (1024,  768)
    ln_f_g = np.load(os.path.join(weights_dir, "ln_f_g.npy")).astype(np.float64) # (768,)
    ln_f_b = np.load(os.path.join(weights_dir, "ln_f_b.npy")).astype(np.float64) # (768,)
    log(f"  wte={wte.shape}  wpe={wpe.shape}  ln_f_g={ln_f_g.shape}")

    # ── BPE tokenise prompt (integer token indexes, no strings in Ramanujan) ─
    encoder = get_encoder(
        model_name="124M",
        models_dir=os.path.join(os.path.dirname(__file__), "models"),
    )
    input_ids: list = encoder.encode(prompt)
    log(f"Prompt: {repr(prompt)}")
    log(f"Input token ids ({len(input_ids)}): {input_ids}")

    assert len(input_ids) + n_tokens <= N_CTX, (
        f"Context overflow: {len(input_ids)} prompt tokens + {n_tokens} "
        f"to generate exceeds max context {N_CTX}"
    )

    # ── Locate kernels ──────────────────────────────────────────────────────
    kernel_path = os.path.join(os.path.dirname(__file__), KERNEL_NAME)
    if not os.path.exists(kernel_path):
        raise FileNotFoundError(f"Kernel not found: {kernel_path}")
    stack_kernel_path = os.path.join(os.path.dirname(__file__), STACK_KERNEL_NAME)
    if not os.path.exists(stack_kernel_path):
        raise FileNotFoundError(f"Stack kernel not found: {stack_kernel_path}")

    # ── Shared working directory (persists across tokens, cleaned up at end) ──
    work_dir = tempfile.mkdtemp(prefix="gpt2_rj_")
    log(f"Working directory: {work_dir}")

    generated: list = []   # output integer token indexes

    try:
        for step in range(n_tokens):
            n_seq   = len(input_ids) + len(generated)
            all_ids = input_ids + generated

            log(f"\n── Token {step + 1}/{n_tokens}  (n_seq={n_seq}) ──")

            # Fresh JVM per token: prevents TranslateUtil stub-string accumulation
            # from causing GC storms across the 12 × N_TOKENS kernel invocations.
            rj_server = RjServer(java_home=java_home, rj_ws=rj_ws)
            try:
                rj_server.start()

                # 1. Embedding lookup (NumPy; double-precision vocab indexing)
                t0     = time.time()
                hidden = embed(all_ids, wte, wpe)   # (n_seq, 768)
                hidden_flat = hidden.flatten().tolist()
                log(f"  embed: {time.time()-t0:.3f}s")

                # 2. All 12 transformer blocks in one JVM kernel call
                t0 = time.time()
                hidden_flat = run_stack(
                    rj_server, stack_kernel_path, work_dir,
                    weights_dir, n_seq, hidden_flat,
                )
                log(f"  stack (12 layers): {time.time()-t0:.3f}s")

                # 3. Final layer norm + greedy argmax (NumPy; double-precision)
                t0         = time.time()
                next_token = gpt2_head(hidden_flat, n_seq, wte, ln_f_g, ln_f_b)
                log(f"  head:  {time.time()-t0:.3f}s  →  token_id={next_token}")

            finally:
                rj_server.shutdown()

            generated.append(next_token)

        # ── Decode output integer token ids → text ──────────────────────────
        output_text = encoder.decode(generated)
        log(f"\nGenerated token ids: {generated}")
        log(f"Output text:\n{output_text}")
        return output_text

    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(
        description="GPT-2 124M inference on Ramanujan (GPU transformer layers)"
    )
    parser.add_argument(
        "prompt",
        help='Input text prompt (e.g. "Alan Turing theorized")',
    )
    parser.add_argument(
        "--n-tokens", type=int, default=40,
        help="Number of tokens to generate (default: 40)",
    )
    parser.add_argument(
        "--weights-dir", default="weights",
        help="Directory with weight CSV/NPY files from download_weights.py",
    )
    parser.add_argument(
        "--java-home",
        default=os.environ.get(
            "JAVA_HOME",
            "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home",
        ),
        help="JAVA_HOME for the Ramanujan JVM (must be Java 8)",
    )
    parser.add_argument(
        "--rj-ws", default=os.environ.get("RAMANUJAN_WS", "/tmp"),
        help="RAMANUJAN_WS workspace directory",
    )

    args = parser.parse_args()

    generate(
        prompt=args.prompt,
        n_tokens=args.n_tokens,
        weights_dir=args.weights_dir,
        java_home=args.java_home,
        rj_ws=args.rj_ws,
    )


if __name__ == "__main__":
    main()

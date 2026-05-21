#!/usr/bin/env python3
"""
GPT-2 124M full-generation orchestrator for Ramanujan.

Architecture:
  • Token + position embedding  ── Python / NumPy  (exact double-precision)
  • n_tokens × 12 transformer blocks + head ── Ramanujan (single JVM call)
      - all 12 layers per step run in GPU
      - head (ln_f → logits → argmax → embed_next) runs in GPU
      - wte and wpe are passed as CSV inputs so embed_next is exact GPT-2
  • Token ID decoding  ── Python / NumPy  (reads generated_tokens dump)

One JVM lifecycle for the entire generation; one IO round-trip regardless of
how many tokens are generated.

Usage:
    python run_gpt2_full.py "Alan Turing theorized" --n-tokens 40 --weights-dir weights

Prerequisites:
    Same as run_gpt2.py.  On first run, wte.csv and wpe.csv are written to
    weights-dir (one-time conversion; wte.csv is ~650 MB and takes ~60 s).
"""
import argparse
import datetime
import os
import platform
import shutil
import sys
import tempfile
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from encoder import get_encoder

# Re-use all helpers from run_gpt2 — RjServer, write_flat_csv, read_flat_csv,
# embed, log — without modification.
from run_gpt2 import (
    RjServer,
    write_flat_csv,
    read_flat_csv,
    embed,
    log,
    N_LAYER,
    N_EMBD,
    N_HEAD,
    N_FF,
    N_VOCAB,
    N_CTX,
)

FULL_STACK_KERNEL_NAME = "transformer_stack_full.py"


# ---------------------------------------------------------------------------
# One-time CSV conversion helpers
# ---------------------------------------------------------------------------

def ensure_csv(weights_dir: str, stem: str, array: np.ndarray):
    """Write <stem>.csv to weights_dir if it does not already exist."""
    path = os.path.join(weights_dir, f"{stem}.csv")
    if not os.path.exists(path):
        log(f"  Converting {stem}.npy → {stem}.csv (one-time; "
            f"{array.size} values) …")
        t0 = time.time()
        write_flat_csv(path, array.flatten())
        log(f"  Done in {time.time() - t0:.1f}s  →  {path}")
    return path


# ---------------------------------------------------------------------------
# Single-call stack runner
# ---------------------------------------------------------------------------

def run_stack_full(
    rj_server: RjServer,
    kernel_path: str,
    work_dir: str,
    weights_dir: str,
    n_seq: int,
    n_tokens: int,
    hidden_flat: list,
    timeout: int = 3600,
) -> list:
    """
    Run transformer_stack_full.py for all n_tokens generation steps in one
    JVM kernel call.

    hidden_flat must have length (n_seq + n_tokens) * N_EMBD: the first
    n_seq * N_EMBD values are the prompt embeddings; the rest are zeros and
    will be filled by embed_next_GPU_1 as the kernel generates each token.

    Returns a list of n_tokens integer-valued floats (the generated token IDs).
    """
    # ── Write per-call CSVs ──────────────────────────────────────────────────
    write_flat_csv(os.path.join(work_dir, "hidden.csv"),           hidden_flat)
    write_flat_csv(os.path.join(work_dir, "params.csv"),           [float(n_seq), float(n_tokens)])
    write_flat_csv(os.path.join(work_dir, "cur_n_seq_arr.csv"),    [float(n_seq)])
    write_flat_csv(os.path.join(work_dir, "step_arr.csv"),         [0.0])

    weight_names = [
        "ln1_g", "ln1_b",
        "c_attn_w", "c_attn_b",
        "c_proj_w", "c_proj_b",
        "ln2_g", "ln2_b",
        "c_fc_w", "c_fc_b",
        "c_fc_proj_w", "c_fc_proj_b",
    ]

    # ── Build ordered CSV argument list ─────────────────────────────────────
    csv_args = [
        os.path.join(work_dir, "hidden.csv"),
        os.path.join(work_dir, "params.csv"),
        os.path.join(work_dir, "cur_n_seq_arr.csv"),
        os.path.join(work_dir, "step_arr.csv"),
        os.path.join(weights_dir, "wte.csv"),
        os.path.join(weights_dir, "wpe.csv"),
        os.path.join(weights_dir, "ln_f_g.csv"),
        os.path.join(weights_dir, "ln_f_b.csv"),
    ]
    for layer_idx in range(N_LAYER):
        p = os.path.join(weights_dir, f"l{layer_idx}")
        for name in weight_names:
            csv_args.append(f"{p}_{name}.csv")

    out_tokens_csv = os.path.join(work_dir, "out_generated_tokens.csv")
    dump_vars = {"generated_tokens": out_tokens_csv}

    for attempt in range(3):
        try:
            rj_server.run_kernel(kernel_path, csv_args, dump_vars,
                                 timeout=timeout)
            return read_flat_csv(out_tokens_csv)
        except RuntimeError as e:
            log(f"  transformer_stack_full attempt {attempt + 1} failed: {e}")
            if attempt == 2:
                raise
            rj_server.restart()

    raise RuntimeError("unreachable")


# ---------------------------------------------------------------------------
# Main generation function
# ---------------------------------------------------------------------------

def generate_full(
    prompt: str,
    n_tokens: int,
    weights_dir: str,
    java_home: str,
    rj_ws: str,
):
    # ── Load weights (NumPy, once) ──────────────────────────────────────────
    log("Loading weights …")
    wte    = np.load(os.path.join(weights_dir, "wte.npy")).astype(np.float64)
    wpe    = np.load(os.path.join(weights_dir, "wpe.npy")).astype(np.float64)
    ln_f_g = np.load(os.path.join(weights_dir, "ln_f_g.npy")).astype(np.float64)
    ln_f_b = np.load(os.path.join(weights_dir, "ln_f_b.npy")).astype(np.float64)
    log(f"  wte={wte.shape}  wpe={wpe.shape}  ln_f_g={ln_f_g.shape}")

    # ── Ensure large weight CSVs exist (one-time conversion) ────────────────
    log("Checking large weight CSVs (wte, wpe, ln_f) …")
    ensure_csv(weights_dir, "wte",    wte)
    ensure_csv(weights_dir, "wpe",    wpe)
    ensure_csv(weights_dir, "ln_f_g", ln_f_g)
    ensure_csv(weights_dir, "ln_f_b", ln_f_b)

    # ── Tokenise prompt ──────────────────────────────────────────────────────
    encoder = get_encoder(
        model_name="124M",
        models_dir=os.path.join(os.path.dirname(__file__), "models"),
    )
    input_ids: list = encoder.encode(prompt)
    log(f"Prompt: {repr(prompt)}")
    log(f"Input token ids ({len(input_ids)}): {input_ids}")

    n_seq = len(input_ids)
    assert n_seq + n_tokens <= N_CTX, (
        f"Context overflow: {n_seq} prompt tokens + {n_tokens} to generate "
        f"exceeds max context {N_CTX}"
    )

    # ── Locate kernel ────────────────────────────────────────────────────────
    kernel_path = os.path.join(os.path.dirname(__file__), FULL_STACK_KERNEL_NAME)
    if not os.path.exists(kernel_path):
        raise FileNotFoundError(f"Kernel not found: {kernel_path}")

    # ── Build padded hidden (prompt rows + n_tokens zero rows) ───────────────
    log("Computing prompt embedding …")
    t0 = time.time()
    prompt_hidden = embed(input_ids, wte, wpe)          # (n_seq, 768)
    padded = np.zeros((n_seq + n_tokens, N_EMBD), dtype=np.float64)
    padded[:n_seq] = prompt_hidden
    hidden_flat = padded.flatten().tolist()
    log(f"  embed: {time.time() - t0:.3f}s  hidden shape: {padded.shape}")

    # ── Working directory ────────────────────────────────────────────────────
    work_dir = tempfile.mkdtemp(prefix="gpt2_rj_full_")
    log(f"Working directory: {work_dir}")

    try:
        # ── Single JVM for the entire generation ────────────────────────────
        rj_server = RjServer(java_home=java_home, rj_ws=rj_ws)
        try:
            rj_server.start()

            kernel_timeout = max(300, n_tokens * 120)
            log(f"\nRunning transformer_stack_full ({n_tokens} tokens, "
                f"timeout={kernel_timeout}s) …")
            t0 = time.time()
            token_floats = run_stack_full(
                rj_server, kernel_path, work_dir, weights_dir,
                n_seq, n_tokens, hidden_flat,
                timeout=kernel_timeout,
            )
            log(f"  kernel done: {time.time() - t0:.3f}s")

        finally:
            rj_server.shutdown()

        # ── Decode token IDs ─────────────────────────────────────────────────
        generated = [int(v) for v in token_floats[:n_tokens]]
        output_text = encoder.decode(generated)
        log(f"\nGenerated token ids: {generated}")
        log(f"Output text:\n{output_text}")
        return output_text

    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="GPT-2 124M full-generation on Ramanujan (single JVM call)"
    )
    parser.add_argument("prompt",
                        help='Input text prompt (e.g. "Alan Turing theorized")')
    parser.add_argument("--n-tokens", type=int, default=40,
                        help="Number of tokens to generate (default: 40)")
    parser.add_argument("--weights-dir", default="weights",
                        help="Directory with weight CSV/NPY files")
    parser.add_argument(
        "--java-home",
        default=os.environ.get(
            "JAVA_HOME",
            "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home",
        ),
        help="JAVA_HOME for the Ramanujan JVM (must be Java 8)",
    )
    parser.add_argument("--rj-ws", default=os.environ.get("RAMANUJAN_WS", "/tmp"),
                        help="RAMANUJAN_WS workspace directory")

    args = parser.parse_args()

    generate_full(
        prompt=args.prompt,
        n_tokens=args.n_tokens,
        weights_dir=args.weights_dir,
        java_home=args.java_home,
        rj_ws=args.rj_ws,
    )


if __name__ == "__main__":
    main()

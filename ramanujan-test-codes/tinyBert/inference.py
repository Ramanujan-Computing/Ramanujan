#!/usr/bin/env python3
"""
inference.py
============
Orchestrates TinyBERT GPU inference via the Ramanujan 'rj' runtime.

Responsibility split
--------------------
  inference_kernel.py  – static Ramanujan GPU script; weights/input come via CSV
  inference.py         – this file; writes CSVs, calls rj, parses output

How it works
------------
  1. Load weights from weights.json.
  2. Write one CSV per weight array to SCRIPT_DIR (single-row, all values
     comma-separated). These are persistent – reused across calls.
  3. Write seq_input.csv (16 token IDs + mask position) for each inference.
  4. Call:
       rj inference_kernel.py \\
          tok_emb.csv pos_emb.csv Wq.csv Wk.csv Wv.csv Wo.csv \\
          W1.csv W2.csv Wout.csv b1.csv b2.csv bout.csv seq_input.csv
     from cwd=SCRIPT_DIR so that bare filenames resolve correctly and
     the Ramanujan runtime derives array names from just the base filename
     (e.g. "tok_emb.csv" → array tok_emb[0][idx]).
  5. Pipe 128 'arr softmax_out N' queries into the query console, parse
     the probability distribution, display top-k predictions.

CSV format (how Ramanujan sees it)
-----------------------------------
  A CSV injected as the N-th argument to rj becomes a 2D Python array
  whose name is the filename with '.csv' stripped.
  We write each weight as a SINGLE-ROW CSV (all values on one line),
  so the kernel accesses them as weight_name[0][flat_index].
  seq_input.csv is also a single row of 17 values.

Usage
-----
  Interactive:
      python inference.py

  Single-shot (use '_' for the masked position):
      python inference.py "hello wor_d    !"
"""

import json
import os
import re
import shutil
import subprocess
import sys

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR   = os.path.dirname(os.path.abspath(__file__))
WEIGHTS_FILE = os.path.join(SCRIPT_DIR, "weights.json")
KERNEL_FILE  = "inference_kernel.py"      # relative; rj is run from SCRIPT_DIR

# ── rj command builder ────────────────────────────────────────────────────────
def _rj_cmd() -> list:
    """
    Return the command list that invokes the Ramanujan developer-console JAR.

    Priority:
      1. 'rj' is a real executable in PATH (rare – only if user created a wrapper).
      2. RAMANUJAN_WS env var points to the directory containing the fat JAR
         (this is what install_ramanujan.sh sets; 'rj' itself is a shell alias
         that subprocess cannot use directly).
    """
    if shutil.which("rj"):
        return ["rj"]
    ws = os.environ.get("RAMANUJAN_WS", "")
    if ws:
        jar = os.path.join(ws, "developer-console-1.0-SNAPSHOT-fat.jar")
        if os.path.exists(jar):
            return ["java", "-jar", jar]
    return ["rj"]   # will raise FileNotFoundError at runtime with a clear message

RJ_CMD = _rj_cmd()

# ── Architecture constants (must mirror tinyBertTrain.py) ─────────────────────
VOCAB = 128
SEQ   = 16

# ── Weight CSV names (base name → Ramanujan array name after stripping .csv) ──
# Order here is the order they appear in the rj command line.
WEIGHT_CSVS = [
    "tok_emb.csv",   # tok_emb[0][0..4095]
    "pos_emb.csv",   # pos_emb[0][0..511]
    "Wq.csv",        # Wq[0][0..1023]
    "Wk.csv",        # Wk[0][0..1023]
    "Wv.csv",        # Wv[0][0..1023]
    "Wo.csv",        # Wo[0][0..1023]
    "W1.csv",        # W1[0][0..2047]
    "W2.csv",        # W2[0][0..2047]
    "Wout.csv",      # Wout[0][0..4095]
    "b1.csv",        # b1[0][0..63]
    "b2.csv",        # b2[0][0..31]
    "bout.csv",      # bout[0][0..127]
]
INPUT_CSV = "seq_input.csv"   # seq_input[0][0..15] = token IDs, [0][16] = mask_pos


# =============================================================================
# CSV helpers
# =============================================================================

def _single_row_csv(values: list) -> str:
    """Encode a list of numbers as a single-row CSV (all values on one line)."""
    return ",".join(f"{float(v):.8f}" for v in values)


def write_weight_csvs(weights: dict, force: bool = False) -> None:
    """
    Write one CSV per weight array to SCRIPT_DIR.
    Each CSV is a single row with all values comma-separated.
    Only writes if the file is missing (or force=True) since weights don't
    change between inference calls.
    """
    for csv_name in WEIGHT_CSVS:
        path = os.path.join(SCRIPT_DIR, csv_name)
        if not force and os.path.exists(path):
            continue
        key = csv_name[:-4]           # strip ".csv"  → matches weights.json key
        with open(path, "w") as f:
            f.write(_single_row_csv(weights[key]))
        print(f"  wrote {csv_name}  ({len(weights[key])} values)")


def write_input_csv(tok_ids: list, mask_pos: int) -> None:
    """
    Write seq_input.csv: a single row with 17 values.
      columns 0-15 = token IDs
      column  16   = mask position
    """
    row = [float(t) for t in tok_ids] + [float(mask_pos)]
    path = os.path.join(SCRIPT_DIR, INPUT_CSV)
    with open(path, "w") as f:
        f.write(",".join(f"{int(v)}" for v in row))


# =============================================================================
# rj interaction
# =============================================================================

def _build_queries() -> str:
    """128 'arr softmax_out N' queries + exit, piped into the query console."""
    lines = [f"arr softmax_out {i}" for i in range(VOCAB)]
    lines.append("exit")
    return "\n".join(lines) + "\n"


def _parse_probs(stdout: str) -> list:
    """
    Parse lines like:  softmax_out[42] = 0.03812741
    Returns a VOCAB-length list of floats.
    """
    probs = [0.0] * VOCAB
    pat   = re.compile(r'softmax_out\[(\d+)\]\s*=\s*([\-\d.eE+]+)')
    for m in pat.finditer(stdout):
        idx = int(m.group(1))
        if 0 <= idx < VOCAB:
            try:
                probs[idx] = float(m.group(2))
            except ValueError:
                pass
    return probs


def run_inference(tok_ids: list, mask_pos: int) -> list:
    """
    Write seq_input.csv then call rj with kernel + all CSV files.
    Returns the VOCAB-length softmax probability list.

    rj command (run from SCRIPT_DIR so bare filenames resolve):
      rj inference_kernel.py tok_emb.csv pos_emb.csv ... seq_input.csv
    """
    write_input_csv(tok_ids, mask_pos)

    cmd = RJ_CMD + [KERNEL_FILE] + WEIGHT_CSVS + [INPUT_CSV]
    proc = subprocess.run(
        cmd,
        input=_build_queries(),
        capture_output=True,
        text=True,
        timeout=120,
        cwd=SCRIPT_DIR,       # bare filenames resolve here; array names from basenames
    )

    if proc.returncode not in (0, None):
        err = (proc.stderr or "").strip()[:400]
        if err:
            print(f"  [rj stderr] {err}", file=sys.stderr)

    return _parse_probs(proc.stdout)


# =============================================================================
# Display helpers
# =============================================================================

def load_weights(path: str = WEIGHTS_FILE):
    with open(path) as f:
        data = json.load(f)
    return data["weights"], data.get("arch", {})


def tokenize(text: str) -> list:
    ids = [ord(c) % VOCAB for c in text[:SEQ]]
    while len(ids) < SEQ:
        ids.append(32)       # pad with space
    return ids


def show_prediction(text: str, mask_pos: int, probs: list, top_k: int = 5) -> None:
    display           = list(text[:SEQ].ljust(SEQ))
    display[mask_pos] = "▓"

    ranked    = sorted(enumerate(probs), key=lambda kv: -kv[1])
    top_ids   = [i for i, _ in ranked[:top_k]]
    top_probs = [p for _, p in ranked[:top_k]]

    print()
    print(f"  Input    : {''.join(display)}")
    orig = text[mask_pos] if mask_pos < len(text) else "·"
    print(f"  Mask pos : {mask_pos}  (original char: {repr(orig)})")
    print()
    print(f"  {'Rank':<5}  {'Char':<8}  {'ID':>3}  {'Prob':>8}   Bar")
    print("  " + "─" * 52)
    for rank, (tid, prob) in enumerate(zip(top_ids, top_probs), 1):
        ch  = repr(chr(tid)) if 32 <= tid < 127 else f"<{tid:03d}>"
        bar = "█" * max(1, int(prob * 30))
        print(f"  #{rank:<4}  {ch:<8}  {tid:3d}  {prob * 100:7.2f}%   {bar}")

    best_ch = chr(top_ids[0]) if 32 <= top_ids[0] < 127 else "?"
    recon   = list(text[:SEQ].ljust(SEQ))
    recon[mask_pos] = best_ch
    print(f"\n  Best guess : {''.join(recon)}")
    print(f"  Confidence : {top_probs[0] * 100:.1f}%")

    if sum(1 for p in probs if p > 1e-6) == 0:
        print("\n  [WARN] All probabilities zero – run: python extract_weights.py")


# =============================================================================
# Main
# =============================================================================

BANNER = """\
╔══════════════════════════════════════════════════════════╗
║  TinyBERT · Masked Character Prediction                 ║
║  inference_kernel.py runs on phone GPU via 'rj'         ║
║  Weights loaded from CSV files in this directory        ║
╚══════════════════════════════════════════════════════════╝
Use '_' to mark the masked position  (e.g.  hello wor_d   )
Type 'q' to quit.
"""


def main() -> None:
    # ── Pre-flight ────────────────────────────────────────────────────────────
    if RJ_CMD == ["rj"] and shutil.which("rj") is None:
        print("[ERROR] Cannot locate the Ramanujan runtime.")
        print("  Either RAMANUJAN_WS is not set or the JAR is missing.")
        print("  Install:  bash install_ramanujan.sh")
        print("  Reload:   source ~/.zshrc  (or ~/.bashrc)")
        sys.exit(1)

    if not os.path.exists(WEIGHTS_FILE):
        print(f"[ERROR] weights.json not found.\n  Run: python extract_weights.py")
        sys.exit(1)

    kernel_path = os.path.join(SCRIPT_DIR, KERNEL_FILE)
    if not os.path.exists(kernel_path):
        print(f"[ERROR] {KERNEL_FILE} not found in {SCRIPT_DIR}")
        sys.exit(1)

    # ── Load weights and write CSVs (only when missing) ───────────────────────
    print("Loading weights … ", end="", flush=True)
    weights, arch = load_weights()
    print("OK")

    missing = [c for c in WEIGHT_CSVS if not os.path.exists(os.path.join(SCRIPT_DIR, c))]
    if missing:
        print(f"Writing {len(missing)} weight CSV(s) to {SCRIPT_DIR}/")
        write_weight_csvs(weights)
    else:
        print(f"Weight CSVs present ({len(WEIGHT_CSVS)} files).")

    # ── Single-shot CLI mode ──────────────────────────────────────────────────
    args = sys.argv[1:]
    if args and args[0] != "--refresh":
        raw      = args[0]
        mask_pos = raw.index("_") if "_" in raw else (
            int(args[1]) if len(args) > 1 else 8)
        text     = raw.replace("_", " ")
        tok_ids  = tokenize(text)
        print(f"\nRunning GPU forward pass (mask_pos={mask_pos}) …")
        probs = run_inference(tok_ids, mask_pos)
        show_prediction(text, mask_pos, probs)
        return

    # --refresh: force-rewrite all weight CSVs
    if args and args[0] == "--refresh":
        print("Refreshing all weight CSVs …")
        write_weight_csvs(weights, force=True)
        print("Done.")
        return

    # ── Interactive loop ──────────────────────────────────────────────────────
    print()
    print(BANNER)

    while True:
        try:
            raw = input("Input> ").rstrip("\n").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nGoodbye.")
            break

        if raw.lower() in ("q", "quit", "exit"):
            print("Goodbye.")
            break
        if not raw:
            continue

        mask_pos = raw.index("_") if "_" in raw else 8
        try:
            if "_" not in raw:
                mask_pos = int(input("  Mask position [0-15]: ").strip())
        except (ValueError, EOFError):
            mask_pos = 8

        text    = raw.replace("_", " ")
        tok_ids = tokenize(text)
        print(f"  Running GPU forward pass (mask_pos={mask_pos}) …", flush=True)
        probs = run_inference(tok_ids, mask_pos)
        show_prediction(text, mask_pos, probs)
        print()


if __name__ == "__main__":
    main()

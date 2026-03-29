#!/usr/bin/env python3
"""
extract_weights.py
==================
Trains TinyBERT via the Ramanujan 'rj' runtime (backed by ExecuteInline.java)
and then harvests every trained weight array from the interactive query console,
saving the result as weights.json ready for inference.py.

How it works
------------
  rj <script>               →  compiles + executes via ExecuteInline.java
                               (GPU kernels run on the phone/device GPU)
  Query console (stdin)     →  'arr <name> <idx>' and 'var <name>'
  Parsed stdout             →  weights.json

The query console is provided by ExecutorImpl.startQueryConsole():
  Input  format  : arr tok_emb 0        → reads index 0 of array tok_emb
  Output format  : tok_emb[0] = 0.0821  → printed to stdout

Usage
-----
  python extract_weights.py                                    # defaults
  python extract_weights.py path/to/tinyBertTrain.py out.json
"""

import os
import re
import sys
import json
import shutil
import subprocess

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR   = os.path.dirname(os.path.abspath(__file__))
TRAIN_SCRIPT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(SCRIPT_DIR, "tinyBertTrain.py")
OUTPUT_FILE  = sys.argv[2] if len(sys.argv) > 2 else os.path.join(SCRIPT_DIR, "weights.json")

# ── Architecture constants (must mirror tinyBertTrain.py) ─────────────────────
#    VOCAB=128  SEQ=16  DM=32  DFF=64  DH=8  NH=4
WEIGHT_ARRAYS = {
    "tok_emb": 128 * 32,   # 4096 – token embedding table
    "pos_emb":  16 * 32,   #  512 – positional embedding table
    "Wq":       32 * 32,   # 1024 – query projection
    "Wk":       32 * 32,   # 1024 – key projection
    "Wv":       32 * 32,   # 1024 – value projection
    "Wo":       32 * 32,   # 1024 – output projection
    "W1":       32 * 64,   # 2048 – FFN layer 1  (DM → DFF)
    "W2":       64 * 32,   # 2048 – FFN layer 2  (DFF → DM)
    "Wout":     32 * 128,  # 4096 – classifier head  (DM → VOCAB)
    "b1":       64,        #   64 – FFN bias 1
    "b2":       32,        #   32 – FFN bias 2
    "bout":     128,       #  128 – classifier bias
}
EXTRA_VARS = ["avg_loss"]

ARCH_META = {
    "VOCAB": 128, "SEQ": 16, "DM": 32, "DFF": 64, "DH": 8, "NH": 4,
}


# ── rj command builder ────────────────────────────────────────────────────────
def _find_java() -> str:
    """
    Locate the correct Java binary.  On Apple Silicon Macs the default
    /usr/bin/java may run under Rosetta (x86_64), which cannot load an
    arm64 native library.  Prefer JAVA_HOME, then the macOS java_home
    helper (requesting arm64), then fall back to PATH lookup.
    """
    # 1. Honour JAVA_HOME if the user set it
    jh = os.environ.get("JAVA_HOME", "")
    if jh:
        candidate = os.path.join(jh, "bin", "java")
        if os.path.isfile(candidate):
            return candidate

    # 2. macOS: ask java_home for an arm64 JDK
    if sys.platform == "darwin":
        try:
            jh = subprocess.check_output(
                ["/usr/libexec/java_home", "-a", "arm64"],
                text=True, stderr=subprocess.DEVNULL
            ).strip()
            candidate = os.path.join(jh, "bin", "java")
            if os.path.isfile(candidate):
                return candidate
        except (subprocess.CalledProcessError, FileNotFoundError):
            pass

    # 3. Plain PATH lookup
    return "java"


def _rj_cmd() -> list:
    """
    'rj' is installed as a shell alias by install_ramanujan.sh, which means
    subprocess cannot use it directly. Fall back to building the java command
    from RAMANUJAN_WS if the alias is not available as a real binary.
    """
    if shutil.which("rj"):
        return ["rj"]
    ws = os.environ.get("RAMANUJAN_WS", "")
    if ws:
        jar = os.path.join(ws, "developer-console-1.0-SNAPSHOT-fat.jar")
        if os.path.exists(jar):
            return [_find_java(), "-jar", jar]
    return ["rj"]   # will raise FileNotFoundError with a clear message

RJ_CMD = _rj_cmd()


# ── Helpers ─────────────────────────────────────────────────────────────────────

def check_rj() -> None:
    if RJ_CMD == ["rj"] and shutil.which("rj") is None:
        print("[ERROR] Cannot locate the Ramanujan runtime.")
        print("  Either RAMANUJAN_WS is not set or the JAR is missing.")
        print("  Run the installer:  bash install_ramanujan.sh")
        print("  Then reload shell:  source ~/.zshrc   (or ~/.bashrc)")
        sys.exit(1)


def build_queries() -> str:
    """
    Build the full stdin payload for the query console:
      • one 'arr <name> <idx>' line per weight element
      • one 'var <name>'      line per scalar
      • 'exit' to close the console
    """
    lines = []
    for name, size in WEIGHT_ARRAYS.items():
        for i in range(size):
            lines.append(f"arr {name} {i}")
    for v in EXTRA_VARS:
        lines.append(f"var {v}")
    lines.append("exit")
    return "\n".join(lines) + "\n"


def parse_output(text: str) -> dict:
    """
    Extract weight values from the query-console portion of rj's stdout.

    Query console response formats (from ExecutorImpl.startQueryConsole):
      arr query  →  tok_emb[0] = 0.08231
      var query  →  avg_loss = 4.831

    Both appear on lines that also start with '> ' (the prompt), so we use
    a pattern that finds the value anywhere in the line.
    """
    weights = {name: [0.0] * size for name, size in WEIGHT_ARRAYS.items()}
    scalars = {}

    # Array:  tok_emb[0] = 0.08231
    arr_re = re.compile(r'\b(\w+)\[(\d+)\]\s*=\s*([\-\d.eE+]+)')
    for m in arr_re.finditer(text):
        name = m.group(1)
        idx  = int(m.group(2))
        val  = m.group(3)
        if name in weights and idx < len(weights[name]):
            try:
                weights[name][idx] = float(val)
            except ValueError:
                pass

    # Scalars:  avg_loss = 4.831
    for v in EXTRA_VARS:
        sca_re = re.compile(rf'\b{re.escape(v)}\s*=\s*([\-\d.eE+]+)')
        m = sca_re.search(text)
        if m:
            try:
                scalars[v] = float(m.group(1))
            except ValueError:
                scalars[v] = m.group(1)

    return {"weights": weights, "scalars": scalars, "arch": ARCH_META}


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    check_rj()

    if not os.path.exists(TRAIN_SCRIPT):
        print(f"[ERROR] Training script not found: {TRAIN_SCRIPT}")
        sys.exit(1)

    total_queries = sum(WEIGHT_ARRAYS.values()) + len(EXTRA_VARS)
    print("=" * 62)
    print("  TinyBERT Weight Extractor  (Ramanujan → weights.json)")
    print("=" * 62)
    print(f"  Training script : {TRAIN_SCRIPT}")
    print(f"  Output file     : {OUTPUT_FILE}")
    print(f"  Weight elements : {total_queries:,}")
    print()
    print("[1/3] Sending training script to rj (ExecuteInline.java) …")
    print("      Training will run on device GPU (12 cycles: 3 epochs × 4 steps).")
    print("      Output appears when training completes.\n")

    stdin_data = build_queries()

    try:
        proc = subprocess.run(
            RJ_CMD + [TRAIN_SCRIPT],
            input=stdin_data,
            capture_output=True,
            text=True,
            timeout=900,          # 15-minute ceiling
        )
    except FileNotFoundError:
        print("[ERROR] 'rj' binary could not be executed. Is the alias active?")
        sys.exit(1)
    except subprocess.TimeoutExpired:
        print("[ERROR] Training timed out (15 min). Try increasing timeout.")
        sys.exit(1)

    stdout = proc.stdout

    # ── Show training logs (everything before the query console banner) ────
    console_marker = "--- Query Console ---"
    split_at = stdout.find(console_marker)
    if split_at >= 0:
        print(stdout[:split_at].rstrip())
    else:
        print(stdout.rstrip())

    if proc.returncode not in (0, None):
        print(f"\n[WARN] rj exited with code {proc.returncode}")
        if proc.stderr:
            print(proc.stderr[:2000])

    # ── Parse weights ──────────────────────────────────────────────────────
    print("\n[2/3] Parsing weight values from query console output …")
    result = parse_output(stdout)

    nonzero = sum(1 for arr in result["weights"].values() for v in arr if v != 0.0)
    total   = sum(WEIGHT_ARRAYS.values())
    print(f"      Non-zero elements found: {nonzero:,} / {total:,}")

    if nonzero == 0:
        print("\n[WARN] No weights were extracted. Possible causes:")
        print("  • Training failed silently (check rj output above)")
        print("  • Array index mismatch – verify WEIGHT_ARRAYS sizes")

    # ── Save ───────────────────────────────────────────────────────────────
    print(f"\n[3/3] Saving → {OUTPUT_FILE}")
    with open(OUTPUT_FILE, "w") as f:
        json.dump(result, f, separators=(",", ":"))

    size_kb   = os.path.getsize(OUTPUT_FILE) / 1024
    avg_loss  = result["scalars"].get("avg_loss", "N/A")
    print(f"      File size  : {size_kb:.1f} KB")
    print(f"      avg_loss   : {avg_loss}")
    print("\n[OK] Done. Run inference.py to use the trained model.")


if __name__ == "__main__":
    main()

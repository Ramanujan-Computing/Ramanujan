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
import time
import signal
import shutil
import tempfile
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
                return [_find_java(),
                        "-Xmx1g",
                        "-XX:+UseG1GC",
                        "-XX:ReservedCodeCacheSize=512m",
                        "-XX:+UseCodeCacheFlushing",
                        "-jar", jar]
    return ["rj"]   # will raise FileNotFoundError with a clear message

RJ_CMD = _rj_cmd()

CORPUS_FILE       = os.path.join(SCRIPT_DIR, "corpus.txt")
_CORPUS_BEGIN_TAG = "# [CORPUS_DATA_BEGIN]"
_CORPUS_END_TAG   = "# [CORPUS_DATA_END]"
_WEIGHT_BEGIN_TAG = "# [WEIGHT_INIT_BEGIN]"
_WEIGHT_END_TAG   = "# [WEIGHT_INIT_END]"


# ── Corpus-based dataset injection ───────────────────────────────────────────

def _build_corpus_block(corpus_path: str) -> str:
    """
    Read corpus_path, extract printable-ASCII characters, and inject a
    256-sample × 16-token dataset into the training script.

    Layout:
      256 samples × 16 tokens = 4096 tokens
      Windows are stride-spread across the FULL corpus so all available
      text is seen rather than just the first 4096 characters.
      stride = (corpus_len - SEQ_LEN) // (N_SAMPLES - 1)
      mask_positions[s] cycles through 1-14 (never masks boundary tokens).
    """
    N_SAMPLES = 256
    SEQ_LEN   = 16
    needed    = N_SAMPLES * SEQ_LEN   # flat dataset array size (4096)

    with open(corpus_path, "r", encoding="utf-8", errors="replace") as f:
        raw = f.read()

    # Keep only printable ASCII (space … tilde, codes 32-126)
    chars = [ord(c) for c in raw if 32 <= ord(c) <= 126]
    # Ensure the corpus is long enough to spread N_SAMPLES windows with stride≥1
    while len(chars) < SEQ_LEN * 2:
        chars = chars + chars

    # Stride that spreads N_SAMPLES windows evenly across the full corpus
    stride = max(1, (len(chars) - SEQ_LEN) // (N_SAMPLES - 1))
    # Pad in the unlikely case the last window goes out of bounds
    max_needed = (N_SAMPLES - 1) * stride + SEQ_LEN
    while len(chars) < max_needed:
        chars = chars + chars

    lines = [
        _CORPUS_BEGIN_TAG + " ← auto-generated from corpus.txt; edit that file, not this block",
        f"dataset        = [0 for _ in range({needed})]",
        f"labels         = [0 for _ in range({N_SAMPLES})]",
        f"mask_positions = [0 for _ in range({N_SAMPLES})]",
    ]
    # Lay dataset flat: row s = chars[s*stride : s*stride+SEQ_LEN]
    for s in range(N_SAMPLES):
        base = s * stride
        for t in range(SEQ_LEN):
            lines.append(f"dataset[{s * SEQ_LEN + t}] = {chars[base + t]}")
    for s in range(N_SAMPLES):
        mp = (s % 14) + 1   # cycle positions 1-14, never masks boundary tokens
        lines.append(f"mask_positions[{s}] = {mp}")
        lines.append(f"labels[{s}] = {chars[s * stride + mp]}")
    lines.append(_CORPUS_END_TAG)
    return "\n".join(lines)


def _build_weight_init_block() -> str:
    """
    Break the zero-initialisation dead zone with minimal extra lines:
      • tok_emb  – Xavier-uniform individual element assignments (~4 096 lines)
      • b1       – small positive individual assignments (64 lines of b1[i]=0.1)
                   so ReLU(b1)>0 activates W2/W1 gradients from step 1
      • everything else – [0 for _ in range(N)] (Ramanujan requires init=0)
    Total ≈ 4 175 lines, safely under the JVM CodeCache limit.
    """
    import random, math
    rng   = random.Random(42)
    limit = math.sqrt(6.0 / (128 + 32))   # Xavier for VOCAB=128 × DM=32

    lines = [_WEIGHT_BEGIN_TAG + " ← auto-generated; edit extract_weights.py, not this block"]

    # tok_emb: declaration then individual float assignments
    lines.append("tok_emb  = [0 for _ in range(4096)]")
    for i in range(4096):
        lines.append(f"tok_emb[{i}] = {round(rng.uniform(-limit, limit), 6)}")

    # Other weight matrices: zero comprehensions (unchanged at this stage)
    lines.append("pos_emb  = [0 for _ in range(512)]")
    lines.append("Wq       = [0 for _ in range(1024)]")
    lines.append("Wk       = [0 for _ in range(1024)]")
    lines.append("Wv       = [0 for _ in range(1024)]")
    lines.append("Wo       = [0 for _ in range(1024)]")
    lines.append("W1       = [0 for _ in range(2048)]")
    lines.append("W2       = [0 for _ in range(2048)]")
    lines.append("Wout     = [0 for _ in range(4096)]")

    # b1 positive: declaration then individual assignments
    # ff_h = ReLU(W1@attn + b1) = ReLU(b1) = 0.1 > 0 before W1 learns
    lines.append("b1       = [0 for _ in range(64)]")
    for i in range(64):
        lines.append(f"b1[{i}] = 0.1")

    lines.append("b2       = [0 for _ in range(32)]")
    lines.append("bout     = [0 for _ in range(128)]")

    lines.append(_WEIGHT_END_TAG)
    return "\n".join(lines)


def _prepare_script(base_script: str) -> tuple:
    """
    Inject corpus data and Xavier weight initialisation into a temp copy of
    the training script.  Returns (tmp_path, used_corpus).
    If neither injection block is found, returns (base_script, False).
    """
    with open(base_script, "r", encoding="utf-8") as f:
        content = f.read()

    made_changes = False
    used_corpus  = False

    # ── Corpus data injection ──────────────────────────────────────────────
    cb_begin = content.find(_CORPUS_BEGIN_TAG)
    cb_end   = content.find(_CORPUS_END_TAG)
    if cb_begin >= 0 and cb_end > cb_begin and os.path.exists(CORPUS_FILE):
        data_block  = _build_corpus_block(CORPUS_FILE)
        content     = (content[:cb_begin]
                       + data_block + "\n"
                       + content[cb_end + len(_CORPUS_END_TAG):])
        used_corpus  = True
        made_changes = True

    # ── Weight init injection skipped ─────────────────────────────────────
    # tinyBertTrain.py already has init_array() (LCG-based Xavier) which
    # initialises all weight matrices after the zero-fill declarations.
    # Injecting 4 000+ individual element assignments here would overflow
    # the JVM CodeCache, so we deliberately leave the [WEIGHT_INIT] block
    # untouched and let the training script handle initialisation itself.

    if not made_changes:
        return base_script, False

    tmp = tempfile.NamedTemporaryFile(
        mode="w", suffix=".py", prefix="tinyBertTrain_corpus_",
        dir=SCRIPT_DIR, delete=False, encoding="utf-8",
    )
    tmp.write(content)
    tmp.close()
    return tmp.name, used_corpus


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

    # ── Inject real corpus data into a temp copy of the training script ──────
    effective_script, used_corpus = _prepare_script(TRAIN_SCRIPT)
    corpus_note = ""
    if used_corpus:
        char_count = len([c for c in open(CORPUS_FILE, encoding="utf-8",
                          errors="replace").read() if 32 <= ord(c) <= 126])
        corpus_note = f" (corpus.txt → {char_count:,} printable chars → 256 × 16-char windows)"

    total_queries = sum(WEIGHT_ARRAYS.values()) + len(EXTRA_VARS)
    print("=" * 62)
    print("  TinyBERT Weight Extractor  (Ramanujan → weights.json)")
    print("=" * 62)
    print(f"  Training script : {TRAIN_SCRIPT}")
    print(f"  Output file     : {OUTPUT_FILE}")
    print(f"  Weight elements : {total_queries:,}")
    if used_corpus:
        print(f"  Dataset         : real-text windows{corpus_note}")
    else:
        print(f"  Dataset         : constant-character fallback (corpus.txt not found)")
    print()
    print("[1/3] Sending training script to rj …")
    print("      JVM will write output to disk; Python polls until done.\n")

    stdin_data = build_queries()

    # Use PID-unique file names so concurrent extract_weights.py runs never
    # share or delete each other's temp files.
    _pid = os.getpid()
    _query_file  = os.path.join(SCRIPT_DIR, f".rj_queries_{_pid}.txt")
    _output_file = os.path.join(SCRIPT_DIR, f".rj_output_{_pid}.txt")
    with open(_query_file, "w", encoding="utf-8") as qf:
        qf.write(stdin_data)

    _tmp_created = (effective_script != TRAIN_SCRIPT)
    try:
        proc = subprocess.Popen(
            RJ_CMD + [effective_script],
            stdin=open(_query_file, "r"),
            stdout=open(_output_file, "w"),
            stderr=subprocess.STDOUT,   # merge stderr into the same output file
            start_new_session=True,     # own session → immune to terminal Ctrl-C
        )
    except FileNotFoundError:
        print("[ERROR] 'rj' binary could not be executed. Is the alias active?")
        sys.exit(1)
    # Write a meta file so watch_train.sh can estimate cycle progress
    _meta_file = os.path.join(SCRIPT_DIR, "train_meta.json")
    import json as _json
    _cycles = 30   # must match threadParallelismCycle(..., 30) in tinyBertTrain.py
    _approx_dags_total = 990   # empirical from the 256-sample / 30-cycle run
    with open(_meta_file, "w") as _mf:
        _json.dump({"cycles": _cycles, "n_samples": 256,
                    "approx_dags_total": _approx_dags_total,
                    "pid": proc.pid}, _mf)
    # Ignore SIGINT in Python while waiting so the tool’s Ctrl-C interrupts
    # do not raise KeyboardInterrupt and abort the training poll loop.
    _old_sigint = signal.signal(signal.SIGINT, signal.SIG_IGN)
    print(f"      JVM pid={proc.pid}  log → {_output_file}", flush=True)
    try:
        while True:
            rc = proc.poll()
            if rc is not None:
                break
            time.sleep(10)
    except KeyboardInterrupt:
        pass
    finally:
        signal.signal(signal.SIGINT, _old_sigint)

    return_code = proc.returncode
    print()

    with open(_output_file, "r", encoding="utf-8", errors="replace") as of:
        stdout = of.read()

    # Keep the output log as train_log_latest.txt for inspection; remove only
    # the ephemeral query file and the pid-tagged log copy.
    _log_archive = os.path.join(SCRIPT_DIR, "train_log_latest.txt")
    try:
        os.replace(_output_file, _log_archive)
        print(f"      Log saved → {_log_archive}")
    except OSError:
        pass
    try:
        os.unlink(_query_file)
    except OSError:
        pass

    # ── Show training logs (everything before the query console banner) ────
    console_marker = "--- Query Console ---"
    split_at = stdout.find(console_marker)
    if split_at >= 0:
        print(stdout[:split_at].rstrip())
    else:
        print(stdout.rstrip())

    if return_code not in (0, None):
        print(f"\n[WARN] rj exited with code {return_code}")

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

    # ── Clean up temp patched script ───────────────────────────────────────
    if _tmp_created and effective_script != TRAIN_SCRIPT:
        try:
            os.unlink(effective_script)
        except OSError:
            pass


if __name__ == "__main__":
    main()

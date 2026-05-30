#!/usr/bin/env python3
"""
Sharded Phi-3 orchestrator for Ramanujan.

Drives `phi3_transformer_stack_sharded.py` through a serialized sequence of
shard `run`s on a single local JVM server. Persistent arrays (h_state,
generated_tokens, step_arr, cur_n_seq_arr, per-layer K/V caches) live as flat
1-D CSVs in a state directory; after each shard the orchestrator pulls a
sparse `idx,val` diff from the JVM (new `dump_diff` command) and merges it
back into the relevant maintained CSVs.

Layer-to-shard mapping is fixed: shard `s` (s∈0..3) owns global layers
`s*8 .. s*8+7`, mapped onto the kernel's generic `slot{0..7}_*` arrays via
a per-call symlink farm. The kernel itself knows nothing about global
layer indices.

Phases (`shard_kind` in params[2]):
  0 → PREFILL   (4 shards × 8 layers)
  1 → HEAD      (final norm + logits + argmax + store + embed_next + inc)
  2 → DECODE    (4 shards × 8 layers, per token)

Sequence per generated token after prefill:
  prefill: shard 0..3 → head
  for t in 1..n_tokens-1: decode shard 0..3 → head
"""

import argparse
import datetime
import os
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


# ── Hardcoded dimensions (Phi-3 mini 4k) ──────────────────────────────────
N_LAYERS         = 32
N_SHARDS         = 4
LAYERS_PER_SHARD = 8
HIDDEN           = 3072
MAX_SEQ          = 1024
KV_FLAT_SIZE     = MAX_SEQ * HIDDEN          # 3,145,728
H_STATE_SIZE     = MAX_SEQ * HIDDEN          # 3,145,728


# ─────────────────── Persistent Ramanujan JVM server client ───────────────
class RjServer:
    """Same shape as run_phi3_4bit.py's RjServer but with a dump_diff helper."""

    def __init__(self, java_home, rj_ws):
        self.java_home = java_home
        self.rj_ws = rj_ws
        self.proc = None

    def start(self, timeout=60):
        java_bin = os.path.join(self.java_home, "bin", "java")
        rj_jar = os.environ.get(
            "RAMANUJAN_FAT_JAR",
            os.path.expanduser("~/Desktop/ws/developer-console-1.0-SNAPSHOT-fat.jar"),
        )
        cmd = [java_bin, "-Xmx14g", "-XX:+UseG1GC", "-jar", rj_jar, "server"]
        log(f"Starting Ramanujan JVM server ...")
        env = os.environ.copy()
        env["JAVA_HOME"] = self.java_home
        env["RAMANUJAN_WS"] = self.rj_ws

        self.proc = subprocess.Popen(
            cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, bufsize=1, env=env,
        )
        threading.Thread(target=self._drain_stderr, daemon=True).start()

        deadline = time.time() + timeout
        while time.time() < deadline:
            line = self.proc.stdout.readline()
            if not line:
                raise RuntimeError("JVM exited before SERVER_READY")
            if line.rstrip() == "SERVER_READY":
                log("JVM server ready")
                return
        raise RuntimeError("Timeout waiting for SERVER_READY")

    def _drain_stderr(self):
        for line in self.proc.stderr:
            sys.stderr.write("[JVM] " + line)

    def _readline(self, deadline):
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                return None
            line = self.proc.stdout.readline()
            if line == "":
                return None
            return line

    def run(self, kernel_py, csv_args, timeout=900):
        line = "run " + " ".join([kernel_py] + csv_args) + "\n"
        self.proc.stdin.write(line)
        self.proc.stdin.flush()
        deadline = time.time() + timeout
        while True:
            l = self._readline(deadline)
            if l is None:
                raise RuntimeError(f"KERNEL_TIMEOUT after {timeout}s")
            l = l.rstrip()
            if l == "KERNEL_DONE":
                return
            if l.startswith("KERNEL_ERROR"):
                raise RuntimeError(f"KERNEL_ERROR: {l}")

    def dump_diff(self, name, start_idx, end_idx_inclusive, out_path, timeout=120):
        self.proc.stdin.write(f"dump_diff {name} {start_idx} {end_idx_inclusive} {out_path}\n")
        self.proc.stdin.flush()
        deadline = time.time() + timeout
        while True:
            l = self._readline(deadline)
            if l is None:
                raise RuntimeError(f"dump_diff timeout for {name}")
            l = l.rstrip()
            if l.startswith("Dumped diff"):
                return
            if l.startswith("Array not found") or l.startswith("Error"):
                raise RuntimeError(f"dump_diff failed for {name}: {l}")

    def shutdown(self):
        if not self.proc:
            return
        try:
            self.proc.stdin.write("quit\n")
            self.proc.stdin.flush()
            self.proc.wait(timeout=10)
        except Exception:
            self.proc.kill()
        log("JVM server shut down")


# ──────────────────────────── CSV helpers ─────────────────────────────────
def write_flat_csv_np(path, arr):
    """Write a numpy 1-D array as a single-line CSV (no per-element newlines).

    Single-row CSV form makes the Ramanujan loader declare the array as 1-D.
    """
    # %.8g is what run_phi3_4bit.py uses; preserves enough precision for f32.
    text = ",".join(f"{v:.8g}" for v in arr.tolist())
    with open(path, "w") as f:
        f.write(text + "\n")


def write_scalar_csv(path, value):
    with open(path, "w") as f:
        f.write(f"{value}\n")


def read_diff_file(path):
    """Parse a dump_diff output file. Returns list of (key, val_str) tuples.

    Slot K/V caches and h_state are 1-D in the kernel, so keys are decimal
    integer strings. We keep the value as a string and let the caller cast.
    """
    if not os.path.exists(path):
        return []
    out = []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            idx_str, val_str = line.split(",", 1)
            out.append((idx_str, val_str))
    return out


# ──────────────────────────── State manager ───────────────────────────────
class State:
    """In-memory persistent arrays + helpers to spill them to disk on demand.

    Held as numpy float32 to keep the working set bounded (~12 MB per cache).
    """

    def __init__(self, state_dir, n_seq, n_tokens):
        self.dir = state_dir
        os.makedirs(self.dir, exist_ok=True)

        self.n_seq_init = n_seq
        self.n_tokens   = n_tokens

        self.h_state          = np.zeros(H_STATE_SIZE, dtype=np.float32)
        self.generated_tokens = np.zeros(MAX_SEQ, dtype=np.float32)
        self.cur_n_seq        = float(n_seq)
        self.step             = 0.0
        # Per-global-layer K/V caches.
        self.k_cache = [np.zeros(KV_FLAT_SIZE, dtype=np.float32) for _ in range(N_LAYERS)]
        self.v_cache = [np.zeros(KV_FLAT_SIZE, dtype=np.float32) for _ in range(N_LAYERS)]

    # ── Spill on-disk CSVs for the next shard call ─────────────────────
    def write_h_state_csv(self, path):    write_flat_csv_np(path, self.h_state)
    def write_gen_tokens_csv(self, path): write_flat_csv_np(path, self.generated_tokens)
    def write_cur_n_seq_csv(self, path):  write_scalar_csv(path, f"{self.cur_n_seq:.1f}")
    def write_step_csv(self, path):       write_scalar_csv(path, f"{self.step:.1f}")
    def write_k_cache_csv(self, layer_i, path): write_flat_csv_np(path, self.k_cache[layer_i])
    def write_v_cache_csv(self, layer_i, path): write_flat_csv_np(path, self.v_cache[layer_i])

    # ── Apply diff strings (idx_str → float) into a backing array ──────
    @staticmethod
    def _apply_diff_1d(arr, diff):
        for idx_str, val_str in diff:
            arr[int(idx_str)] = float(val_str)


# ──────────────────────── Placeholder CSV management ──────────────────────
class Placeholders:
    """Tiny one-element CSVs symlinked in wherever an array is unused.

    Kernel branching guarantees out-of-range indices are never accessed.
    """

    def __init__(self, work_dir):
        self.dir = os.path.join(work_dir, "placeholders")
        os.makedirs(self.dir, exist_ok=True)
        self.tiny = os.path.join(self.dir, "tiny.csv")
        with open(self.tiny, "w") as f:
            f.write("0\n")


# ────────────────────── Per-shard arg-list builder ────────────────────────
def _symlink_into(slot_dir, src, name):
    """Symlink src into slot_dir/name; replace if it already exists."""
    dst = os.path.join(slot_dir, name)
    if os.path.islink(dst) or os.path.exists(dst):
        os.remove(dst)
    os.symlink(os.path.abspath(src), dst)
    return dst


def _params_csv_path(work_dir, params):
    p = os.path.join(work_dir, "params.csv")
    with open(p, "w") as f:
        f.write(",".join(f"{v:.1f}" for v in params) + "\n")
    return p


# Slot arrays consumed by the layer-shard branches:
SLOT_WEIGHT_STEMS = [
    "qkv_packed", "qkv_scales",
    "o_packed",   "o_scales",
    "gate_up_packed", "gate_up_scales",
    "down_packed", "down_scales",
    "ln1_g", "ln2_g",
]
# Slot KV caches (per-shard state, rotated through the slot{j}_ names):
SLOT_CACHE_STEMS = ["k_cache", "v_cache"]
# Head-shard CSVs (real for head, placeholder for layer shards):
HEAD_REAL_STEMS = ["ln_f_g", "lm_head_1", "lm_head_2", "wte_1", "wte_2"]


def build_layer_shard_args(work_dir, slot_dir, weights_dir, state, ph, shard_idx, n_seq, n_tokens, shard_kind):
    """Construct the CSV args list for a prefill (kind=0) or decode (kind=2) shard."""
    base_layer = shard_idx * LAYERS_PER_SHARD

    # State CSVs (refreshed each call from in-memory state):
    state.write_h_state_csv(os.path.join(work_dir, "h_state.csv"))
    state.write_cur_n_seq_csv(os.path.join(work_dir, "cur_n_seq_arr.csv"))
    state.write_step_csv(os.path.join(work_dir, "step_arr.csv"))
    state.write_gen_tokens_csv(os.path.join(work_dir, "generated_tokens.csv"))
    # Per-layer K/V caches in slot positions
    for j in range(LAYERS_PER_SHARD):
        layer_i = base_layer + j
        state.write_k_cache_csv(layer_i, os.path.join(work_dir, f"slot{j}_k_cache.csv"))
        state.write_v_cache_csv(layer_i, os.path.join(work_dir, f"slot{j}_v_cache.csv"))

    params_csv = _params_csv_path(work_dir, [n_seq, n_tokens, shard_kind, shard_idx])

    args = [
        params_csv,
        os.path.join(work_dir, "h_state.csv"),
        os.path.join(work_dir, "cur_n_seq_arr.csv"),
        os.path.join(work_dir, "step_arr.csv"),
        os.path.join(work_dir, "generated_tokens.csv"),
        os.path.join(weights_dir, "cos_cache.csv"),
        os.path.join(weights_dir, "sin_cache.csv"),
    ]

    # Slot weights — symlink global layer's CSV → slot{j}_<stem>.csv
    for j in range(LAYERS_PER_SHARD):
        layer_i = base_layer + j
        for stem in SLOT_WEIGHT_STEMS:
            link = _symlink_into(slot_dir, os.path.join(weights_dir, f"l{layer_i}_{stem}.csv"),
                                 f"slot{j}_{stem}.csv")
            args.append(link)
        # Slot KV caches were already written to work_dir
        for stem in SLOT_CACHE_STEMS:
            args.append(os.path.join(work_dir, f"slot{j}_{stem}.csv"))

    # Head-shard arrays are unused under layer branches → tiny placeholders
    for stem in HEAD_REAL_STEMS:
        link = _symlink_into(slot_dir, ph.tiny, f"{stem}.csv")
        args.append(link)

    return args


def build_head_shard_args(work_dir, slot_dir, weights_dir, state, ph, n_seq, n_tokens):
    """Construct the CSV args list for the head shard (kind=1)."""
    state.write_h_state_csv(os.path.join(work_dir, "h_state.csv"))
    state.write_cur_n_seq_csv(os.path.join(work_dir, "cur_n_seq_arr.csv"))
    state.write_step_csv(os.path.join(work_dir, "step_arr.csv"))
    state.write_gen_tokens_csv(os.path.join(work_dir, "generated_tokens.csv"))

    params_csv = _params_csv_path(work_dir, [n_seq, n_tokens, 1, 0])

    args = [
        params_csv,
        os.path.join(work_dir, "h_state.csv"),
        os.path.join(work_dir, "cur_n_seq_arr.csv"),
        os.path.join(work_dir, "step_arr.csv"),
        os.path.join(work_dir, "generated_tokens.csv"),
        # cos/sin are unused but the kernel references them in the layer branches
        _symlink_into(slot_dir, ph.tiny, "cos_cache.csv"),
        _symlink_into(slot_dir, ph.tiny, "sin_cache.csv"),
    ]
    # Slot weights + slot KV caches → tiny placeholders
    for j in range(LAYERS_PER_SHARD):
        for stem in SLOT_WEIGHT_STEMS:
            args.append(_symlink_into(slot_dir, ph.tiny, f"slot{j}_{stem}.csv"))
        for stem in SLOT_CACHE_STEMS:
            args.append(_symlink_into(slot_dir, ph.tiny, f"slot{j}_{stem}.csv"))
    # Real head weights
    for stem in HEAD_REAL_STEMS:
        args.append(_symlink_into(slot_dir, os.path.join(weights_dir, f"{stem}.csv"), f"{stem}.csv"))
    # logits_partial is declared inside the kernel (in-Python list) — no CSV.
    return args


# ─────────────────────────── Diff handlers ────────────────────────────────
def _diff_path(work_dir, name):
    return os.path.join(work_dir, "diffs", f"{name}.diff")


def dump_and_merge_layer_shard(rj, state, work_dir, shard_idx, n_seq, n_tokens, shard_kind):
    """After a prefill or decode shard, pull the diff for the rows the shard
    just touched and merge into the in-memory state."""
    os.makedirs(os.path.join(work_dir, "diffs"), exist_ok=True)
    base_layer = shard_idx * LAYERS_PER_SHARD

    if shard_kind == 0:
        # PREFILL: rows 0..n_seq-1 touched.
        start = 0
        end_inclusive = n_seq * HIDDEN - 1
    else:
        # DECODE: row (cur_n_seq-1) touched. JVM has already executed; in-mem
        # cur_n_seq is still pre-call value (we don't increment during layer
        # shards — only the head shard increments). So:
        cur = int(state.cur_n_seq)
        start = (cur - 1) * HIDDEN
        end_inclusive = cur * HIDDEN - 1

    # h_state diff
    p = _diff_path(work_dir, "h_state")
    rj.dump_diff("h_state", start, end_inclusive, p)
    State._apply_diff_1d(state.h_state, read_diff_file(p))

    # Slot KV cache diffs → write back into global per-layer arrays
    for j in range(LAYERS_PER_SHARD):
        layer_i = base_layer + j
        kp = _diff_path(work_dir, f"slot{j}_k_cache")
        vp = _diff_path(work_dir, f"slot{j}_v_cache")
        rj.dump_diff(f"slot{j}_k_cache", start, end_inclusive, kp)
        rj.dump_diff(f"slot{j}_v_cache", start, end_inclusive, vp)
        State._apply_diff_1d(state.k_cache[layer_i], read_diff_file(kp))
        State._apply_diff_1d(state.v_cache[layer_i], read_diff_file(vp))


def dump_and_merge_head_shard(rj, state, work_dir):
    """Head shard mutates: a new h_state row (row = cur_n_seq before inc),
    generated_tokens[step], step_arr, cur_n_seq_arr."""
    os.makedirs(os.path.join(work_dir, "diffs"), exist_ok=True)
    cur_pre = int(state.cur_n_seq)   # the row embed_next will write into

    # h_state diff: row cur_pre
    p = _diff_path(work_dir, "h_state")
    rj.dump_diff("h_state", cur_pre * HIDDEN, (cur_pre + 1) * HIDDEN - 1, p)
    State._apply_diff_1d(state.h_state, read_diff_file(p))

    # generated_tokens: full-array range is cheap (1024 entries)
    p = _diff_path(work_dir, "generated_tokens")
    rj.dump_diff("generated_tokens", 0, MAX_SEQ - 1, p)
    State._apply_diff_1d(state.generated_tokens, read_diff_file(p))

    # step_arr, cur_n_seq_arr: 1-element arrays
    p = _diff_path(work_dir, "step_arr")
    rj.dump_diff("step_arr", 0, 0, p)
    diff = read_diff_file(p)
    if diff:
        state.step = float(diff[0][1])
    p = _diff_path(work_dir, "cur_n_seq_arr")
    rj.dump_diff("cur_n_seq_arr", 0, 0, p)
    diff = read_diff_file(p)
    if diff:
        state.cur_n_seq = float(diff[0][1])


# ─────────────────────────── Top-level driver ─────────────────────────────
def generate(prompt, n_tokens, weights_dir, java_home, rj_ws):
    weights_dir = os.path.abspath(weights_dir)
    tokenizer = AutoTokenizer.from_pretrained(
        "microsoft/Phi-3-mini-4k-instruct", trust_remote_code=True
    )

    messages = [{"role": "user", "content": prompt}]
    formatted_prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    input_ids = tokenizer.encode(formatted_prompt, add_special_tokens=False)

    log(f"Original prompt: {prompt!r}")
    log(f"Tokenised ({len(input_ids)} tokens): {input_ids}")

    n_seq = len(input_ids)

    kernel_path = os.path.abspath(os.path.join(os.path.dirname(__file__),
                                               "phi3_transformer_stack_sharded.py"))
    work_root = tempfile.mkdtemp(prefix="phi3_rj_sharded_")
    work_dir  = os.path.join(work_root, "work")
    slot_dir  = os.path.join(work_root, "slots")
    state_dir = os.path.join(work_root, "state")
    os.makedirs(work_dir,  exist_ok=True)
    os.makedirs(slot_dir,  exist_ok=True)
    os.makedirs(state_dir, exist_ok=True)
    log(f"Working dir: {work_root}")

    # Seed the hidden state with the prompt's embedding via NumPy (same as
    # the monolithic baseline does in run_phi3_4bit.py).
    log("Reading WTE for prompt embed ...")
    wte = np.load(os.path.join(weights_dir, "wte.npy")).astype(np.float32).reshape(-1, HIDDEN)
    state = State(state_dir, n_seq, n_tokens)
    state.h_state.fill(0.0)
    flat_embed = wte[input_ids].flatten()
    state.h_state[:flat_embed.size] = flat_embed

    ph = Placeholders(work_root)

    rj = RjServer(java_home=java_home, rj_ws=rj_ws)
    rj.start()
    t_start = time.time()
    try:
        # ── PREFILL: 4 layer shards ────────────────────────────────────
        for s in range(N_SHARDS):
            log(f"PREFILL shard {s} (layers {s*LAYERS_PER_SHARD}..{s*LAYERS_PER_SHARD+7}) ...")
            args = build_layer_shard_args(work_dir, slot_dir, weights_dir, state, ph,
                                          s, n_seq, n_tokens, shard_kind=0)
            t0 = time.time()
            rj.run(kernel_path, args)
            dump_and_merge_layer_shard(rj, state, work_dir, s, n_seq, n_tokens, shard_kind=0)
            log(f"  shard {s} done in {time.time()-t0:.2f}s")

        # ── HEAD #1 (produces token at position n_seq) ────────────────
        log("HEAD shard #1 (post-prefill) ...")
        args = build_head_shard_args(work_dir, slot_dir, weights_dir, state, ph, n_seq, n_tokens)
        rj.run(kernel_path, args)
        dump_and_merge_head_shard(rj, state, work_dir)
        log(f"  cur_n_seq={state.cur_n_seq} step={state.step} "
            f"first_token={int(state.generated_tokens[0])}")

        # ── DECODE LOOP ────────────────────────────────────────────────
        for t in range(1, n_tokens):
            for s in range(N_SHARDS):
                log(f"DECODE token {t} shard {s} ...")
                args = build_layer_shard_args(work_dir, slot_dir, weights_dir, state, ph,
                                              s, n_seq, n_tokens, shard_kind=2)
                t0 = time.time()
                rj.run(kernel_path, args)
                dump_and_merge_layer_shard(rj, state, work_dir, s, n_seq, n_tokens, shard_kind=2)
                log(f"  shard {s} done in {time.time()-t0:.2f}s")
            log(f"HEAD shard token {t} ...")
            args = build_head_shard_args(work_dir, slot_dir, weights_dir, state, ph, n_seq, n_tokens)
            rj.run(kernel_path, args)
            dump_and_merge_head_shard(rj, state, work_dir)
            log(f"  generated token {int(state.generated_tokens[t])} "
                f"(cur_n_seq={state.cur_n_seq})")

        log(f"All shards complete in {time.time()-t_start:.1f}s")
        out_ids = [int(state.generated_tokens[i]) for i in range(n_tokens)]
        log(f"Generated ids: {out_ids}")
        text = tokenizer.decode(out_ids)
        log(f"Output text:\n{text}")
    finally:
        rj.shutdown()
        shutil.rmtree(work_root, ignore_errors=True)


# ───────────────────────────────── CLI ────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("prompt", type=str)
    parser.add_argument("--n-tokens", type=int, default=4)
    parser.add_argument("--weights-dir", default="phi3_weights_csv")
    parser.add_argument("--java-home",
                        default=os.environ.get("JAVA_HOME",
                            "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home"))
    parser.add_argument("--rj-ws", default=os.environ.get("RAMANUJAN_WS", "/tmp"))
    args = parser.parse_args()

    generate(args.prompt, args.n_tokens, args.weights_dir, args.java_home, args.rj_ws)

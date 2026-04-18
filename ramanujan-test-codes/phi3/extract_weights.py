#!/usr/bin/env python3
"""
extract_weights.py
==================
Extracts Phi-3-mini-4k-instruct weights from safetensors files and saves
them as single-row CSV files ready for the Ramanujan runtime.

Also precomputes RoPE cos/sin tables.

Usage
-----
  python extract_weights.py                          # default model path
  python extract_weights.py /path/to/Phi-3-model     # custom model path
  python extract_weights.py --layers 2               # only first 2 layers (for testing)

Requirements
------------
  pip install safetensors numpy
"""

import json
import math
import os
import sys
import numpy as np

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_MODEL_DIR = os.path.join(os.path.dirname(SCRIPT_DIR), "..", "Phi-3-mini-4k-instruct")

# ── Architecture constants ────────────────────────────────────────────────────
VOCAB       = 32064
HIDDEN      = 3072
INTERMEDIATE = 8192
NUM_HEADS   = 32
HEAD_DIM    = 96   # HIDDEN // NUM_HEADS
NUM_LAYERS  = 32
MAX_SEQ     = 64
ROPE_THETA  = 10000.0
RMS_EPS     = 1e-5


def parse_args():
    model_dir = DEFAULT_MODEL_DIR
    num_layers = NUM_LAYERS

    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--layers":
            num_layers = int(args[i + 1])
            i += 2
        elif not args[i].startswith("-"):
            model_dir = args[i]
            i += 1
        else:
            i += 1

    model_dir = os.path.abspath(model_dir)
    return model_dir, num_layers


def load_safetensors(model_dir):
    """Load all weight tensors from safetensors files.
    Handles bfloat16 by reading raw file bytes and converting uint16 → float32.
    """
    import struct

    def bf16_bytes_to_f32(raw_bytes, shape):
        u16 = np.frombuffer(raw_bytes, dtype=np.uint16)
        u32 = u16.astype(np.uint32) << 16
        return u32.view(np.float32).reshape(shape)

    DTYPE_SIZES = {"F32": 4, "F16": 2, "BF16": 2, "I32": 4, "I64": 8, "U8": 1}
    DTYPE_NP    = {"F32": np.float32, "F16": np.float16, "I32": np.int32, "I64": np.int64, "U8": np.uint8}

    weights = {}
    for fname in sorted(os.listdir(model_dir)):
        if not fname.endswith(".safetensors"):
            continue
        path = os.path.join(model_dir, fname)
        print(f"  Loading {fname} ...")
        with open(path, "rb") as fh:
            # Read 8-byte header length
            header_len = struct.unpack("<Q", fh.read(8))[0]
            header_bytes = fh.read(header_len)
            header = json.loads(header_bytes)
            data_start = 8 + header_len

            for key, meta in header.items():
                if key == "__metadata__":
                    continue
                dtype_str = meta["dtype"]
                shape = meta["shape"]
                offsets = meta["data_offsets"]  # [start, end] relative to data_start
                nbytes = offsets[1] - offsets[0]

                fh.seek(data_start + offsets[0])
                raw = fh.read(nbytes)

                if dtype_str == "BF16":
                    weights[key] = bf16_bytes_to_f32(raw, shape)
                elif dtype_str in DTYPE_NP:
                    weights[key] = np.frombuffer(raw, dtype=DTYPE_NP[dtype_str]).reshape(shape).astype(np.float32)
                else:
                    # F16 via numpy
                    weights[key] = np.frombuffer(raw, dtype=np.float16).reshape(shape).astype(np.float32)

    print(f"  Loaded {len(weights)} tensors total")
    return weights


def single_row_csv(arr: np.ndarray) -> str:
    """Flatten array to a single-row CSV string."""
    flat = arr.flatten().astype(np.float32)
    return ",".join(f"{v:.8f}" for v in flat)


def save_csv(name: str, arr: np.ndarray, out_dir: str) -> None:
    """Save a numpy array as CSV.
    For 2D arrays: multi-row CSV (one row per matrix row) so the rj Scanner
    can handle large weight matrices without exceeding line-length limits.
    For 1D arrays: single-row CSV (unchanged from tinyBert convention).
    """
    path = os.path.join(out_dir, f"{name}.csv")
    f32 = arr.astype(np.float32)
    if f32.ndim == 2:
        # Multi-row: each matrix row is one CSV line
        with open(path, "w") as f:
            for r in range(f32.shape[0]):
                f.write(",".join(f"{v:.8f}" for v in f32[r]))
                if r < f32.shape[0] - 1:
                    f.write("\n")
        print(f"    {name}.csv  ({arr.size:,} values, shape {arr.shape}, {f32.shape[0]} rows)")
    else:
        # 1D: single-row
        csv_str = ",".join(f"{v:.8f}" for v in f32.flatten())
        with open(path, "w") as f:
            f.write(csv_str)
        print(f"    {name}.csv  ({arr.size:,} values, shape {arr.shape})")


def compute_rope_tables(max_seq: int, head_dim: int, theta: float):
    """Precompute RoPE cos/sin tables: shape [max_seq, head_dim//2]."""
    half = head_dim // 2
    freqs = 1.0 / (theta ** (np.arange(0, head_dim, 2, dtype=np.float32) / head_dim))
    positions = np.arange(max_seq, dtype=np.float32)
    angles = np.outer(positions, freqs)  # [max_seq, half]
    return np.cos(angles).astype(np.float32), np.sin(angles).astype(np.float32)


def extract_and_save(model_dir: str, num_layers: int):
    """Main extraction routine."""
    weights_dir = os.path.join(SCRIPT_DIR, "weights")
    os.makedirs(weights_dir, exist_ok=True)

    print(f"\nModel directory : {model_dir}")
    print(f"Layers to export: {num_layers}")
    print(f"Output directory: {weights_dir}\n")

    # Load weights
    print("Loading safetensors files ...")
    st = load_safetensors(model_dir)

    # ── Global weights ────────────────────────────────────────────────────
    print("\nSaving global weights ...")
    save_csv("embed_tokens", st["model.embed_tokens.weight"], weights_dir)
    save_csv("final_norm", st["model.norm.weight"], weights_dir)
    save_csv("lm_head", st["lm_head.weight"], weights_dir)

    # ── RoPE tables ───────────────────────────────────────────────────────
    print("\nPrecomputing RoPE tables ...")
    rope_cos, rope_sin = compute_rope_tables(MAX_SEQ, HEAD_DIM, ROPE_THETA)
    save_csv("rope_cos", rope_cos.flatten(), weights_dir)
    save_csv("rope_sin", rope_sin.flatten(), weights_dir)

    # ── Per-layer weights ─────────────────────────────────────────────────
    for layer_idx in range(num_layers):
        print(f"\nSaving layer {layer_idx} weights ...")
        prefix = f"model.layers.{layer_idx}"

        save_csv(f"l{layer_idx}_qkv",  st[f"{prefix}.self_attn.qkv_proj.weight"], weights_dir)
        save_csv(f"l{layer_idx}_o",    st[f"{prefix}.self_attn.o_proj.weight"], weights_dir)
        save_csv(f"l{layer_idx}_gu",   st[f"{prefix}.mlp.gate_up_proj.weight"], weights_dir)
        save_csv(f"l{layer_idx}_down", st[f"{prefix}.mlp.down_proj.weight"], weights_dir)
        save_csv(f"l{layer_idx}_iln",  st[f"{prefix}.input_layernorm.weight"], weights_dir)
        save_csv(f"l{layer_idx}_pln",  st[f"{prefix}.post_attention_layernorm.weight"], weights_dir)

    # ── Save manifest ─────────────────────────────────────────────────────
    manifest = {
        "model": "Phi-3-mini-4k-instruct",
        "num_layers_exported": num_layers,
        "arch": {
            "VOCAB": VOCAB,
            "HIDDEN": HIDDEN,
            "INTERMEDIATE": INTERMEDIATE,
            "NUM_HEADS": NUM_HEADS,
            "HEAD_DIM": HEAD_DIM,
            "MAX_SEQ": MAX_SEQ,
            "ROPE_THETA": ROPE_THETA,
            "RMS_EPS": RMS_EPS,
        },
        "weight_files": {
            "global": ["embed_tokens.csv", "final_norm.csv", "lm_head.csv",
                       "rope_cos.csv", "rope_sin.csv"],
            "per_layer_template": [
                "l{i}_qkv.csv", "l{i}_o.csv", "l{i}_gu.csv",
                "l{i}_down.csv", "l{i}_iln.csv", "l{i}_pln.csv",
            ],
        },
    }
    manifest_path = os.path.join(SCRIPT_DIR, "manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
    print(f"\nManifest saved to {manifest_path}")

    # ── Generate kernel if function-param approach doesn't work ───────────
    generate_unrolled_kernel(num_layers)

    # ── Summary ───────────────────────────────────────────────────────────
    total_params = (
        VOCAB * HIDDEN +                          # embed_tokens
        HIDDEN +                                   # final_norm
        VOCAB * HIDDEN +                           # lm_head
        num_layers * (
            3 * HIDDEN * HIDDEN +                  # qkv_proj
            HIDDEN * HIDDEN +                      # o_proj
            2 * INTERMEDIATE * HIDDEN +            # gate_up_proj
            HIDDEN * INTERMEDIATE +                # down_proj (HIDDEN × INTERMEDIATE)
            HIDDEN * 2                             # layer norms
        )
    )
    csv_count = 5 + num_layers * 6
    print(f"\n{'='*60}")
    print(f"  Total parameters : {total_params:,}")
    print(f"  CSV files created: {csv_count}")
    print(f"  Layers exported  : {num_layers}")
    print(f"{'='*60}")
    print(f"\nNext steps:")
    print(f"  python inference.py \"Hello world\"")


def generate_unrolled_kernel(num_layers: int):
    """
    Generate inference_kernel_unrolled.py with explicit per-layer calls.
    This is a fallback in case the Ramanujan translator doesn't support
    passing CSV-injected arrays through host function parameters to GPU kernels.
    """
    out_path = os.path.join(SCRIPT_DIR, "inference_kernel_unrolled.py")

    lines = []
    lines.append("# === AUTO-GENERATED Phi-3 Inference Kernel (unrolled) ===")
    lines.append(f"# Layers: {num_layers}  VOCAB: {VOCAB}  HIDDEN: {HIDDEN}")
    lines.append(f"# INTERMEDIATE: {INTERMEDIATE}  HEADS: {NUM_HEADS}  HD: {HEAD_DIM}  MAX_SEQ: {MAX_SEQ}")
    lines.append("# Generated by extract_weights.py — do not edit manually.")
    lines.append("# If forward_layer() works, use inference_kernel.py instead.")
    lines.append("")

    # Read the static kernel to extract everything before the layer calls
    static_kernel = os.path.join(SCRIPT_DIR, "inference_kernel.py")
    if os.path.exists(static_kernel):
        with open(static_kernel) as f:
            content = f.read()
        # Copy everything up to the "# === LAYER CALLS ===" marker
        marker = "# === LAYER CALLS ==="
        idx = content.find(marker)
        if idx >= 0:
            lines.append(content[:idx])
        else:
            # No marker, copy the whole file and append layer calls
            lines.append(content)
            lines.append("")
        lines.append(marker)
        lines.append("")

        # Generate per-layer blocks
        for i in range(num_layers):
            lines.append(f"# --- Layer {i} ---")
            lines.append(f"rms_norm_compute(hidden)")
            lines.append(f"rms_apply_GPU_1(hidden, rms_vals, l{i}_iln, normed, 196608)")
            lines.append(f"qkv_proj_GPU_1(normed, l{i}_qkv, q_buf, k_buf, v_buf, 196608)")
            lines.append(f"rope_GPU_1(q_buf, k_buf, rope_cos, rope_sin, 98304)")
            lines.append(f"lh = 0")
            lines.append(f"while lh < {NUM_HEADS}:")
            lines.append(f"    head_buf[0] = lh")
            lines.append(f"    attn_score_GPU_1(q_buf, k_buf, attn_sc, head_buf, 4096)")
            lines.append(f"    softmax_attn()")
            lines.append(f"    attn_ctx_GPU_1(attn_sc, v_buf, concat, head_buf, 6144)")
            lines.append(f"    lh = lh + 1")
            lines.append(f"out_proj_res_GPU_1(concat, l{i}_o, hidden, 196608)")
            lines.append(f"rms_norm_compute(hidden)")
            lines.append(f"rms_apply_GPU_1(hidden, rms_vals, l{i}_pln, normed, 196608)")
            lines.append(f"gate_up_GPU_1(normed, l{i}_gu, gate_buf, up_buf, 524288)")
            lines.append(f"silu_mul_GPU_1(gate_buf, up_buf, 524288)")
            lines.append(f"down_proj_res_GPU_1(gate_buf, l{i}_down, hidden, 196608)")
            lines.append("")

        # Final norm + LM head + argmax (copy from static kernel if available)
        lines.append("# === FINAL OUTPUT ===")
        final_marker = "# === FINAL OUTPUT ==="
        fidx = content.find(final_marker)
        if fidx >= 0:
            lines.append(content[fidx + len(final_marker):])
        else:
            lines.append("# (copy final output code from inference_kernel.py)")

    with open(out_path, "w") as f:
        f.write("\n".join(lines))
    print(f"\nGenerated unrolled kernel: {out_path}")


if __name__ == "__main__":
    model_dir, num_layers = parse_args()
    extract_and_save(model_dir, num_layers)

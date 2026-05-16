#!/usr/bin/env python3
"""
Download GPT-2 124M model weights from OpenAI's public bucket and save them
as flat 1D CSV files (for Ramanujan layer_kernel.py) and .npy files (for fast
Python loading in run_gpt2.py).

Usage:
    pip install tensorflow requests tqdm numpy
    python download_weights.py [--models-dir models] [--weights-dir weights]
"""
import os
import re
import sys
import argparse
import numpy as np

MODEL_SIZE = "124M"
BASE_URL   = "https://openaipublic.blob.core.windows.net/gpt-2/models"
FILES = [
    "checkpoint",
    "encoder.json",
    "hparams.json",
    "model.ckpt.data-00000-of-00001",
    "model.ckpt.index",
    "model.ckpt.meta",
    "vocab.bpe",
]

# GPT-2 124M constants (hard-coded, match layer_kernel.py literals)
N_LAYER = 12
N_EMBD  = 768
N_HEAD  = 12
D_HEAD  = 64
N_FF    = 3072
N_VOCAB = 50257
N_CTX   = 1024


# ---------------------------------------------------------------------------
# Download helpers
# ---------------------------------------------------------------------------

def download_file(url, dest, chunk_size=8192):
    import requests
    from tqdm import tqdm
    r = requests.get(url, stream=True)
    r.raise_for_status()
    total = int(r.headers.get("content-length", 0))
    with open(dest, "wb") as f, tqdm(
        total=total, desc=os.path.basename(dest),
        unit="B", unit_scale=True, ncols=80,
    ) as pbar:
        for chunk in r.iter_content(chunk_size):
            f.write(chunk)
            pbar.update(len(chunk))


# ---------------------------------------------------------------------------
# CSV / NPY save helpers
# ---------------------------------------------------------------------------

def save_flat_csv(arr: np.ndarray, path: str):
    """Save as a single-line comma-separated file (Ramanujan 1-D format)."""
    flat = arr.flatten().astype(np.float32)
    with open(path, "w") as f:
        f.write(",".join(f"{v:.8g}" for v in flat))
        f.write("\n")


def save_npy(arr: np.ndarray, path: str):
    """Save as numpy binary for fast Python loading."""
    np.save(path, arr.astype(np.float32))


def save_weight(arr: np.ndarray, base_path: str):
    """Save both .csv (Ramanujan) and .npy (Python) formats."""
    save_flat_csv(arr, base_path + ".csv")
    save_npy(arr, base_path + ".npy")


# ---------------------------------------------------------------------------
# TF checkpoint loading
# ---------------------------------------------------------------------------

def load_params_from_tf_ckpt(tf_ckpt_path):
    import tensorflow.compat.v1 as tf
    tf.disable_eager_execution()

    def set_nested(d, keys, val):
        if not keys:
            return val
        if keys[0] not in d:
            d[keys[0]] = {}
        d[keys[0]] = set_nested(d[keys[0]], keys[1:], val)
        return d

    params = {"blocks": [{} for _ in range(N_LAYER)]}

    for name, _ in tf.train.list_variables(tf_ckpt_path):
        arr = np.squeeze(tf.train.load_variable(tf_ckpt_path, name))
        name_clean = name[len("model/"):]
        if name_clean.startswith("h"):
            m = re.match(r"h([0-9]+)/(.*)", name_clean)
            n, sub = int(m[1]), m[2]
            set_nested(params["blocks"][n], sub.split("/"), arr)
        else:
            set_nested(params, name_clean.split("/"), arr)

    return params


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(models_dir: str, weights_dir: str):
    model_dir = os.path.join(models_dir, MODEL_SIZE)
    os.makedirs(model_dir, exist_ok=True)
    os.makedirs(weights_dir, exist_ok=True)

    # ── Download model files ──────────────────────────────────────────────
    print("=== Downloading GPT-2 124M ===")
    for filename in FILES:
        dest = os.path.join(model_dir, filename)
        if not os.path.exists(dest):
            url = f"{BASE_URL}/{MODEL_SIZE}/{filename}"
            print(f"  Downloading {filename} ...")
            download_file(url, dest)
        else:
            print(f"  {filename} (already present)")

    # ── Load checkpoint ───────────────────────────────────────────────────
    import tensorflow.compat.v1 as tf
    tf.disable_eager_execution()
    tf_ckpt_path = tf.train.latest_checkpoint(model_dir)
    print(f"\n=== Loading checkpoint: {tf_ckpt_path} ===")
    params = load_params_from_tf_ckpt(tf_ckpt_path)

    # ── Save global weights ───────────────────────────────────────────────
    print("\n=== Saving global weights ===")

    wte = params["wte"]   # (50257, 768)
    wpe = params["wpe"]   # (1024,  768)
    print(f"  wte {wte.shape}")
    save_weight(wte, os.path.join(weights_dir, "wte"))

    print(f"  wpe {wpe.shape}")
    save_weight(wpe, os.path.join(weights_dir, "wpe"))

    ln_f_g = params["ln_f"]["g"]   # (768,)
    ln_f_b = params["ln_f"]["b"]   # (768,)
    print(f"  ln_f_g {ln_f_g.shape}, ln_f_b {ln_f_b.shape}")
    save_weight(ln_f_g, os.path.join(weights_dir, "ln_f_g"))
    save_weight(ln_f_b, os.path.join(weights_dir, "ln_f_b"))

    # ── Save per-layer weights ────────────────────────────────────────────
    print("\n=== Saving per-layer weights ===")
    for i, blk in enumerate(params["blocks"]):
        print(f"  Layer {i:2d} ...")
        p = os.path.join(weights_dir, f"l{i}")

        # Layer norms
        save_weight(blk["ln_1"]["g"],            p + "_ln1_g")
        save_weight(blk["ln_1"]["b"],            p + "_ln1_b")
        save_weight(blk["ln_2"]["g"],            p + "_ln2_g")
        save_weight(blk["ln_2"]["b"],            p + "_ln2_b")

        # Attention: c_attn.w  (768, 2304)  ─ QKV weight
        #            c_attn.b  (2304,)
        #            c_proj.w  (768, 768)   ─ output weight
        #            c_proj.b  (768,)
        save_weight(blk["attn"]["c_attn"]["w"],  p + "_c_attn_w")
        save_weight(blk["attn"]["c_attn"]["b"],  p + "_c_attn_b")
        save_weight(blk["attn"]["c_proj"]["w"],  p + "_c_proj_w")
        save_weight(blk["attn"]["c_proj"]["b"],  p + "_c_proj_b")

        # FFN: c_fc.w   (768, 3072)  ─ up-projection
        #      c_fc.b   (3072,)
        #      c_proj.w (3072, 768)  ─ down-projection (saved as c_fc_proj_w)
        #      c_proj.b (768,)
        save_weight(blk["mlp"]["c_fc"]["w"],     p + "_c_fc_w")
        save_weight(blk["mlp"]["c_fc"]["b"],     p + "_c_fc_b")
        save_weight(blk["mlp"]["c_proj"]["w"],   p + "_c_fc_proj_w")
        save_weight(blk["mlp"]["c_proj"]["b"],   p + "_c_fc_proj_b")

    print(f"\nDone!  Weights saved to: {weights_dir}/")
    print(f"  n_layer={N_LAYER}, n_head={N_HEAD}, n_embd={N_EMBD}")
    print(f"  n_vocab={N_VOCAB}, n_ctx={N_CTX}, n_ff={N_FF}")
    print("\nCSV files are for the Ramanujan layer kernel.")
    print("NPY files are for fast Python loading in run_gpt2.py.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Download GPT-2 124M weights")
    parser.add_argument("--models-dir",  default="models",  help="where to store raw TF checkpoint")
    parser.add_argument("--weights-dir", default="weights", help="where to write CSV/NPY files")
    args = parser.parse_args()
    main(args.models_dir, args.weights_dir)

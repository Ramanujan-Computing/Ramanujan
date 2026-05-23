import struct
import json
import numpy as np
import os
import gc

def read_safetensors_header(filename):
    with open(filename, 'rb') as f:
        header_size = struct.unpack('<Q', f.read(8))[0]
        header_json = f.read(header_size).decode('utf-8')
        return json.loads(header_json), 8 + header_size

def load_tensor(filename, tensor_info, base_offset):
    shape = tensor_info['shape']
    dtype = tensor_info['dtype']
    offsets = tensor_info['data_offsets']
    start = base_offset + offsets[0]
    end = base_offset + offsets[1]

    with open(filename, 'rb') as f:
        f.seek(start)
        data = f.read(end - start)

    if dtype == 'BF16':
        data16 = np.frombuffer(data, dtype=np.uint16)
        data32 = np.zeros(data16.shape[0], dtype=np.uint32)
        data32[:] = data16
        data32 = data32 << 16
        arr = data32.view(np.float32).reshape(shape)
    elif dtype == 'F16':
        arr = np.frombuffer(data, dtype=np.float16).astype(np.float32).reshape(shape)
    elif dtype == 'F32':
        arr = np.frombuffer(data, dtype=np.float32).reshape(shape)
    else:
        raise ValueError(f"Unsupported dtype {dtype}")

    return arr

def quantize_and_pack(arr):
    # arr: [out_features, in_features]
    out_features, in_features = arr.shape

    # Pad in_features to multiple of 6
    pad_len = (6 - (in_features % 6)) % 6
    if pad_len > 0:
        arr = np.pad(arr, ((0,0), (0, pad_len)), 'constant')
        in_features += pad_len

    abs_max = np.abs(arr).max(axis=1, keepdims=True)
    scales = abs_max / 7.0
    scales[scales == 0] = 1.0

    q_arr = np.round(arr / scales)
    q_arr = np.clip(q_arr, -8, 7)
    q_arr = q_arr + 8
    q_arr = q_arr.astype(np.uint32)

    q_arr_reshaped = q_arr.reshape(out_features, in_features // 6, 6)
    multipliers = np.array([1, 16, 256, 4096, 65536, 1048576], dtype=np.uint32)
    packed = np.sum(q_arr_reshaped * multipliers, axis=2).astype(np.float32)

    return packed, scales.squeeze(-1).astype(np.float32)

def write_csv(path, arr):
    """Write a fresh CSV (overwrites). arr can be 1-D or 2-D; we flatten."""
    flat = arr.flatten()
    with open(path, 'w') as f:
        for i in range(0, len(flat), 100000):
            chunk = flat[i:i+100000]
            f.write(','.join(map(lambda x: str(x) if isinstance(x, (int, np.integer)) else f"{x:.8g}", chunk)))
            # Trailing comma between chunks but no final comma — pass `,` only if more chunks remain.
            if i + 100000 < len(flat):
                f.write(',')

def main():
    out_dir = "phi3_weights_csv"
    os.makedirs(out_dir, exist_ok=True)

    f1 = '/Users/pranav/Desktop/ramanujan_oss/Phi-3-mini-4k-instruct/model-00001-of-00002.safetensors'
    f2 = '/Users/pranav/Desktop/ramanujan_oss/Phi-3-mini-4k-instruct/model-00002-of-00002.safetensors'

    h1, off1 = read_safetensors_header(f1)
    h2, off2 = read_safetensors_header(f2)

    def get_t(k):
        if k in h1:
            return load_tensor(f1, h1[k], off1)
        elif k in h2:
            return load_tensor(f2, h2[k], off2)
        else:
            raise KeyError(k)

    # ── RoPE caches (shared across layers) ─────────────────────────────────
    print("Generating RoPE cache...")
    dim = 96
    base = 10000.0
    max_seq_len = 4096

    inv_freq = 1.0 / (base ** (np.arange(0, dim, 2, dtype=np.float32) / dim))
    t = np.arange(max_seq_len, dtype=np.float32)
    freqs = np.outer(t, inv_freq)

    cos_cache = np.cos(freqs).astype(np.float32)
    sin_cache = np.sin(freqs).astype(np.float32)
    write_csv(f"{out_dir}/cos_cache.csv", cos_cache)
    write_csv(f"{out_dir}/sin_cache.csv", sin_cache)

    # ── Embedding table (kept as .npy for fast NumPy prompt-embed; split csv for kernel) ──
    print("Exporting embed_tokens (wte)...")
    wte = get_t('model.embed_tokens.weight')                # [32064, 3072]
    np.save(f"{out_dir}/wte.npy", wte)
    # Split at token 16000: wte_1 = tokens 0..15999, wte_2 = tokens 16000..32063
    write_csv(f"{out_dir}/wte_1.csv", wte[:16000])
    write_csv(f"{out_dir}/wte_2.csv", wte[16000:])

    # ── LM head (separate from embed_tokens because tie_word_embeddings=false) ──
    print("Exporting lm_head...")
    lm_head = get_t('lm_head.weight')                        # [32064, 3072]
    write_csv(f"{out_dir}/lm_head_1.csv", lm_head[:16000])
    write_csv(f"{out_dir}/lm_head_2.csv", lm_head[16000:])

    # ── Final norm ─────────────────────────────────────────────────────────
    print("Exporting ln_f_g (model.norm)...")
    ln_f = get_t('model.norm.weight')
    write_csv(f"{out_dir}/ln_f_g.csv", ln_f)

    # ── Per-layer weights: 32 layers, one CSV per (layer, weight) ──────────
    # The kernel references variables named l{i}_qkv_packed etc., so CSV
    # filenames must match those stems.
    for i in range(32):
        print(f"Processing Layer {i}/32...")

        qkv = get_t(f'model.layers.{i}.self_attn.qkv_proj.weight')
        packed, scales = quantize_and_pack(qkv)
        write_csv(f"{out_dir}/l{i}_qkv_packed.csv", packed)
        write_csv(f"{out_dir}/l{i}_qkv_scales.csv", scales)

        o = get_t(f'model.layers.{i}.self_attn.o_proj.weight')
        packed, scales = quantize_and_pack(o)
        write_csv(f"{out_dir}/l{i}_o_packed.csv", packed)
        write_csv(f"{out_dir}/l{i}_o_scales.csv", scales)

        gu = get_t(f'model.layers.{i}.mlp.gate_up_proj.weight')
        packed, scales = quantize_and_pack(gu)
        write_csv(f"{out_dir}/l{i}_gate_up_packed.csv", packed)
        write_csv(f"{out_dir}/l{i}_gate_up_scales.csv", scales)

        d = get_t(f'model.layers.{i}.mlp.down_proj.weight')
        packed, scales = quantize_and_pack(d)
        write_csv(f"{out_dir}/l{i}_down_packed.csv", packed)
        write_csv(f"{out_dir}/l{i}_down_scales.csv", scales)

        ln1 = get_t(f'model.layers.{i}.input_layernorm.weight')
        ln2 = get_t(f'model.layers.{i}.post_attention_layernorm.weight')
        write_csv(f"{out_dir}/l{i}_ln1_g.csv", ln1)
        write_csv(f"{out_dir}/l{i}_ln2_g.csv", ln2)

        gc.collect()

    print("Done converting!")

if __name__ == "__main__":
    main()

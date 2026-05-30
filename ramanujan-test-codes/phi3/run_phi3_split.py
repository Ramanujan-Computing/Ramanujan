#!/usr/bin/env python3
import argparse
import datetime
import os
import shutil
import subprocess
import sys
import tempfile
import time
import numpy as np
from transformers import AutoTokenizer

# Import the orchestrator classes from run_phi3_4bit
# We assume run_phi3_4bit.py is in the same directory
from run_phi3_4bit import RjServer, RjHomelabClient, log, write_flat_csv, read_flat_csv

LAYER_WEIGHT_STEMS = [
    "qkv_packed", "qkv_scales",
    "o_packed", "o_scales",
    "gate_up_packed", "gate_up_scales",
    "down_packed", "down_scales",
    "ln1_g", "ln2_g",
]

def generate_chunk_script(chunk_size, base_code):
    lines = []
    lines.append("""
cur_n_seq_arr = [0 for _ in range(1)]
h_ln1     = [0 for _ in range(3145728)]   
h_ln2     = [0 for _ in range(3145728)]
attn_out  = [0 for _ in range(3145728)]
scores_2d = [0 for _ in range(33554432)]  
qkv_buf    = [0 for _ in range(9437184)]   
h_attn_buf = [0 for _ in range(3145728)]
h_ff_buf   = [0 for _ in range(16777216)]  
h_out_buf  = [0 for _ in range(3145728)]

kp_qkv  = [0 for _ in range(3)]
kp_proj = [0 for _ in range(3)]
kp_fc   = [0 for _ in range(3)]
kp_fcp  = [0 for _ in range(3)]
""")

    lines.append(base_code)

    lines.append("""
n_seq       = params[0]
is_decode   = params[1]
cur_n_seq   = params[2]

cur_n_seq_arr[0] = cur_n_seq

kp_qkv[0]  = 3072.0
kp_qkv[1]  = 9216.0
kp_qkv[2]  = 512.0

kp_proj[0] = 3072.0
kp_proj[1] = 3072.0
kp_proj[2] = 512.0

kp_fc[0]   = 3072.0
kp_fc[1]   = 16384.0
kp_fc[2]   = 512.0

kp_fcp[0]  = 16384.0
kp_fcp[1]  = 3072.0
kp_fcp[2]  = 1366.0
""")

    lines.append("""
if is_decode == 0.0:
    # PREFILL
""")
    for k in range(chunk_size):
        lines.append(f"""    rmsnorm_GPU_1(hidden, l{k}_ln1_g, h_ln1, n_seq)
    matmul_4bit_GPU_2(h_ln1, l{k}_qkv_packed, l{k}_qkv_scales, qkv_buf, kp_qkv, n_seq, 9216)
    rope_and_cache_GPU_2(qkv_buf, cos_cache, sin_cache, l{k}_k_cache, l{k}_v_cache, n_seq, 32)
    causal_attn_GPU_2(qkv_buf, l{k}_k_cache, l{k}_v_cache, scores_2d, attn_out, cur_n_seq_arr, n_seq, 32)
    matmul_4bit_GPU_2(attn_out, l{k}_o_packed, l{k}_o_scales, h_attn_buf, kp_proj, n_seq, 3072)
    residual_add_GPU_2(hidden, h_attn_buf, n_seq, 3072)

    rmsnorm_GPU_1(hidden, l{k}_ln2_g, h_ln2, n_seq)
    matmul_4bit_GPU_2(h_ln2, l{k}_gate_up_packed, l{k}_gate_up_scales, h_ff_buf, kp_fc, n_seq, 16384)
    silu_GPU_2(h_ff_buf, n_seq, 8192)
    matmul_4bit_GPU_2(h_ff_buf, l{k}_down_packed, l{k}_down_scales, h_out_buf, kp_fcp, n_seq, 3072)
    residual_add_GPU_2(hidden, h_out_buf, n_seq, 3072)
""")

    lines.append("""else:
    # DECODE
""")
    for k in range(chunk_size):
        lines.append(f"""    rmsnorm_decode_GPU_1(hidden, l{k}_ln1_g, h_ln1, cur_n_seq_arr, 3072)
    matmul_4bit_decode_GPU_1(h_ln1, l{k}_qkv_packed, l{k}_qkv_scales, qkv_buf, kp_qkv, cur_n_seq_arr, 9216)
    rope_and_cache_decode_GPU_1(qkv_buf, cos_cache, sin_cache, l{k}_k_cache, l{k}_v_cache, cur_n_seq_arr, 32)
    causal_attn_k_decode_GPU_2(qkv_buf, l{k}_k_cache, scores_2d, cur_n_seq_arr, cur_n_seq, 32)
    causal_attn_softmax_decode_GPU_1(scores_2d, cur_n_seq_arr, 32)
    causal_attn_v_decode_GPU_2(scores_2d, l{k}_v_cache, attn_out, cur_n_seq_arr, 96, 32)
    matmul_4bit_decode_GPU_1(attn_out, l{k}_o_packed, l{k}_o_scales, h_attn_buf, kp_proj, cur_n_seq_arr, 3072)
    residual_add_decode_GPU_1(hidden, h_attn_buf, cur_n_seq_arr, 3072)

    rmsnorm_decode_GPU_1(hidden, l{k}_ln2_g, h_ln2, cur_n_seq_arr, 3072)
    matmul_4bit_decode_GPU_1(h_ln2, l{k}_gate_up_packed, l{k}_gate_up_scales, h_ff_buf, kp_fc, cur_n_seq_arr, 16384)
    silu_decode_GPU_1(h_ff_buf, cur_n_seq_arr, 8192)
    matmul_4bit_decode_GPU_1(h_ff_buf, l{k}_down_packed, l{k}_down_scales, h_out_buf, kp_fcp, cur_n_seq_arr, 3072)
    residual_add_decode_GPU_1(hidden, h_out_buf, cur_n_seq_arr, 3072)
""")
    lines.append("""
max_cache_idx_f = cur_n_seq * 3072.0 + 4.0 * 3072.0 - 1.0
max_cache_idx = 0
while max_cache_idx_f >= 1.0:
    max_cache_idx = max_cache_idx + 1
    max_cache_idx_f = max_cache_idx_f - 1.0
""")
    for k in range(chunk_size):
        lines.append(f"l{k}_k_cache[max_cache_idx] = l{k}_k_cache[max_cache_idx]")
        lines.append(f"l{k}_v_cache[max_cache_idx] = l{k}_v_cache[max_cache_idx]")

    for k in range(chunk_size):
        lines.append(f"GPU_SYNC(l{k}_k_cache)")
        lines.append(f"GPU_SYNC(l{k}_v_cache)")
    lines.append("GPU_SYNC(hidden)  # hidden was last written by residual_add_GPU_2 on GPU")

    return "\n".join(lines)

def generate_split(prompt, n_tokens, chunk_size, weights_dir, java_home, rj_ws,
             homelab=False, homelab_url="http://localhost:8888"):
    weights_dir = os.path.abspath(weights_dir)
    
    with open(os.path.join(os.path.dirname(__file__), "phi3_functions_only.py"), "r") as f:
        base_code = f.read()
        
    tokenizer = AutoTokenizer.from_pretrained(
        "microsoft/Phi-3-mini-4k-instruct", trust_remote_code=True
    )
    
    messages = [{"role": "user", "content": prompt}]
    formatted_prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    input_ids = tokenizer.encode(formatted_prompt, add_special_tokens=False)
    
    log(f"Original Prompt: {repr(prompt)}")
    log(f"Input token ids ({len(input_ids)}): {input_ids}")
    
    n_seq = len(input_ids)
    
    layer_kernel = os.path.join(os.path.dirname(__file__), "phi3_layer.py")
    lm_head_kernel = os.path.join(os.path.dirname(__file__), "phi3_lm_head.py")
    
    work_dir = tempfile.mkdtemp(prefix="phi3_split_")
    log(f"Working directory: {work_dir}")
    
    try:
        rj_server = None
        
        def execute_kernel(kernel_path, args_list, dumps_dict):
            nonlocal rj_server
            if homelab:
                if rj_server is None:
                    rj_server = RjHomelabClient(homelab_url)
                    rj_server.start()
                rj_server.run_kernel(kernel_path, args_list, dumps_dict)
            else:
                if rj_server is not None:
                    rj_server.shutdown()
                rj_server = RjServer(java_home=java_home, rj_ws=rj_ws)
                rj_server.start()
                rj_server.run_kernel(kernel_path, args_list, dumps_dict)
        
        log("Reading WTE for prompt embed...")
        wte = np.load(os.path.join(weights_dir, "wte.npy")).astype(np.float32)
        wte = wte.reshape(-1, 3072)
        
        hidden = wte[input_ids]
        hidden_flat = hidden.flatten().tolist()
        write_flat_csv(os.path.join(work_dir, "hidden.csv"), hidden_flat)
        
        # KV cache: sized to fit the full generation (prefill + decode).
        # The GPU kernel indexes k_cache as [seq_pos * 3072 + head * 96 + dim],
        # so it needs (n_seq + n_tokens) * 3072 elements.
        # Use binary sidecars so the JVM fast-path avoids parsing large zero CSVs.
        cache_size = (n_seq + n_tokens) * 3072
        cache_zeros_bin = b'\x00' * (cache_size * 4)   # little-endian float32 zeros
        for i in range(32):
            for tag in ("k_cache", "v_cache"):
                csv_path = os.path.join(work_dir, f"l{i}_{tag}.csv")
                bin_path = os.path.join(work_dir, f"l{i}_{tag}.bin")
                write_flat_csv(csv_path, [0.0])          # stub — bin determines size
                with open(bin_path, 'wb') as f:
                    f.write(cache_zeros_bin)

        cur_n_seq = float(n_seq)
        
        common_args = [
            os.path.join(weights_dir, "cos_cache.csv"),
            os.path.join(weights_dir, "sin_cache.csv"),
        ]
        
        # PREFILL
        log("=== PREFILL ===")
        write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq), 0.0, cur_n_seq])
        
        kc_path = os.path.join(work_dir, "k_cache.csv")
        vc_path = os.path.join(work_dir, "v_cache.csv")

        for chunk_start in range(0, 32, chunk_size):
            actual_chunk_size = min(chunk_size, 32 - chunk_start)
            log(f"Prefill Layers {chunk_start} to {chunk_start + actual_chunk_size - 1}")
            
            # Symlink layer weights
            for k in range(actual_chunk_size):
                l = chunk_start + k
                for stem in LAYER_WEIGHT_STEMS:
                    for ext in ("csv", "bin"):
                        src = os.path.join(weights_dir, f"l{l}_{stem}.{ext}")
                        dst = os.path.join(work_dir, f"l{k}_{stem}.{ext}")
                        if os.path.lexists(dst): os.remove(dst)
                        if os.path.exists(src): os.symlink(src, dst)
                for tag in ("k_cache", "v_cache"):
                    for ext in ("csv", "bin"):
                        src = os.path.join(work_dir, f"l{l}_{tag}.{ext}")
                        dst = os.path.join(work_dir, f"l{k}_{tag}.{ext}")
                        if src != dst:
                            # Save canonical regular file before replacing with symlink
                            if os.path.exists(dst) and not os.path.islink(dst):
                                save = os.path.join(work_dir, f"_save_l{k}_{tag}.{ext}")
                                os.rename(dst, save)
                            elif os.path.lexists(dst):
                                os.remove(dst)
                            if os.path.exists(src): os.symlink(src, dst)
                        else:
                            # k == l: restore saved canonical file, or remove stale symlink
                            save = os.path.join(work_dir, f"_save_l{k}_{tag}.{ext}")
                            if os.path.exists(save):
                                if os.path.lexists(dst): os.remove(dst)
                                os.rename(save, dst)
                            elif os.path.islink(dst):
                                os.remove(dst)

            layer_args = [
                os.path.join(work_dir, "hidden.csv"),
                os.path.join(work_dir, "params.csv"),
            ] + common_args
            
            for k in range(actual_chunk_size):
                layer_args.append(os.path.join(work_dir, f"l{k}_k_cache.csv"))
                layer_args.append(os.path.join(work_dir, f"l{k}_v_cache.csv"))
            for k in range(actual_chunk_size):
                for stem in LAYER_WEIGHT_STEMS:
                    layer_args.append(os.path.join(work_dir, f"l{k}_{stem}.csv"))

            dump_vars = {
                "hidden": os.path.join(work_dir, "hidden.csv"),
            }
            for k in range(actual_chunk_size):
                l = chunk_start + k
                dump_vars[f"l{k}_k_cache"] = os.path.join(work_dir, f"l{l}_k_cache.csv")
                dump_vars[f"l{k}_v_cache"] = os.path.join(work_dir, f"l{l}_v_cache.csv")

            chunk_script_path = os.path.join(work_dir, f"chunk_{actual_chunk_size}.py")
            if not os.path.exists(chunk_script_path):
                with open(chunk_script_path, "w") as f:
                    f.write(generate_chunk_script(actual_chunk_size, base_code))
                    
            execute_kernel(chunk_script_path, layer_args, dump_vars)

            # Remove stale .bin sidecars so the next JVM reads fresh CSV dumps
            for k in range(actual_chunk_size):
                l = chunk_start + k
                for tag in ("k_cache", "v_cache"):
                    bin_path = os.path.join(work_dir, f"l{l}_{tag}.bin")
                    if os.path.exists(bin_path):
                        os.remove(bin_path)
            
        # LM Head
        log(f"Prefill LM Head")
        out_argmax = os.path.join(work_dir, "argmax_arr.csv")
        # Write a sentinel so argmax_arr is CSV-backed (always in Java Array.values map)
        write_flat_csv(out_argmax, [-1.0])
        lm_args = [
            os.path.join(work_dir, "hidden.csv"),
            os.path.join(work_dir, "params.csv"),
            os.path.join(weights_dir, "lm_head_1.csv"),
            os.path.join(weights_dir, "lm_head_2.csv"),
            os.path.join(weights_dir, "ln_f_g.csv"),
            out_argmax,
        ]
        execute_kernel(lm_head_kernel, lm_args, {"argmax_arr": out_argmax})
        
        argmax_val = read_flat_csv(out_argmax)
        next_tok = int(argmax_val[0])
        gen_tokens = [next_tok]
        log(f"Token: {next_tok}")

        # Read back the GPU-synced hidden state written by the last prefill chunk.
        # This is the full n_seq x 3072 context the decode kernels will extend.
        hidden_buf = read_flat_csv(os.path.join(work_dir, "hidden.csv"))
        
        # DECODE
        log("=== DECODE ===")
        for step in range(n_tokens - 1):
            cur_n_seq += 1.0
            write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq), 1.0, cur_n_seq])
            
            # Embed next token and append to the full rolling hidden buffer.
            # Decode kernels address hidden at row (cur_n_seq-1)*3072, so the
            # buffer must cover all rows 0..cur_n_seq-1.
            h_next = wte[next_tok].flatten().tolist()  # 3072 elements
            hidden_buf = hidden_buf + h_next
            write_flat_csv(os.path.join(work_dir, "hidden.csv"), hidden_buf)
            
            for chunk_start in range(0, 32, chunk_size):
                actual_chunk_size = min(chunk_size, 32 - chunk_start)
                log(f"Decode Step {step+1} Layers {chunk_start} to {chunk_start + actual_chunk_size - 1}")
                
                # Symlink layer weights
                for k in range(actual_chunk_size):
                    l = chunk_start + k
                    for stem in LAYER_WEIGHT_STEMS:
                        for ext in ("csv", "bin"):
                            src = os.path.join(weights_dir, f"l{l}_{stem}.{ext}")
                            dst = os.path.join(work_dir, f"l{k}_{stem}.{ext}")
                            if os.path.lexists(dst): os.remove(dst)
                            if os.path.exists(src): os.symlink(src, dst)
                    for tag in ("k_cache", "v_cache"):
                        for ext in ("csv", "bin"):
                            src = os.path.join(work_dir, f"l{l}_{tag}.{ext}")
                            dst = os.path.join(work_dir, f"l{k}_{tag}.{ext}")
                            if src != dst:
                                # Save canonical regular file before replacing with symlink
                                if os.path.exists(dst) and not os.path.islink(dst):
                                    save = os.path.join(work_dir, f"_save_l{k}_{tag}.{ext}")
                                    os.rename(dst, save)
                                elif os.path.lexists(dst):
                                    os.remove(dst)
                                if os.path.exists(src): os.symlink(src, dst)
                            else:
                                # k == l: restore saved canonical file, or remove stale symlink
                                save = os.path.join(work_dir, f"_save_l{k}_{tag}.{ext}")
                                if os.path.exists(save):
                                    if os.path.lexists(dst): os.remove(dst)
                                    os.rename(save, dst)
                                elif os.path.islink(dst):
                                    os.remove(dst)

                layer_args = [
                    os.path.join(work_dir, "hidden.csv"),
                    os.path.join(work_dir, "params.csv"),
                ] + common_args
                
                for k in range(actual_chunk_size):
                    layer_args.append(os.path.join(work_dir, f"l{k}_k_cache.csv"))
                    layer_args.append(os.path.join(work_dir, f"l{k}_v_cache.csv"))
                for k in range(actual_chunk_size):
                    for stem in LAYER_WEIGHT_STEMS:
                        layer_args.append(os.path.join(work_dir, f"l{k}_{stem}.csv"))

                dump_vars = {
                    "hidden": os.path.join(work_dir, "hidden.csv"),
                }
                for k in range(actual_chunk_size):
                    l = chunk_start + k
                    dump_vars[f"l{k}_k_cache"] = os.path.join(work_dir, f"l{l}_k_cache.csv")
                    dump_vars[f"l{k}_v_cache"] = os.path.join(work_dir, f"l{l}_v_cache.csv")

                chunk_script_path = os.path.join(work_dir, f"chunk_{actual_chunk_size}.py")
                if not os.path.exists(chunk_script_path):
                    with open(chunk_script_path, "w") as f:
                        f.write(generate_chunk_script(actual_chunk_size, base_code))
                        
                try:
                    execute_kernel(chunk_script_path, layer_args, dump_vars)
                except Exception as e:
                    log(f"Error occurred while executing chunk kernel: {e}")
                    raise

                # Remove stale .bin sidecars so the next JVM reads fresh CSV dumps
                for k in range(actual_chunk_size):
                    l = chunk_start + k
                    for tag in ("k_cache", "v_cache"):
                        bin_path = os.path.join(work_dir, f"l{l}_{tag}.bin")
                        if os.path.exists(bin_path):
                            os.remove(bin_path)

            # Read back GPU-synced hidden after this chunk (needed for next step).
            hidden_buf = read_flat_csv(os.path.join(work_dir, "hidden.csv"))

            # LM Head
            log(f"Decode Step {step+1} LM Head")
            write_flat_csv(out_argmax, [-1.0])  # reset sentinel for CSV-backed argmax_arr
            execute_kernel(lm_head_kernel, lm_args, {"argmax_arr": out_argmax})
            next_tok = int(read_flat_csv(out_argmax)[0])
            gen_tokens.append(next_tok)
            log(f"Token: {next_tok} -> {tokenizer.decode([next_tok])}")
            
        log(f"Final output:\n{tokenizer.decode(gen_tokens)}")
        
    finally:
        if rj_server is not None:
            rj_server.shutdown()
        shutil.rmtree(work_dir, ignore_errors=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("prompt", type=str)
    parser.add_argument("--n-tokens", type=int, default=4)
    parser.add_argument("--chunk-size", type=int, default=1)
    parser.add_argument("--weights-dir", default="phi3_weights_csv")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME", "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home"))
    parser.add_argument("--rj-ws", default=os.environ.get("RAMANUJAN_WS", "/tmp"))
    parser.add_argument("--homelab", action="store_true")
    parser.add_argument("--homelab-url", default="http://localhost:8888")
    args = parser.parse_args()

    generate_split(args.prompt, args.n_tokens, args.chunk_size, args.weights_dir, args.java_home, args.rj_ws,
             homelab=args.homelab, homelab_url=args.homelab_url)

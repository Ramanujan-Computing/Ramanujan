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

def link_layer_weights(l, weights_dir, work_dir):
    """Symlink per-layer weight and cache files to bare names the kernel expects."""
    for stem in LAYER_WEIGHT_STEMS:
        for ext in ("csv", "bin"):
            src = os.path.join(weights_dir, f"l{l}_{stem}.{ext}")
            dst = os.path.join(work_dir, f"{stem}.{ext}")
            if os.path.lexists(dst):
                os.remove(dst)
            if os.path.exists(src):
                os.symlink(src, dst)
    for name in ("k_cache", "v_cache"):
        for ext in ("csv", "bin"):
            src = os.path.join(work_dir, f"l{l}_{name}.{ext}")
            dst = os.path.join(work_dir, f"{name}.{ext}")
            if os.path.lexists(dst):
                os.remove(dst)
            os.symlink(src, dst)

def generate_split(prompt, n_tokens, weights_dir, java_home, rj_ws,
             homelab=False, homelab_url="http://localhost:8888"):
    weights_dir = os.path.abspath(weights_dir)
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

        for l in range(32):
            log(f"Prefill Layer {l}")
            link_layer_weights(l, weights_dir, work_dir)
            layer_args = [
                os.path.join(work_dir, "hidden.csv"),
                os.path.join(work_dir, "params.csv"),
                kc_path,
                vc_path,
            ] + common_args + [
                os.path.join(work_dir, "qkv_packed.csv"),
                os.path.join(work_dir, "qkv_scales.csv"),
                os.path.join(work_dir, "o_packed.csv"),
                os.path.join(work_dir, "o_scales.csv"),
                os.path.join(work_dir, "gate_up_packed.csv"),
                os.path.join(work_dir, "gate_up_scales.csv"),
                os.path.join(work_dir, "down_packed.csv"),
                os.path.join(work_dir, "down_scales.csv"),
                os.path.join(work_dir, "ln1_g.csv"),
                os.path.join(work_dir, "ln2_g.csv"),
            ]

            dump_vars = {
                "hidden": os.path.join(work_dir, "hidden.csv"),
                "k_cache": kc_path,
                "v_cache": vc_path,
            }
            execute_kernel(layer_kernel, layer_args, dump_vars)
            
        # LM Head
        log(f"Prefill LM Head")
        lm_args = [
            os.path.join(work_dir, "hidden.csv"),
            os.path.join(work_dir, "params.csv"),
            os.path.join(weights_dir, "lm_head_1.csv"),
            os.path.join(weights_dir, "lm_head_2.csv"),
            os.path.join(weights_dir, "ln_f_g.csv"),
        ]
        out_argmax = os.path.join(work_dir, "argmax.csv")
        execute_kernel(lm_head_kernel, lm_args, {"argmax_arr": out_argmax})
        
        argmax_val = read_flat_csv(out_argmax)
        next_tok = int(argmax_val[0])
        gen_tokens = [next_tok]
        log(f"Token: {next_tok}")
        
        # DECODE
        log("=== DECODE ===")
        for step in range(n_tokens - 1):
            cur_n_seq += 1.0
            write_flat_csv(os.path.join(work_dir, "params.csv"), [float(n_seq), 1.0, cur_n_seq])
            
            # Embed next token
            if next_tok < 16000:
                h_next = wte[next_tok].flatten().tolist()
            else:
                h_next = wte[next_tok].flatten().tolist()
            
            # Append to hidden? No, decode hidden is just 1x3072!
            write_flat_csv(os.path.join(work_dir, "hidden.csv"), h_next)
            
            for l in range(32):
                log(f"Decode Step {step+1} Layer {l}")
                link_layer_weights(l, weights_dir, work_dir)
                layer_args = [
                    os.path.join(work_dir, "hidden.csv"),
                    os.path.join(work_dir, "params.csv"),
                    kc_path,
                    vc_path,
                ] + common_args + [
                    os.path.join(work_dir, "qkv_packed.csv"),
                    os.path.join(work_dir, "qkv_scales.csv"),
                    os.path.join(work_dir, "o_packed.csv"),
                    os.path.join(work_dir, "o_scales.csv"),
                    os.path.join(work_dir, "gate_up_packed.csv"),
                    os.path.join(work_dir, "gate_up_scales.csv"),
                    os.path.join(work_dir, "down_packed.csv"),
                    os.path.join(work_dir, "down_scales.csv"),
                    os.path.join(work_dir, "ln1_g.csv"),
                    os.path.join(work_dir, "ln2_g.csv"),
                ]
                dump_vars = {
                    "hidden": os.path.join(work_dir, "hidden.csv"),
                    "k_cache": kc_path,
                    "v_cache": vc_path,
                }
                try:
                    execute_kernel(layer_kernel, layer_args, dump_vars)
                except Exception as e:
                    log(f"Error occurred while executing layer kernel: {e}")
                    raise

            # LM Head
            log(f"Decode Step {step+1} LM Head")
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
    parser.add_argument("--weights-dir", default="phi3_weights_csv")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME", "/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home"))
    parser.add_argument("--rj-ws", default=os.environ.get("RAMANUJAN_WS", "/tmp"))
    parser.add_argument("--homelab", action="store_true")
    parser.add_argument("--homelab-url", default="http://localhost:8888")
    args = parser.parse_args()

    generate_split(args.prompt, args.n_tokens, args.weights_dir, args.java_home, args.rj_ws,
             homelab=args.homelab, homelab_url=args.homelab_url)

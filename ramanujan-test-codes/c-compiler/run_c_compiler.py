#!/usr/bin/env python3
"""
C-to-AArch64 Assembly Compiler
Host orchestrator: tokenizes C source, drives Ramanujan JVM kernel,
decodes output to assembly, assembles and runs the result.
"""

import os
import sys
import argparse
import tempfile
import subprocess
import shutil
import time
import select
import threading

# Token codes (must match kernel)
TK_EOF = 0
TK_IF = 2
TK_ELSE = 3
TK_FOR = 4
TK_INT = 5
TK_LBRACE = 6
TK_RBRACE = 7
TK_LPAREN = 8
TK_RPAREN = 9
TK_SEMI = 10
TK_EQ = 11
TK_EQEQ = 12
TK_LT = 13
TK_GT = 22
TK_LTEQ = 23
TK_GTEQ = 24
TK_NOTEQ = 25
TK_PLUS = 14
TK_MINUS = 15
TK_STAR = 16
TK_SLASH = 17
TK_PLUSPLUS = 18
TK_IDENT = 20
TK_NUMBER = 21

# Opcode constants
OP_MOV_REG = 1
OP_MOV_IMM = 2
OP_ADD_R = 3
OP_SUB_R = 4
OP_MUL_R = 5
OP_SDIV_R = 6
OP_CMP = 7
OP_B = 8
OP_BEQ = 9
OP_BNE = 10
OP_BLT = 11
OP_BGT = 12
OP_BLE = 13
OP_BGE = 14
OP_LABEL = 17
OP_INC = 18

KEYWORDS = {
    'if': TK_IF,
    'else': TK_ELSE,
    'for': TK_FOR,
    'int': TK_INT,
}

def tokenize(source: str):
    """Tokenize C source into integer codes and values."""
    tokens = []
    token_vals = []
    ident_hash_map = {}

    i = 0
    while i < len(source):
        c = source[i]

        # Skip whitespace
        if c.isspace():
            i += 1
            continue

        # Two-character tokens
        two = source[i:i+2] if i+1 < len(source) else ''
        if two == '==':
            tokens.append(TK_EQEQ)
            token_vals.append(0.0)
            i += 2
            continue
        if two == '++':
            tokens.append(TK_PLUSPLUS)
            token_vals.append(0.0)
            i += 2
            continue
        if two == '<=':
            tokens.append(TK_LTEQ)
            token_vals.append(0.0)
            i += 2
            continue
        if two == '>=':
            tokens.append(TK_GTEQ)
            token_vals.append(0.0)
            i += 2
            continue
        if two == '!=':
            tokens.append(TK_NOTEQ)
            token_vals.append(0.0)
            i += 2
            continue

        # Single-character tokens
        one_map = {
            '{': TK_LBRACE, '}': TK_RBRACE,
            '(': TK_LPAREN, ')': TK_RPAREN,
            ';': TK_SEMI,   '=': TK_EQ,
            '<': TK_LT,     '>': TK_GT,
            '+': TK_PLUS,   '-': TK_MINUS,
            '*': TK_STAR,   '/': TK_SLASH,
        }

        if c in one_map:
            tokens.append(one_map[c])
            token_vals.append(0.0)
            i += 1
            continue

        # Number literals
        if c.isdigit() or (c == '.' and i+1 < len(source) and source[i+1].isdigit()):
            j = i
            while j < len(source) and (source[j].isdigit() or source[j] == '.'):
                j += 1
            tokens.append(TK_NUMBER)
            token_vals.append(float(source[i:j]))
            i = j
            continue

        # Keywords and identifiers
        if c.isalpha() or c == '_':
            j = i
            while j < len(source) and (source[j].isalnum() or source[j] == '_'):
                j += 1
            word = source[i:j]

            if word in KEYWORDS:
                tokens.append(KEYWORDS[word])
                token_vals.append(0.0)
            else:
                # Assign unique hash to identifier
                if word not in ident_hash_map:
                    ident_hash_map[word] = float(hash(word) & 0x7FFFFFFF)
                tokens.append(TK_IDENT)
                token_vals.append(ident_hash_map[word])

            i = j
            continue

        # Unknown character; skip
        i += 1

    tokens.append(TK_EOF)
    token_vals.append(0.0)

    return tokens, token_vals


def write_flat_csv(path: str, values):
    """Write flat array as single-line CSV."""
    with open(path, 'w') as f:
        f.write(','.join(repr(float(v)) for v in values))
        f.write('\n')


def read_flat_csv(path: str) -> list:
    """Read single-line CSV as float array."""
    with open(path) as f:
        text = f.read().strip()
    if not text:
        return []
    return [float(t) for t in text.split(',') if t]


class RjServer:
    """Manages Ramanujan JVM server lifecycle."""

    def __init__(self, java_home: str, rj_ws: str):
        self.java_home = java_home
        self.rj_ws = rj_ws
        self.proc = None

    def start(self, timeout=60):
        """Start the JVM server and wait for SERVER_READY."""
        java_bin = os.path.join(self.java_home, 'bin', 'java')
        rj_jar = os.environ.get(
            'RAMANUJAN_FAT_JAR',
            os.path.expanduser('~/Desktop/ws/developer-console-1.0-SNAPSHOT-fat.jar')
        )

        if not os.path.exists(rj_jar):
            print(f"Error: JAR not found at {rj_jar}", file=sys.stderr)
            print("Set RAMANUJAN_FAT_JAR or ensure it exists at the default location", file=sys.stderr)
            sys.exit(1)

        cmd = [java_bin, '-Xmx2g', '-XX:+UseG1GC', '-jar', rj_jar, 'server']
        env = os.environ.copy()
        env['JAVA_HOME'] = self.java_home
        env['RAMANUJAN_WS'] = self.rj_ws

        try:
            self.proc = subprocess.Popen(
                cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                stderr=subprocess.PIPE, text=True, bufsize=1, env=env
            )
        except Exception as e:
            print(f"Error starting JVM: {e}", file=sys.stderr)
            sys.exit(1)

        # Drain stderr in background
        threading.Thread(target=self._drain_stderr, daemon=True).start()

        # Wait for SERVER_READY
        deadline = time.time() + timeout
        while time.time() < deadline:
            line = self.proc.stdout.readline()
            if line.rstrip() == 'SERVER_READY':
                return
            if not line:
                break

        print("Error: Server did not start in time", file=sys.stderr)
        sys.exit(1)

    def _drain_stderr(self):
        """Drain stderr in background thread."""
        try:
            for line in self.proc.stderr:
                sys.stderr.write('[JVM] ' + line)
        except:
            pass

    def run_kernel(self, kernel_py, csv_args, dump_vars, timeout=120):
        """Run kernel and dump variables."""
        args_str = ' '.join([kernel_py] + csv_args)
        dump_names = ' '.join(dump_vars.keys())
        full_cmd = f'run {args_str} --dump {dump_names}'
        self.proc.stdin.write(f'{full_cmd}\n')
        self.proc.stdin.flush()
        print(args_str)
        deadline = time.time() + timeout
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                raise RuntimeError('KERNEL_TIMEOUT')
            ready, _, _ = select.select([self.proc.stdout], [], [], min(remaining, 1.0))
            if ready:
                line = self.proc.stdout.readline().rstrip()
                if line == 'KERNEL_DONE':
                    break
                if line.startswith('KERNEL_ERROR'):
                    raise RuntimeError(line)

        # Dump variables
        for name, path in dump_vars.items():
            self.proc.stdin.write(f'dump {name} {path}\n')
            self.proc.stdin.flush()
            ddl = time.time() + 30
            while True:
                remaining = ddl - time.time()
                if remaining <= 0:
                    raise RuntimeError(f'DUMP_TIMEOUT for {name}')
                ready, _, _ = select.select([self.proc.stdout], [], [], min(remaining, 1.0))
                if ready:
                    dline = self.proc.stdout.readline()
                    if not dline:
                        raise RuntimeError(f'JVM closed during dump {name}')
                    dline_str = dline.rstrip()
                    print(f'[JVM dump] {dline_str}')
                    if dline_str.startswith('Dumped'):
                        break
                    if dline_str.startswith('Error') or dline_str.startswith('Array not found'):
                        raise RuntimeError(f'JVM error during dump: {dline_str}')

    def shutdown(self):
        """Shut down the JVM server."""
        if self.proc:
            try:
                self.proc.stdin.write('quit\n')
                self.proc.stdin.flush()
                self.proc.wait(timeout=10)
            except:
                pass


REG_NAMES = {
    0: 'x0', 1: 'x1', 2: 'x2', 3: 'x3', 4: 'x4', 5: 'x5',
    6: 'x6', 7: 'x7', 8: 'x8', 9: 'x9', 10: 'x10', 11: 'x11',
    12: 'x12', 13: 'x13', 14: 'x14', 15: 'x15', 16: 'x16', 17: 'x17',
}

OP_NAMES = {
    OP_MOV_REG: 'mov', OP_MOV_IMM: 'mov_imm',
    OP_ADD_R: 'add', OP_SUB_R: 'sub', OP_MUL_R: 'mul', OP_SDIV_R: 'sdiv',
    OP_CMP: 'cmp', OP_B: 'b', OP_BEQ: 'b.eq', OP_BNE: 'b.ne',
    OP_BLT: 'b.lt', OP_BGT: 'b.gt', OP_BLE: 'b.le', OP_BGE: 'b.ge',
    OP_LABEL: 'label', OP_INC: 'inc',
}


def decode_assembly(out_op, out_dst, out_src, count):
    """Convert integer instruction tuples to AArch64 assembly text."""
    lines = []

    for i in range(int(count)):
        op = int(out_op[i])
        dst = int(out_dst[i])
        src = int(out_src[i])

        # Label
        if op == OP_LABEL:
            lines.append(f'.L{dst}:')
            continue

        # Unconditional branch
        if op == OP_B:
            lines.append(f'    b        .L{dst}')
            continue

        # Conditional branches
        if op in (OP_BEQ, OP_BNE, OP_BLT, OP_BGT, OP_BLE, OP_BGE):
            cc = OP_NAMES.get(op, '?')
            lines.append(f'    {cc:<8} .L{dst}')
            continue

        # MOV immediate
        if op == OP_MOV_IMM:
            dst_reg = REG_NAMES.get(dst, f'x{dst}')
            lines.append(f'    mov      {dst_reg}, #{src}')
            continue

        # MOV register
        if op == OP_MOV_REG:
            dst_reg = REG_NAMES.get(dst, f'x{dst}')
            src_reg = REG_NAMES.get(src, f'x{src}')
            lines.append(f'    mov      {dst_reg}, {src_reg}')
            continue

        # Arithmetic (two registers)
        if op in (OP_ADD_R, OP_SUB_R, OP_MUL_R, OP_SDIV_R):
            op_name = OP_NAMES.get(op, f'op{op}')
            dst_reg = REG_NAMES.get(dst, f'x{dst}')
            src_reg = REG_NAMES.get(src, f'x{src}')
            lines.append(f'    {op_name:<8} {dst_reg}, {dst_reg}, {src_reg}')
            continue

        # CMP
        if op == OP_CMP:
            dst_reg = REG_NAMES.get(dst, f'x{dst}')
            src_reg = REG_NAMES.get(src, f'x{src}')
            lines.append(f'    cmp      {dst_reg}, {src_reg}')
            continue

        # INC (add #1)
        if op == OP_INC:
            dst_reg = REG_NAMES.get(dst, f'x{dst}')
            lines.append(f'    add      {dst_reg}, {dst_reg}, #1')
            continue

    return '\n'.join(lines)


def assemble_and_run(asm_text: str, label: str):
    """Assemble and run AArch64 assembly; return exit code."""
    with tempfile.TemporaryDirectory() as d:
        s_path = os.path.join(d, 'out.s')
        exe_path = os.path.join(d, 'out')

        with open(s_path, 'w') as f:
            f.write(asm_text)

        try:
            subprocess.run(
                ['clang', '-arch', 'arm64', '-o', exe_path, s_path],
                check=True, capture_output=True
            )
        except subprocess.CalledProcessError as e:
            print(f"Assembler/linker error:\n{e.stderr.decode()}", file=sys.stderr)
            return -1

        try:
            result = subprocess.run([exe_path], capture_output=True, timeout=5)
            exit_code = result.returncode
            print(f"[{label}] exit code: {exit_code}")
            if result.stdout:
                print(f"    stdout: {result.stdout.decode()}")
            if result.stderr:
                print(f"    stderr: {result.stderr.decode()}")
            return exit_code
        except subprocess.TimeoutExpired:
            print(f"[{label}] timeout", file=sys.stderr)
            return -1
        except Exception as e:
            print(f"[{label}] execution error: {e}", file=sys.stderr)
            return -1


def build_asm_scaffold(generated_asm: str, last_var_reg: int):
    """Wrap generated assembly in a minimal main function."""
    # The generated code has set up the last declared variable in some register.
    # Move it to x0 and return.
    last_reg = REG_NAMES.get(last_var_reg, f'x{last_var_reg}')

    scaffold = f""".global _main
.align 2
_main:
    stp      x29, x30, [sp, #-16]!
    mov      x29, sp

{generated_asm}

    mov      x0, {last_reg}
    ldp      x29, x30, [sp], #16
    ret
"""
    return scaffold


def main():
    parser = argparse.ArgumentParser(description='C-to-AArch64 Assembly Compiler')
    parser.add_argument('source', help='Input .c file')
    parser.add_argument('--out', default=None, help='Output .s file (default: stdout)')
    parser.add_argument('--java-home',
        default=os.environ.get('JAVA_HOME',
            '/Users/pranav/Library/Java/JavaVirtualMachines/corretto-1.8.0_402/Contents/Home'))
    parser.add_argument('--rj-ws', default=os.environ.get('RAMANUJAN_WS', '/tmp'))
    parser.add_argument('--skip-run', action='store_true', help='Skip assembly and execution')

    args = parser.parse_args()

    # Read source
    if not os.path.exists(args.source):
        print(f"Error: source file not found: {args.source}", file=sys.stderr)
        sys.exit(1)

    with open(args.source) as f:
        source = f.read()

    print(f"[*] Tokenizing {args.source}")
    tokens, token_vals = tokenize(source)
    n = len(tokens)
    print(f"[*] {n} tokens")

    work_dir = tempfile.mkdtemp(prefix='rj_cc_')

    try:
        tokens_csv = os.path.join(work_dir, 'tokens.csv')
        tvals_csv = os.path.join(work_dir, 'token_vals.csv')
        meta_csv = os.path.join(work_dir, 'meta.csv')
        out_op_csv = os.path.join(work_dir, 'out_op.csv')
        out_dst_csv = os.path.join(work_dir, 'out_dst.csv')
        out_src_csv = os.path.join(work_dir, 'out_src.csv')

        print(f"[*] Writing CSVs to {work_dir}")
        write_flat_csv(tokens_csv, tokens)
        write_flat_csv(tvals_csv, token_vals)
        write_flat_csv(meta_csv, [float(n)] + [0.0] * 7)
        # Pre-allocate output arrays as CSV inputs so kernel can write to them
        write_flat_csv(out_op_csv, [0.0] * 2048)
        write_flat_csv(out_dst_csv, [0.0] * 2048)
        write_flat_csv(out_src_csv, [0.0] * 2048)

        # Find kernel file
        kernel_dir = os.path.dirname(args.source)
        kernel_path = os.path.join(kernel_dir, 'c_compiler_kernel.py')
        if not os.path.exists(kernel_path):
            kernel_path = os.path.join(os.path.dirname(__file__), 'c_compiler_kernel.py')

        if not os.path.exists(kernel_path):
            print(f"Error: kernel not found at {kernel_path}", file=sys.stderr)
            sys.exit(1)

        print(f"[*] Starting Ramanujan JVM")
        rj = RjServer(args.java_home, args.rj_ws)
        rj.start()

        print(f"[*] Running kernel")
        csv_args = [tokens_csv, tvals_csv, meta_csv, out_op_csv, out_dst_csv, out_src_csv]
        dump_vars = {
            'out_op': out_op_csv,
            'out_dst': out_dst_csv,
            'out_src': out_src_csv,
            'meta': meta_csv,
        }

        try:
            rj.run_kernel(kernel_path, csv_args, dump_vars)
        except RuntimeError as e:
            print(f"Kernel error: {e}", file=sys.stderr)
            rj.shutdown()
            sys.exit(1)

        print(f"[*] Reading output")
        meta_out = read_flat_csv(meta_csv)
        instr_count = int(meta_out[1])
        op_vals = read_flat_csv(out_op_csv)
        dst_vals = read_flat_csv(out_dst_csv)
        src_vals = read_flat_csv(out_src_csv)

        print(f"[*] {instr_count} instructions generated")

        rj.shutdown()

        # Decode assembly
        print(f"[*] Decoding assembly")
        asm_text = decode_assembly(op_vals, dst_vals, src_vals, instr_count)

        # Infer last variable reg from last assignment in dst_vals
        # (heuristic: last MOV_REG instruction's dst that is not x0/x1)
        last_var_reg = 2  # default: x2
        for i in range(int(instr_count) - 1, -1, -1):
            if int(op_vals[i]) == OP_MOV_REG:
                d = int(dst_vals[i])
                if d > 1:  # not x0, not x1
                    last_var_reg = d
                    break

        scaffold = build_asm_scaffold(asm_text, last_var_reg)

        # Print/write assembly
        print(f"\n[*] Generated assembly:\n")
        print(scaffold)

        if args.out:
            with open(args.out, 'w') as f:
                f.write(scaffold + '\n')
            print(f"[*] Written to {args.out}")

        # Assemble and run
        if not args.skip_run:
            print(f"\n[*] Assembling and running")
            assemble_and_run(scaffold, os.path.basename(args.source))

    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == '__main__':
    main()

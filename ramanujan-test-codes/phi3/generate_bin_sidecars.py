#!/usr/bin/env python3
"""
generate_bin_sidecars.py
Converts every .csv file in the weights directory to a raw float32 binary
sidecar (.bin).  The JVM binary fast-path in ExecutorImpl.createJson() checks
for a same-name .bin file and, when found, replaces the full CSV string (which
would OOM the heap) with a tiny dimension-stub.

Usage:
    python3 generate_bin_sidecars.py [--weights-dir phi3_weights_csv] [--force]
"""

import argparse
import glob
import os
import struct
import sys
import time


def csv_to_bin(csv_path: str, bin_path: str) -> int:
    """Parse flat float CSV and write raw little-endian float32 binary."""
    with open(csv_path, "r") as f:
        text = f.read()
    # Strip whitespace / newlines, then split on commas
    text = text.strip()
    if not text:
        return 0
    tokens = text.split(",")
    n = len(tokens)
    # Pack as little-endian float32
    buf = struct.pack(f"<{n}f", *[float(t) for t in tokens])
    with open(bin_path, "wb") as f:
        f.write(buf)
    return n


def main():
    parser = argparse.ArgumentParser(description="Generate .bin sidecars for weight CSVs")
    parser.add_argument("--weights-dir", default="phi3_weights_csv",
                        help="Directory containing weight CSV files")
    parser.add_argument("--force", action="store_true",
                        help="Regenerate even if .bin already exists and is newer")
    args = parser.parse_args()

    weights_dir = os.path.abspath(args.weights_dir)
    if not os.path.isdir(weights_dir):
        print(f"ERROR: weights directory not found: {weights_dir}")
        sys.exit(1)

    csv_files = sorted(glob.glob(os.path.join(weights_dir, "*.csv")))
    if not csv_files:
        print("No .csv files found.")
        sys.exit(0)

    print(f"Found {len(csv_files)} CSV files in {weights_dir}")
    total_t0 = time.time()
    skipped = 0
    converted = 0

    for idx, csv_path in enumerate(csv_files, 1):
        bin_path = csv_path[:-4] + ".bin"
        csv_mtime = os.path.getmtime(csv_path)

        if not args.force and os.path.exists(bin_path) and os.path.getmtime(bin_path) >= csv_mtime:
            skipped += 1
            print(f"[{idx}/{len(csv_files)}] SKIP (already up-to-date): {os.path.basename(csv_path)}")
            continue

        t0 = time.time()
        try:
            n = csv_to_bin(csv_path, bin_path)
            elapsed = time.time() - t0
            mb_csv = os.path.getsize(csv_path) / 1024 / 1024
            mb_bin = os.path.getsize(bin_path) / 1024 / 1024
            print(f"[{idx}/{len(csv_files)}] {os.path.basename(csv_path)}: "
                  f"{n} floats, {mb_csv:.1f} MB → {mb_bin:.1f} MB bin  ({elapsed:.2f}s)")
            converted += 1
        except Exception as e:
            print(f"[{idx}/{len(csv_files)}] ERROR {os.path.basename(csv_path)}: {e}")

    total_elapsed = time.time() - total_t0
    print(f"\nDone. Converted: {converted}, Skipped: {skipped}  (total {total_elapsed:.1f}s)")
    print("Re-run inference — the JVM will now use the binary fast-path and avoid OOM.")


if __name__ == "__main__":
    main()

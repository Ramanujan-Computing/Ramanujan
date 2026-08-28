#!/usr/bin/env python3
"""Generate raw float32 sidecars for low-end Phi-3 weight CSV files."""

import argparse
import glob
import os
import sys
import time

import numpy as np


READ_CHUNK_BYTES = 8 * 1024 * 1024


def csv_to_bin(csv_path, bin_path):
    """Convert a flat CSV incrementally without holding the full file in RAM."""
    temp_path = bin_path + ".tmp"
    count = 0
    remainder = ""

    try:
        with open(csv_path, "r", encoding="utf-8") as csv_file, \
                open(temp_path, "wb") as bin_file:
            while True:
                chunk = csv_file.read(READ_CHUNK_BYTES)
                if not chunk:
                    break

                text = remainder + chunk
                split_at = text.rfind(",")
                if split_at < 0:
                    remainder = text
                    continue

                complete = text[:split_at]
                remainder = text[split_at + 1:]
                values = np.fromstring(complete, dtype=np.float32, sep=",")
                values.tofile(bin_file)
                count += values.size

            remainder = remainder.strip()
            if remainder:
                values = np.fromstring(remainder, dtype=np.float32, sep=",")
                values.tofile(bin_file)
                count += values.size

        os.replace(temp_path, bin_path)
        return count
    except Exception:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        raise


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(
        description="Generate low-end Phi-3 .bin sidecars without large RAM use"
    )
    parser.add_argument(
        "--weights-dir",
        default=os.path.join(script_dir, "phi3_weights_csv"),
        help="Directory containing the low-end weight CSV files",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Regenerate sidecars even when they are newer than their CSV files",
    )
    args = parser.parse_args()

    weights_dir = os.path.abspath(args.weights_dir)
    if not os.path.isdir(weights_dir):
        print(f"ERROR: weights directory not found: {weights_dir}", file=sys.stderr)
        return 1

    csv_files = sorted(glob.glob(os.path.join(weights_dir, "*.csv")))
    if not csv_files:
        print(f"ERROR: no CSV files found in {weights_dir}", file=sys.stderr)
        return 1

    converted = 0
    skipped = 0
    started = time.time()
    print(f"Found {len(csv_files)} low-end weight CSV files")

    for index, csv_path in enumerate(csv_files, 1):
        bin_path = os.path.splitext(csv_path)[0] + ".bin"
        if (not args.force and os.path.exists(bin_path)
                and os.path.getsize(bin_path) > 0
                and os.path.getmtime(bin_path) >= os.path.getmtime(csv_path)):
            skipped += 1
            print(f"[{index}/{len(csv_files)}] SKIP {os.path.basename(csv_path)}")
            continue

        item_started = time.time()
        count = csv_to_bin(csv_path, bin_path)
        converted += 1
        bin_mb = os.path.getsize(bin_path) / (1024 * 1024)
        print(
            f"[{index}/{len(csv_files)}] {os.path.basename(csv_path)}: "
            f"{count} floats, {bin_mb:.1f} MiB "
            f"({time.time() - item_started:.1f}s)"
        )

    print(
        f"Done. Converted: {converted}, skipped: {skipped} "
        f"({time.time() - started:.1f}s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env bash
# watch_train.sh  – poll TinyBERT training progress every 10 seconds.
# Usage:  bash watch_train.sh
# Press Ctrl-C to detach; training keeps running in the background.

TINYBERT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WEIGHTS="$TINYBERT/weights.json"
META="$TINYBERT/train_meta.json"
INTERVAL=10
start_ts=$SECONDS
prev_dag=0
prev_ts=$SECONDS

# Read expected totals from meta file written by extract_weights.py
TOTAL_CYCLES=30
APPROX_DAGS_TOTAL=990
if [[ -f "$META" ]]; then
    _c=$(python3 -c "import json; m=json.load(open('$META')); print(m.get('cycles',30))" 2>/dev/null)
    _d=$(python3 -c "import json; m=json.load(open('$META')); print(m.get('approx_dags_total',990))" 2>/dev/null)
    [[ -n "$_c" ]] && TOTAL_CYCLES=$_c
    [[ -n "$_d" ]] && APPROX_DAGS_TOTAL=$_d
fi
DAGS_PER_CYCLE=$(( APPROX_DAGS_TOTAL / TOTAL_CYCLES ))   # ≈ 33

echo "=========================================="
echo "  TinyBERT training watcher"
echo "  dir : $TINYBERT"
echo "  poll: every ${INTERVAL}s   Ctrl-C to detach"
echo "=========================================="

while true; do
    sleep $INTERVAL

    log=$(ls -t "$TINYBERT"/.rj_output_*.txt 2>/dev/null | head -1)
    alive=$(pgrep -f "developer-console" | wc -l | tr -d ' ')
    elapsed=$(( SECONDS - start_ts ))
    elapsed_fmt=$(printf '%02d:%02d' $(( elapsed / 60 )) $(( elapsed % 60 )))

    if [[ -n "$log" ]]; then
        dag=$(grep -c "completed" "$log" 2>/dev/null); dag=${dag:-0}
        lines=$(wc -l < "$log" 2>/dev/null | tr -d ' '); lines=${lines:-0}
        dt=$(( SECONDS - prev_ts ))
        if [[ $dt -gt 0 ]]; then
            rate=$(( (dag - prev_dag) * 60 / dt ))
        else
            rate=0
        fi
        prev_dag=$dag
        prev_ts=$SECONDS
        sz=$(stat -f%z "$WEIGHTS" 2>/dev/null); sz=${sz:-0}
        # estimated cycle (clamp to TOTAL_CYCLES)
        est_cycle=$(( dag / (DAGS_PER_CYCLE > 0 ? DAGS_PER_CYCLE : 1) ))
        (( est_cycle > TOTAL_CYCLES )) && est_cycle=$TOTAL_CYCLES
        echo "[${elapsed_fmt}]  dag=${dag}  cycle≈${est_cycle}/${TOTAL_CYCLES}  rate=~${rate}/min  log_lines=${lines}  weights=${sz}B  JVM=${alive}"
    else
        echo "[${elapsed_fmt}]  waiting for log…  JVM=${alive}"
    fi

    # ── detect completion ──────────────────────────────────────────────────
    if [[ "$alive" == "0" ]]; then
        echo ""
        echo "=== JVM stopped after ${elapsed_fmt} ==="

        if [[ -f "$WEIGHTS" ]]; then
            python3 - "$WEIGHTS" <<'PY'
import json, os, sys
p = sys.argv[1]
w = json.load(open(p))
nz    = sum(1 for a in w.get('weights', {}).values() for v in a if v != 0.0)
total = sum(len(a) for a in w.get('weights', {}).values())
sz    = os.path.getsize(p)
loss  = w.get('scalars', {}).get('avg_loss', 'N/A')
print(f"  Non-zero weights : {nz:,} / {total:,}")
print(f"  avg_loss         : {loss}")
print(f"  weights.json     : {sz:,} bytes")
if nz == 0:
    print("  [WARN] All weights are zero – check train_log_latest.txt")
else:
    print("  [OK]  Training produced non-zero weights")
PY
        else
            echo "  [WARN] weights.json not found"
        fi

        log_keep="$TINYBERT/train_log_latest.txt"
        if [[ -f "$log_keep" ]]; then
            console_lines=$(grep -c "Query Console\|\[" "$log_keep" 2>/dev/null || echo 0)
            echo "  Log  : $log_keep  (${console_lines} console/marker lines)"
        fi

        echo ""
        echo "  Next step: cd $TINYBERT && python3 inference.py"
        break
    fi

    sleep "$INTERVAL"
done

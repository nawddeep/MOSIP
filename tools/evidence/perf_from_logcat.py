#!/usr/bin/env python3
"""Extract PERFORMANCE lines from a saved `adb logcat -s FaceLivenessAudit` file (standard library only).

Line format (from LivenessAuditRecord.toCompactString, written by AuditManagerLivenessLogger in debug builds):
  ...FaceLivenessAudit...: type=PERFORMANCE;ts=..;session=..;workflow=..;attempt=..;outcome=..;
                           framesAnalysed=N;framesWithPad=M;meanMs=..;p95Ms=..;maxMs=..;engine=..
Each line is one window of frames (flushed per session), not one frame. So:
  frames   = sum of framesAnalysed
  mean ms  = mean of the windows' meanMs weighted by framesAnalysed (exact)
  p95 ms   = a true p95 cannot be rebuilt from window p95s. Two honest bounds are printed:
             frame-weighted average of window p95s, and the worst window p95.
Usage: perf_from_logcat.py logcat.txt
"""
import re, sys


def parse(line):
    i = line.find("type=PERFORMANCE")
    if i < 0:
        return None
    kv = {}
    for part in line[i:].strip().split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            kv[k] = v
    try:
        return int(kv["framesAnalysed"]), float(kv["meanMs"]), float(kv["p95Ms"]), float(kv["maxMs"]), kv.get("engine", "")
    except (KeyError, ValueError):
        return None


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    rows = []
    with open(sys.argv[1], errors="replace") as f:
        for line in f:
            r = parse(line)
            if r:
                rows.append(r)
    if not rows:
        print("no PERFORMANCE lines found (debug build? FaceLivenessAudit tag? session finished?)")
        sys.exit(1)
    frames = sum(r[0] for r in rows)
    mean = sum(r[0] * r[1] for r in rows) / frames
    p95_avg = sum(r[0] * r[2] for r in rows) / frames
    print(f"windows: {len(rows)}")
    print(f"frames: {frames}")
    print(f"mean ms/frame: {mean:.1f}")
    print(f"p95 ms/frame (frame-weighted average of window p95): {p95_avg:.1f}")
    print(f"p95 ms/frame (worst window): {max(r[2] for r in rows):.1f}")
    print(f"max ms/frame: {max(r[3] for r in rows):.1f}")
    print(f"engine: {rows[0][4]}")


if __name__ == "__main__":
    main()

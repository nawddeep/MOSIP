#!/usr/bin/env python3
"""Summarise ~/mosip-evidence/results.csv (standard library only).

Per client x device x attack_type prints tries / blocked / passed for attacks and
tries / passed / wrongly rejected for bona fide rows.

Counting rules (so the numbers can be audited):
  passed   = final_state SUCCESS
  blocked  = attack row whose final_state is a decision failure (PAD_FAILED, LIVENESS_FAILED,
             CHALLENGE_TIMEOUT, MAX_RETRIES)
  wrongly rejected = bona fide row with one of those same decision failures
  errors   = any other final_state (device/camera/engine error). Reported separately: an error is
             neither a detection nor a pass, so it is never counted as blocked or as a rejection.
Usage: summarise.py [results.csv]
"""
import csv, os, sys
from collections import Counter, defaultdict

DECISION_FAILURES = {"PAD_FAILED", "LIVENESS_FAILED", "CHALLENGE_TIMEOUT", "MAX_RETRIES"}


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/mosip-evidence/results.csv")
    attacks = defaultdict(Counter)
    bona = defaultdict(Counter)
    stages = defaultdict(Counter)
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            key = (r["client"], r["device"], r["workflow"])
            state = r["final_state"].strip()
            if r["subject"].strip().startswith("attack"):
                k = key + (r["attack_type"].strip(),)
                c = attacks[k]
                c["tries"] += 1
                if state == "SUCCESS":
                    c["passed"] += 1
                elif state in DECISION_FAILURES:
                    c["blocked"] += 1
                    stages[k][r["stage_stopped"].strip() or "n/a"] += 1
                else:
                    c["errors"] += 1
            else:
                c = bona[key + (r["subject"].strip(),)]
                c["tries"] += 1
                if state == "SUCCESS":
                    c["passed"] += 1
                elif state in DECISION_FAILURES:
                    c["wrongly_rejected"] += 1
                else:
                    c["errors"] += 1
    print("ATTACKS (client | device | workflow | attack | tries | blocked | passed (should be 0) | errors | stopped at)")
    if not attacks:
        print("  no attack rows")
    for k in sorted(attacks):
        c = attacks[k]
        st = ", ".join(f"{s}:{n}" for s, n in sorted(stages[k].items())) or "-"
        print(" | ".join(k) + f" | {c['tries']} | {c['blocked']} | {c['passed']} | {c['errors']} | {st}")
    print("\nBONA FIDE (client | device | workflow | subject | tries | passed | wrongly rejected | errors)")
    if not bona:
        print("  no bona fide rows")
    for k in sorted(bona):
        c = bona[k]
        print(" | ".join(k) + f" | {c['tries']} | {c['passed']} | {c['wrongly_rejected']} | {c['errors']}")


if __name__ == "__main__":
    main()

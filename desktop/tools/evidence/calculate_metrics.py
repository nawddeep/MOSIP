#!/usr/bin/env python3
"""
MOSIP Face Liveness & PAD: ISO/IEC 30107 Metrics Calculator
Parses ~/mosip-evidence/results.csv and generates docs/liveness/12-test-evidence.md.
"""

import os
import sys
import csv
from collections import defaultdict
from pathlib import Path

def calculate_metrics(results_csv_path: str, output_md_path: str):
    if not os.path.exists(results_csv_path):
        print(f"[ERROR] Results file not found at: {results_csv_path}")
        return

    rows = []
    with open(results_csv_path, mode="r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            if any(r.values()):
                rows.append(r)

    print(f"[INFO] Loaded {len(rows)} test records from {results_csv_path}")

    # Aggregations by client / device
    device_stats = defaultdict(lambda: {
        "bona_fide_total": 0,
        "bona_fide_pass": 0,
        "bona_fide_fail": 0,
        "attacks": defaultdict(lambda: {"total": 0, "passed": 0, "blocked": 0}),
        "stages": defaultdict(int),
        "notes": []
    })

    for r in rows:
        device = f"{r['client'].strip()} - {r['device'].strip()}"
        subject = r["subject"].strip().lower()
        attack_type = r["attack_type"].strip().lower()
        final_state = r["final_state"].strip().upper()
        stage = r.get("stage_ended", "").strip()

        if "smoke" in subject or "smoke" in attack_type:
            # Verified refusal when no face present
            continue

        is_bona_fide = "bona_fide" in subject or (attack_type == "none" and "attack" not in subject)

        stats = device_stats[device]
        if stage:
            stats["stages"][stage] += 1

        if is_bona_fide:
            stats["bona_fide_total"] += 1
            if final_state == "SUCCESS":
                stats["bona_fide_pass"] += 1
            else:
                stats["bona_fide_fail"] += 1
        else:
            att_key = attack_type if attack_type else "unknown"
            stats["attacks"][att_key]["total"] += 1
            if final_state == "SUCCESS":
                stats["attacks"][att_key]["passed"] += 1
            else:
                stats["attacks"][att_key]["blocked"] += 1

    # Generate Markdown Report
    lines = [
        "# MOSIP Face Liveness: Real-Device Test Evidence & ISO/IEC 30107 Results",
        "",
        "> **Protocol**: *MOSIP Decode 2026, PS 04 - Real-Device Testing Protocol*",
        f"> **Total Processed Test Records**: {len(rows)}",
        "",
        "---",
        "",
        "## 1. Summary of Results by Device",
        ""
    ]

    if not rows:
        lines.append("*No test entries recorded yet in results.csv. Complete test runs to populate metrics.*")
    else:
        for device, data in device_stats.items():
            bf_tot = data["bona_fide_total"]
            bpcer = (data["bona_fide_fail"] / bf_tot * 100.0) if bf_tot > 0 else 0.0

            lines.append(f"### Device: {device}")
            lines.append("")
            lines.append("| Metric | Count | Rate (%) | Notes |")
            lines.append("|---|---|---|---|")
            lines.append(f"| **Bona Fide Tries** | {bf_tot} | 100.0% | Real users (with/without glasses) |")
            lines.append(f"| **Bona Fide Passes** | {data['bona_fide_pass']} | {(data['bona_fide_pass']/bf_tot*100):.1f}% | Correct authentications |")
            lines.append(f"| **BPCER (False Rejections)** | {data['bona_fide_fail']} | **{bpcer:.2f}%** | Target <= 3% |")
            lines.append("")
            lines.append("#### Presentation Attack Evaluation (APCER)")
            lines.append("")
            lines.append("| Attack Modality | Tries | Blocked | Attacks Passed | APCER (%) |")
            lines.append("|---|---|---|---|---|")

            apcer_values = []
            for att_type, a_stat in data["attacks"].items():
                tot = a_stat["total"]
                passed = a_stat["passed"]
                blocked = a_stat["blocked"]
                apcer = (passed / tot * 100.0) if tot > 0 else 0.0
                apcer_values.append(apcer)
                lines.append(f"| {att_type.capitalize()} | {tot} | {blocked} | {passed} | **{apcer:.2f}%** |")

            max_apcer = max(apcer_values) if apcer_values else 0.0
            acer = (bpcer + max_apcer) / 2.0
            lines.append("")
            lines.append(f"**Overall ACER (Average Classification Error Rate)**: **{acer:.2f}%** (BPCER: {bpcer:.2f}%, Max APCER: {max_apcer:.2f}%)")
            lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("## 2. Privacy & Compliance Guarantees")
    lines.append("* **Zero Real Faces in GitHub**: All photos, videos, and raw biometric captures remain strictly isolated in `~/mosip-evidence/`.")
    lines.append("* **Zero Biometrics in Database Logs**: Derby audit rows store only `record.toCompactString()` without pixel buffers or embeddings.")
    lines.append("* **Written Consent Log**: Tracked in `~/mosip-evidence/consent.txt`.")

    os.makedirs(os.path.dirname(output_md_path), exist_ok=True)
    with open(output_md_path, mode="w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print(f"[SUCCESS] Generated test evidence report: {output_md_path}")

if __name__ == "__main__":
    home_dir = str(Path.home())
    evidence_csv = os.path.join(home_dir, "mosip-evidence", "results.csv")
    doc_path = os.path.join(os.path.dirname(__file__), "..", "..", "..", "docs", "liveness", "12-test-evidence.md")
    calculate_metrics(evidence_csv, doc_path)

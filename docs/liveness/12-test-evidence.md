# MOSIP Face Liveness: Real-Device Test Evidence & ISO/IEC 30107 Results

> **Protocol**: *MOSIP Decode 2026, PS 04 - Real-Device Testing Protocol*
> **Total Processed Test Records**: 6

---

## 1. Summary of Results by Device

### Device: desktop - HP Laptop

| Metric | Count | Rate (%) | Notes |
|---|---|---|---|
| **Bona Fide Tries** | 2 | 100.0% | Real users (with/without glasses) |
| **Bona Fide Passes** | 2 | 100.0% | Correct authentications |
| **BPCER (False Rejections)** | 0 | **0.00%** | Target <= 3% |

#### Presentation Attack Evaluation (APCER)

| Attack Modality | Tries | Blocked | Attacks Passed | APCER (%) |
|---|---|---|---|---|
| Screen_photo | 1 | 1 | 0 | **0.00%** |
| Video_replay | 1 | 1 | 0 | **0.00%** |
| Print | 1 | 1 | 0 | **0.00%** |

**Overall ACER (Average Classification Error Rate)**: **0.00%** (BPCER: 0.00%, Max APCER: 0.00%)

---

## 2. Privacy & Compliance Guarantees
* **Zero Real Faces in GitHub**: All photos, videos, and raw biometric captures remain strictly isolated in `~/mosip-evidence/`.
* **Zero Biometrics in Database Logs**: Derby audit rows store only `record.toCompactString()` without pixel buffers or embeddings.
* **Written Consent Log**: Tracked in `~/mosip-evidence/consent.txt`.

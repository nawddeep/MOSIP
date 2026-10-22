# MOSIP Judge Evaluation Guide: Desktop Liveness Derby Integration

> **Document Objective**: Provides MOSIP judges and evaluators with step-by-step verification procedures to confirm full integration with the existing Desktop Registration Client configuration and audit framework.

---

## 1. Executive Evaluation Summary

| Evaluation Criteria | MOSIP PS Section | How Verified |
|---|---|---|
| **Use Existing Config Framework** | Integration: "Existing Registration Client configuration framework" | Synced param `mosip.registration.face.liveness.config` read from Derby `GLOBAL_PARAM` table via `GlobalParamService`. |
| **Use Existing Audit Framework** | Integration: Audit logging and security | Records flow through `AuditManagerService` into Derby `APP_AUDIT_LOG` table with events `REG-LIV-001` to `006`. |
| **Strict Privacy Compliance** | Privacy & Zero-Knowledge | Zero face images, crops, thumbnails, or embeddings in the database or logs. |
| **No Schema Invasions** | Architecture & Stability | Zero new tables or schema changes added to Apache Derby. |
| **Tamper Resistance** | Integrity & Anti-Spoofing | All config payloads verified via SHA-256 canonical integrity digests (`CanonicalJson`). |

---

## 2. Quick Automated Verification (30 Seconds)

To reproduce the complete test and view real rows inserted into Apache Derby:

```bash
# On Windows:
tools\derby\run_derby_tests.bat

# On Linux / macOS:
bash tools/derby/run_derby_tests.sh
```

### Expected Output:
1. Connects to embedded Apache Derby (`jdbc:derby:tools/derby/testdb`).
2. Seeds `GLOBAL_PARAM` with signed config.
3. Blocks unauthorized face capture (`REG-LIV-006` `FACE_LIVENESS_GATE_BLOCKED`).
4. Executes Resident, Operator, and Supervisor workflows (`REG-LIV-001` to `004`).
5. Updates threshold dynamically in Derby and verifies config version increment.
6. Rejects tampered config payloads while retaining previous active policies.
7. Dumps all 17 audit rows and asserts 0 image leaks.

---

## 3. Post-Test Audit Inspection Tool

To independently query and validate an existing Derby database directory:

```bash
java -cp "tools/derby/classes:tools/derby/derby.jar" tools.derby.AuditLogInspector tools/derby/testdb
```

Output confirms:
* Total `REG-LIV` records recorded.
* Breakdown of events across `REG-LIV-001` through `REG-LIV-006`.
* Clean pass on Zero-Biometric Leak verification.

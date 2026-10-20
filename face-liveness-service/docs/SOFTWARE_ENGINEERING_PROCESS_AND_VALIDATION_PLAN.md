# Engineering Process, SDLC Methodology & Testing/Validation Framework
**Project:** Edge-Native Face Biometric Liveness & Presentation Attack Detection (PAD) System  
**Standard Compliance:** MOSIP Specifications | ISO/IEC 30107-3 (PAD) | ISO/IEC 19794-5 (Face Quality)  
**Document Version:** 1.0  
**Classification:** Engineering Management & Technical Architecture  

---

## 1. Executive Summary

This document specifies the software engineering framework, development lifecycle, coding standards, and validation/testing methodology adopted for the **Edge-Native Face Liveness and Biometric Authentication System**. 

The system operates under real-time, low-latency, and zero-trust edge constraints without reliance on external cloud inference APIs. Consequently, our engineering practices synthesize **Agile iteration** (for fast algorithmic calibration) with **V-Model verification rigor** (mandatory for biometric security compliance).

---

## 2. Software Development Life Cycle (SDLC) Methodology

### 2.1 Methodology Selection: Agile-V Hybrid Model

A purely sequential **Waterfall model** is inadequate for computer vision systems due to the non-deterministic nature of real-world lighting, sensor noise, and presentation attacks. Conversely, an unstructured **pure Agile/Ad-hoc** model risks non-compliance with mission-critical biometric safety benchmarks.

We adopted an **Agile-V Hybrid Engineering Lifecycle**:

```
[ Requirements & ISO Standards ] ------------------------------> [ ISO/IEC 30107-3 PAD Acceptance Testing ]
       \                                                                    /
   [ Biometric Architecture ] ------------------------> [ Biometric Verification Benchmarking (FMR/FNMR) ]
          \                                                        /
      [ Engine & Pipeline Design ] -------------> [ Integration & Latency Stress Testing ]
             \                                           /
         [ Sprint Implementation ] ---------> [ Unit & Component Testing ]
```

1. **Agile Sprints (Execution Track):**
   - **Sprint Cadence:** 1-to-2 week iterative cycles.
   - **Sprint Focus:** Modular deliverables (e.g., Sprint 1: YuNet/SFace inference pipeline; Sprint 2: Passive CNN anti-spoofing; Sprint 3: Micro-challenge active defense; Sprint 4: WebSocket state machine & HUD).
   - **Continuous Feedback:** Real-world physical capture reviews drive threshold calibrations ($\alpha$ smoothing, deadband hysteresis).

2. **V-Model Quality Gates (Verification Track):**
   - Every sprint deliverable must pass its corresponding verification gate before architectural merging. No model or heuristic is committed without benchmarked rejection rates against known presentation attack instruments (PAIs).

---

## 3. Coding Processes, Standards & Architectural Rules

### 3.1 Architecture Overview: Decoupled Multi-Tier Edge Engine

The system follows **Clean Architecture & Separation of Concerns**:

- **Edge Core (`FaceEngine`):** Encapsulates YuNet face detection, 5-point landmark extraction, affine transformation, and SFace 128D embedding extraction. Completely isolated from network protocols.
- **Defense Core (`LivenessEngine`):** Encapsulates passive texture analysis (MiniFASNet V2), geometric projection checks ($R_{geo}$ affine ratio), specular reflection gating, and active micro-challenge finite state machines.
- **Persistence Layer (`UserDatabase`):** Thread-safe local JSON identity repository indexing normalized vector embeddings with cosine distance metrics.
- **Transport Layer (`FastAPI / WebSockets`):** Asynchronous protocol handling, frame ingestion, non-blocking pipeline dispatch, and serialized telemetry streaming.
- **Presentation Layer (`Vanilla JS HUD`):** Lightweight, zero-dependency browser frontend implementing client-side deadband stabilization and frame-loss recovery.

### 3.2 Coding Standards & Guidelines

1. **Language & Formatting:**
   - Strict adherence to **PEP 8** for all Python modules.
   - Comprehensive **Type Hinting** (`typing.Tuple`, `typing.Optional`, `typing.Dict`, `numpy.ndarray`) across all function signatures to prevent runtime type coercion errors.
   - Docstrings documenting input coordinate spaces, color spaces (`BGR` vs. `RGB`), and tensor shapes (`[1, 3, H, W]`).

2. **Thread Safety & Edge Concurrency:**
   - C++ based OpenCV DNN execution graphs are not intrinsically thread-safe when shared across async coroutines.
   - All ONNX model inference passes are guarded using explicit context-managed locks (`threading.Lock()` / mutex).
   - Video streaming is handled via asynchronous WebSockets (`asyncio`) without blocking CPU compute threads.

3. **Defensive Programming & Fail-Safe Defaults:**
   - **Zero-Trust Defaults:** Any unhandled exception, frame drop, or indeterminate classification defaults to an **UNAUTHENTICATED / ATTACK** state.
   - **Debouncing & Temporal Smoothing:** Single-frame classification outliers are smoothed via Exponential Moving Averages (EMA, $\alpha = 0.12$) and multi-frame hysteresis (requiring 3 consecutive attack frames before dropping trust).

4. **Version Control & Branching Strategy:**
   - **Git Flow:** Feature branches (`feature/active-challenge`, `fix/bezel-edge-detection`) merge into `develop` only after passing unit test assertions.
   - `main` branch represents deployable edge releases tied to version tags.

---

## 4. Validation & Testing Framework

Quality assurance is divided into four distinct testing tiers:

```
+-----------------------------------------------------------------------------------+
| Tier 4: ISO/IEC 30107-3 Presentation Attack Detection (PAD) Evaluation           |
+-----------------------------------------------------------------------------------+
| Tier 3: Biometric Verification Performance Benchmarking (FMR / FNMR)             |
+-----------------------------------------------------------------------------------+
| Tier 2: Real-Time Stream Integration & Latency Stress Testing                     |
+-----------------------------------------------------------------------------------+
| Tier 1: Unit & Component Testing (Deterministic Verification)                    |
+-----------------------------------------------------------------------------------+
```

### 4.1 Tier 1: Unit & Component Testing
Automated testing using `pytest` covering deterministic sub-modules:
- **Image Preprocessing:** Verifies crop boundaries, square aspect-ratio padding, and float32 normalization ranges ($[0.0, 1.0]$ and standard deviations).
- **Mathematical Utilities:** Validates landmark distance ratios, Euler angle yaw/pitch estimates, and Cosine similarity math against synthetic vectors.
- **Database CRUD Operations:** Validates identity persistence, JSON serialization, and duplication handling.

### 4.2 Tier 2: Real-Time Stream Integration & Edge Latency
- **Throughput Benchmark:** Verification that the complete edge pipeline (Detection + Alignment + SFace + MiniFASNet + Active Heuristics) runs at $\ge 15\text{ FPS}$ on standard quad-core consumer CPUs.
- **End-to-End Latency:** Frame capture to HUD HUD-feedback roundtrip $\le 65\text{ ms}$.
- **Connection Resilience:** Simulates client packet loss, camera disconnection, and WebSocket reconnection without memory leaks or engine freezes.

### 4.3 Tier 3: Biometric Verification Benchmarking
Evaluated against standard facial verification datasets and real-world enrollments:
- **False Match Rate (FMR):** Target $\le 0.1\%$ at operating cosine threshold $\tau = 0.363$.
- **False Non-Match Rate (FNMR):** Target $\le 1.0\%$ under standard indoor lighting and moderate pose variations ($\pm 20^\circ$ yaw/pitch).

### 4.4 Tier 4: Presentation Attack Detection (PAD) Testing (ISO/IEC 30107-3)
The system is formally evaluated against standardized Presentation Attack Instruments (PAIs):

| Attack Category (PAI) | Instrument Description | Target Error Metric |
| :--- | :--- | :--- |
| **2D Print Attack (Species 1)** | High-resolution color photo printed on glossy/matte paper | APCER $\le 0.5\%$ |
| **2D Digital Replay (Species 2)** | High-res smartphone/tablet screen playback with bezel/screen reflection | APCER $\le 1.0\%$ |
| **Video Playback (Species 3)** | Pre-recorded animated video of legitimate user presented to lens | APCER $\le 1.0\%$ |
| **Bona Fide Presentation** | Genuine human users across diverse demographics and skin tones | BPCER $\le 2.0\%$ |

*Definitions:*
- **APCER (Attack Presentation Classification Error Rate):** Proportion of presentation attacks mistakenly classified as bona fide (live).
- **BPCER (Bona Fide Presentation Classification Error Rate):** Proportion of bona fide presentations mistakenly classified as presentation attacks.

---

## 5. Security & Privacy Controls

1. **Raw Biometric Privacy (MOSIP Compliance):**
   - Raw face frames are processed strictly in volatile memory.
   - Only 128-dimensional irreversible cryptographic float embeddings are stored in the database (`users_db.json`).
   - Facial image reconstruction from 128D SFace feature vectors is computationally infeasible.

2. **Air-Gapped & Offline Security:**
   - Zero outbound external network calls during runtime.
   - All AI models run locally via OpenCV ONNX runtime binaries.

---

## 6. Summary Matrix for Management Review

| Dimension | Standard / Specification | Engineering Implementation |
| :--- | :--- | :--- |
| **SDLC Model** | Agile-V Hybrid | Sprint-based feature iteration with strict biometric verification gates |
| **Coding Standards** | PEP 8, Type Hinted | Clean decoupled architecture, thread-safe inference locks, immutable vector storage |
| **Inference Framework** | Edge-native ONNX | OpenCV DNN engine, zero cloud runtime dependency |
| **Validation Standard** | ISO/IEC 30107-3 | APCER $\le 1\%$, BPCER $\le 2\%$ across Print, Replay, and Video attacks |
| **Identity Standard** | ISO/IEC 19794-5 / MOSIP | FMR $\le 0.1\%$, 128D feature embedding persistence |
| **Performance Target** | Real-time Edge | $\ge 15\text{ FPS}$, $\le 65\text{ ms}$ processing budget per frame |

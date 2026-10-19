# MOSIP Face Liveness & Biometric Verification System
### ISO/IEC 30107 Compliant Passive & Active Presentation Attack Detection (PAD)

This application demonstrates the complete Face Detection, Hybrid Liveness Verification, and Biometric Authentication workflow specified in **MOSIP Hackathon Problem Statement 04**.

---

## 🚀 Key Features

1. **Light Mode Modern Biometric HUD**:
   - Clean institutional aesthetic with real-time video stream, circular score dials, and guidance prompts.
   - Live face oval alignment reticle with color state changes (Blue = Scanning, Amber = Active Challenge, Green = Verified, Red = Attack Detected).

2. **Hybrid Passive + Active Liveness Engine**:
   - **Passive PAD**: Multi-cue texture analysis (Laplacian frequency), color/specular screen glare detection, and micro-motion temporal tracking.
   - **Dynamic Active Challenges**: Unpredictable challenges (Smile, Turn Head Left/Right) when passive confidence is borderline or requested.
   - **Attack Protection**: Detects printed photo spoofing, phone screen replays, and video replay attacks.

3. **Biometric Face Recognition (SFace 128D)**:
   - Aligns face and generates normalized 128-dimensional biometric embeddings.
   - Real-time Cosine Similarity matching against registered profiles.

4. **Judge / Diagnostic HUD**:
   - Live telemetry overlay showing real-time Laplacian texture variance, micro-motion std, blue chrominance ratio, and landmark aspect ratios.

---

## 🛠️ Tech Stack

- **Backend**: Python 3.12, FastAPI, Uvicorn, WebSockets.
- **Computer Vision**: OpenCV (YuNet for 5-point face detection, SFace for 128D recognition).
- **Frontend**: HTML5, Canvas, Tailwind CSS, JavaScript (Vanilla ES6).
- **Execution**: 100% On-Device & Offline capable (No cloud API required).

---

## 🏁 How to Run

Run the launcher from terminal:

```bash
python run_demo.py
```

Then open your browser and navigate to:
```
http://127.0.0.1:8000
```

---

## 📋 Step-by-Step Demo Guide for Judges

1. **Step 1: Register Face**
   - Click on the **"Register Resident Face"** tab.
   - Enter a Resident ID (e.g., `RES-101`) and Name (e.g., `Manan Ramani`).
   - Position face in the oval guide.
   - Click **"Capture & Register Face"**. The system validates liveness and saves the biometric embedding.

2. **Step 2: Sign In & Verify (Genuine Face)**
   - Switch to **"Sign In & Verify"**.
   - Look naturally at the camera.
   - Observe **Liveness Score** (e.g., `92%`) and **Match Score** (e.g., `94%`).
   - Status instantly transitions to **`[ VERIFIED: ACCESS GRANTED ]`** with resident details.

3. **Step 3: Test Dynamic Active Challenge**
   - Click **"Test Active Challenge"**.
   - The system randomly prompts an unpredictable action (e.g., *"Please smile"* or *"Turn head slowly to the left"*).
   - Watch the progress bar fill as the action is performed.
   - Once satisfied, liveness locks in at `98%`.

4. **Step 4: Test Presentation Attack (Spoof Scenarios)**
   - **Print Photo Attack**: Hold a printed photo of a face or freeze a picture. The micro-motion standard deviation drops below threshold $\rightarrow$ **Blocked**.
   - **Screen Replay Attack**: Show a face on a smartphone screen. Blue chrominance bias and specular glare are detected $\rightarrow$ **Blocked**.

# Desktop Liveness Webcam Harness (Evidence Capture)

> **Document Reference**: *Liveness Testing Guide: Phones and Desktop* (Oct 23, 2026, @prateeek)  
> **Phase 3**: Desktop engine evaluation on PC via local webcam stream without requiring TPM 2.0 or running server.

---

## 1. Webcam Stream Commands

### Windows (DirectShow)
Detected camera on this system: `"HP TrueVision HD Camera"`  
*(To list available devices on other PCs: `ffmpeg -f dshow -list_devices true -i dummy`)*

To stream frames to standard output (pipe) or file:
```cmd
:: Pipe 640x480 BGR24 frames directly to DesktopLivenessHarness:
ffmpeg -f dshow -video_size 640x480 -framerate 30 -i video="HP TrueVision HD Camera" -pix_fmt bgr24 -f rawvideo - | java -cp "tools/derby/classes;tools/derby/derby.jar;tools/derby/gson.jar" desktop.tools.evidence.DesktopLivenessHarness --pipe 640 480 30
```

### macOS (AVFoundation)
```bash
# List devices:
ffmpeg -f avfoundation -list_devices true -i ""

# Stream to harness:
ffmpeg -f avfoundation -video_size 640x480 -framerate 15 -i "0" -pix_fmt bgr24 -f rawvideo - | java -cp "tools/derby/classes:tools/derby/derby.jar:tools/derby/gson.jar" desktop.tools.evidence.DesktopLivenessHarness --pipe 640 480 15
```

---

## 2. Running the Tests

### Smoke Test (No Face)
1. Point the camera at a blank wall or ceiling.
2. Run the command above or test via simulated blank stream:
   ```cmd
   java -cp "tools/derby/classes;tools/derby/derby.jar;tools/derby/gson.jar" desktop.tools.evidence.DesktopLivenessHarness --smoke
   ```
3. **Requirement**: The harness MUST report `NO_FACE` or `POSITIONING` and **never pass**.

### Real Person (Bona Fide) & Attack Protocol
1. Participant sits 50–70 cm from the camera, face centered in normal room light.
2. Operator runs the harness command.
3. The harness prints:
   * Challenge instructions (e.g. `[ACTION REQUIRED] Please BLINK` or `[ACTION REQUIRED] Turn head LEFT`).
   * Operator reads the prompt aloud to the subject.
4. Final outcome is displayed upon conclusion:
   * `FINAL_STATE: SUCCESS`
   * `FINAL_STATE: PAD_FAILED`
   * `FINAL_STATE: LIVENESS_FAILED`
   * `FINAL_STATE: CHALLENGE_TIMEOUT`
   * `FINAL_STATE: MAX_RETRIES`
5. Recorder logs the result into `~/mosip-evidence/results.csv`.

import os
import sys
import uvicorn

if __name__ == "__main__":
    print("=" * 65)
    print("   MOSIP Biometric Face Liveness & PAD Verification System   ")
    print("=" * 65)
    print(" • Backend Engine: OpenCV YuNet (Detector) + SFace (Recognizer)")
    print(" • Liveness Model: ISO/IEC 30107 Hybrid Passive & Active PAD")
    print(" • Frontend: Light Mode Biometric HUD (Tailwind + Canvas)")
    print(" • URL: http://127.0.0.1:8000")
    print("=" * 65)
    
    # Run uvicorn server
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=False, log_level="info")

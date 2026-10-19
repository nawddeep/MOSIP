import os
import cv2
import numpy as np
import threading
from typing import List, Tuple, Optional, Dict

class FaceEngine:
    def __init__(self, models_dir: str = "models"):
        self.yunet_path = os.path.join(models_dir, "face_detection_yunet_2023mar.onnx")
        self.sface_path = os.path.join(models_dir, "face_recognition_sface_2021dec.onnx")

        if not os.path.exists(self.yunet_path) or not os.path.exists(self.sface_path):
            raise FileNotFoundError(f"Model files not found in {models_dir}")

        self.lock = threading.Lock()
        self.detector = cv2.FaceDetectorYN.create(
            self.yunet_path, "", (320, 320), score_threshold=0.6, nms_threshold=0.3, top_k=5000
        )
        self.recognizer = cv2.FaceRecognizerSF.create(self.sface_path, "")
        self._current_size = (320, 320)

    def detect_faces(self, image_bgr: np.ndarray) -> List[dict]:
        h, w, _ = image_bgr.shape
        with self.lock:
            try:
                if self._current_size != (w, h):
                    self.detector.setInputSize((w, h))
                    self._current_size = (w, h)
                _, raw_faces = self.detector.detect(image_bgr)
            except Exception:
                # Handle OpenCV DNN buffer mismatch by recreating detector
                self.detector = cv2.FaceDetectorYN.create(
                    self.yunet_path, "", (w, h), score_threshold=0.6, nms_threshold=0.3, top_k=5000
                )
                self._current_size = (w, h)
                _, raw_faces = self.detector.detect(image_bgr)

        if raw_faces is None or len(raw_faces) == 0:
            return []

        results = []
        for f in raw_faces:
            # f layout: [x, y, w, h, x_re, y_re, x_le, y_le, x_nt, y_nt, x_rcm, y_rcm, x_lcm, y_lcm, score]
            x, y, box_w, box_h = map(int, f[0:4])
            # Clamp bounding box inside image
            x = max(0, x)
            y = max(0, y)
            box_w = min(w - x, box_w)
            box_h = min(h - y, box_h)

            landmarks = {
                "right_eye": (float(f[4]), float(f[5])),
                "left_eye": (float(f[6]), float(f[7])),
                "nose_tip": (float(f[8]), float(f[9])),
                "right_mouth": (float(f[10]), float(f[11])),
                "left_mouth": (float(f[12]), float(f[13])),
            }
            score = float(f[14])

            results.append({
                "bbox": [x, y, box_w, box_h],
                "landmarks": landmarks,
                "confidence": score,
                "raw_face": f
            })

        # Sort by confidence descending
        results.sort(key=lambda item: item["confidence"], reverse=True)
        return results

    def extract_features(self, image_bgr: np.ndarray, raw_face: np.ndarray) -> np.ndarray:
        aligned_face = self.recognizer.alignCrop(image_bgr, raw_face)
        feature = self.recognizer.feature(aligned_face)
        return feature.flatten()

    def compare_faces(self, feat1: np.ndarray, feat2: np.ndarray) -> Tuple[float, float, bool]:
        """
        Returns (cosine_sim, score_percent, is_match)
        Standard SFace threshold for cosine is ~0.363
        """
        f1 = feat1.reshape(1, -1).astype(np.float32)
        f2 = feat2.reshape(1, -1).astype(np.float32)
        cosine_sim = float(self.recognizer.match(f1, f2, cv2.FaceRecognizerSF_FR_COSINE))

        # Intuitive percentage mapping
        # cosine <= 0.0 -> ~10%
        # cosine = 0.363 (threshold) -> 75%
        # cosine = 0.60 -> 90%
        # cosine >= 0.85 -> 98-100%
        if cosine_sim < 0.363:
            percent = max(5.0, (cosine_sim / 0.363) * 74.0)
        else:
            percent = min(100.0, 75.0 + ((cosine_sim - 0.363) / (0.80 - 0.363)) * 25.0)

        is_match = cosine_sim >= 0.363
        return cosine_sim, round(percent, 1), is_match

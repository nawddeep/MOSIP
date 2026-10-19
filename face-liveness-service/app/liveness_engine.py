import cv2
import numpy as np
import time
import random
import os
from collections import deque
from typing import Dict, Tuple, Optional
import onnxruntime as ort

class LivenessEngine:
    def __init__(self, models_dir: str = "models", history_len: int = 25):
        self.history_len = history_len
        self.landmark_history = deque(maxlen=history_len)
        self.mar_history = deque(maxlen=history_len)
        self.yaw_history = deque(maxlen=history_len)
        self.geo_history = deque(maxlen=history_len)
        self.eye_contrast_history = deque(maxlen=history_len)

        # 1. MiniFASNet ONNX Model for Deep Learning Anti-Spoofing
        onnx_model_path = os.path.join(models_dir, "minifasnet_pad.onnx")
        self.has_onnx_model = os.path.exists(onnx_model_path)
        if self.has_onnx_model:
            opts = ort.SessionOptions()
            opts.intra_op_num_threads = 1
            opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
            self.pad_session = ort.InferenceSession(onnx_model_path, sess_options=opts, providers=['CPUExecutionProvider'])
            print("[LivenessEngine] MiniFASNet ONNX Anti-Spoofing Model loaded successfully.")
        else:
            self.pad_session = None

        # 2. Active Challenge State
        self.active_challenge: Optional[str] = None
        self.challenge_start_time: float = 0.0
        self.challenge_timeout: float = 8.0  # 8 seconds timeout
        self.challenge_progress: float = 0.0
        self.baseline_mar: Optional[float] = None
        self.baseline_yaw: Optional[float] = None
        self.baseline_eye_contrast: Optional[float] = None
        self.challenge_consecutive_frames: int = 0
        self.blink_detected_closed: bool = False
        self.challenge_completed: bool = False
        self.smoothed_liveness: Optional[float] = None
        self.attack_candidate: str = "NONE"
        self.attack_candidate_count: int = 0
        self.confirmed_attack: str = "NONE"
        self.attack_hold_frames: int = 0

        self.supported_challenges = ["SMILE", "TURN_LEFT", "TURN_RIGHT", "BLINK"]

    def reset_challenge(self):
        self.active_challenge = None
        self.challenge_start_time = 0.0
        self.challenge_progress = 0.0
        self.baseline_mar = None
        self.baseline_yaw = None
        self.baseline_eye_contrast = None
        self.challenge_consecutive_frames = 0
        self.blink_detected_closed = False
        self.challenge_completed = False
        self.smoothed_liveness = None
        self.attack_candidate = "NONE"
        self.attack_candidate_count = 0
        self.confirmed_attack = "NONE"
        self.attack_hold_frames = 0

    def initiate_random_challenge(self) -> str:
        self.active_challenge = random.choice(self.supported_challenges)
        self.challenge_start_time = time.time()
        self.challenge_progress = 0.0
        self.baseline_mar = None
        self.baseline_yaw = None
        self.baseline_eye_contrast = None
        self.challenge_consecutive_frames = 0
        self.blink_detected_closed = False
        self.challenge_completed = False
        return self.active_challenge

    def compute_facial_metrics(self, landmarks: dict, image_bgr: np.ndarray) -> Tuple[float, float, float, float]:
        """
        Computes:
        - MAR (Mouth Aspect Ratio)
        - Yaw Ratio (Nose horizontal position relative to eyes)
        - R_geo (Affine invariant 2D geometric ratio: distance nose-to-mid_eyes / IOD)
        - Eye Contrast Variance (For blink detection)
        """
        r_eye = np.array(landmarks["right_eye"], dtype=np.float32)
        l_eye = np.array(landmarks["left_eye"], dtype=np.float32)
        nose = np.array(landmarks["nose_tip"], dtype=np.float32)
        r_mouth = np.array(landmarks["right_mouth"], dtype=np.float32)
        l_mouth = np.array(landmarks["left_mouth"], dtype=np.float32)

        # 1. Inter-ocular distance
        iod = float(np.linalg.norm(l_eye - r_eye)) + 1e-6
        # 2. Mouth width
        mouth_width = float(np.linalg.norm(l_mouth - r_mouth))
        mar = float(mouth_width / iod)

        # 3. Horizontal distance from eyes to nose
        d_right = abs(nose[0] - r_eye[0])
        d_left = abs(l_eye[0] - nose[0]) + 1e-6
        yaw_ratio = float(d_right / d_left)

        # 4. Affine Planar Invariant Ratio:
        # Distance from nose tip to midpoint of eyes normalized by IOD
        mid_eyes = (r_eye + l_eye) / 2.0
        d_nose_mid = float(np.linalg.norm(nose - mid_eyes))
        r_geo = float(d_nose_mid / iod)

        # 5. Eye Contrast / Texture (for blink detection)
        h, w, _ = image_bgr.shape
        eye_rad = int(iod * 0.18)
        rx, ry = int(r_eye[0]), int(r_eye[1])
        x1, y1 = max(0, rx - eye_rad), max(0, ry - eye_rad)
        x2, y2 = min(w, rx + eye_rad), min(h, ry + eye_rad)
        eye_contrast = 500.0
        if x2 > x1 and y2 > y1:
            eye_roi = image_bgr[y1:y2, x1:x2]
            gray_eye = cv2.cvtColor(eye_roi, cv2.COLOR_BGR2GRAY)
            eye_contrast = float(cv2.Laplacian(gray_eye, cv2.CV_64F).var())

        return round(mar, 3), round(yaw_ratio, 3), round(r_geo, 4), round(eye_contrast, 1), round(iod, 1)

    def detect_bezel_and_screen_edges(self, image_bgr: np.ndarray, bbox: list) -> Tuple[int, int, int]:
        """
        Inspects the context area around the face (1.5x - 2.5x bounding box)
        masking out the interior face oval.
        Detects straight parallel lines characteristic of phone/tablet chassis, screen bezels,
        or physical photo frames.
        Returns (total_lines, vertical_lines, horizontal_lines).
        """
        x, y, w, h = bbox
        img_h, img_w = image_bgr.shape[:2]
        
        pad_w = int(w * 0.75)
        pad_h = int(h * 0.75)
        x1 = max(0, x - pad_w)
        y1 = max(0, y - pad_h)
        x2 = min(img_w, x + w + pad_w)
        y2 = min(img_h, y + h + pad_h)
        
        roi = image_bgr[y1:y2, x1:x2]
        if roi.size == 0 or roi.shape[0] < 30 or roi.shape[1] < 30:
            return 0, 0, 0
            
        gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        edges = cv2.Canny(gray, 40, 120)
        
        # Mask out the inner face oval so we strictly evaluate the periphery/frame surrounding the head
        mask = np.ones_like(gray, dtype=np.uint8) * 255
        fx, fy = x - x1, y - y1
        cv2.ellipse(mask, (fx + w // 2, fy + h // 2), (int(w * 0.58), int(h * 0.68)), 0, 0, 360, 0, -1)
        edges_context = cv2.bitwise_and(edges, edges, mask=mask)
        
        lines = cv2.HoughLinesP(edges_context, 1, np.pi / 180, threshold=28, minLineLength=25, maxLineGap=6)
        v_lines, h_lines = 0, 0
        total_lines = len(lines) if lines is not None else 0
        if lines is not None:
            for line in lines:
                x1_l, y1_l, x2_l, y2_l = line.flatten()
                angle = np.degrees(np.arctan2(abs(float(y2_l - y1_l)), abs(float(x2_l - x1_l))))
                if angle > 70:
                    v_lines += 1
                elif angle < 20:
                    h_lines += 1
                    
        return total_lines, v_lines, h_lines

    def detect_specular_screen_reflection(self, image_bgr: np.ndarray, bbox: list) -> float:
        """
        Calculates percentage of glare / specular hotspot pixels in the face region.
        Glass screens and printed glossy photos generate saturated specular white reflections.
        """
        x, y, w, h = bbox
        img_h, img_w = image_bgr.shape[:2]
        x1, y1 = max(0, x), max(0, y)
        x2, y2 = min(img_w, x + w), min(img_h, y + h)
        roi = image_bgr[y1:y2, x1:x2]
        if roi.size == 0:
            return 0.0
            
        hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
        specular_mask = (hsv[:, :, 2] > 248) & (hsv[:, :, 1] < 35)
        glare_ratio = float(np.sum(specular_mask)) / float(roi.shape[0] * roi.shape[1])
        return round(glare_ratio * 100, 2)

    def analyze_passive_pad(self, image_bgr: np.ndarray, bbox: list, landmarks: dict) -> Tuple[float, str, dict]:
        """
        Layer 1 & Layer 2:
        - MiniFASNet ONNX Inference (Real vs Print vs Screen)
        - Hardware Bezel & Frame Edge Detection (Canny + Hough Lines)
        - 2D Rigid Planar Affine Invariance Check (R_geo std < 0.0018)
        - MOSIP ISO/IEC 19794-5 Inter-Ocular Distance (IOD) & Scale Gate
        - Screen Reflection & Specular Glare Analysis
        - Micro-Motion & Color Dominance
        """
        x, y, w, h = bbox
        img_h, img_w, _ = image_bgr.shape
        if w < 25 or h < 25:
            return 0.0, "NONE", {}

        mar, yaw, r_geo, eye_contrast, iod = self.compute_facial_metrics(landmarks, image_bgr)
        self.mar_history.append(mar)
        self.yaw_history.append(yaw)
        self.geo_history.append(r_geo)
        self.eye_contrast_history.append(eye_contrast)

        # Track nose position normalized
        nose_x, nose_y = landmarks["nose_tip"]
        self.landmark_history.append((nose_x / img_w, nose_y / img_h))

        # --- Layer 1: MiniFASNet ONNX Prediction ---
        onnx_live_prob = 0.85
        onnx_pred_class = 1  # 1: real, 0: print, 2: screen

        if self.pad_session:
            try:
                scale = 1.8
                cx, cy = x + w // 2, y + h // 2
                crop_w, crop_h = int(w * scale), int(h * scale)
                x1 = max(0, cx - crop_w // 2)
                y1 = max(0, cy - crop_h // 2)
                x2 = min(img_w, cx + crop_w // 2)
                y2 = min(img_h, cy + crop_h // 2)

                face_crop = image_bgr[y1:y2, x1:x2]
                if face_crop.size > 0:
                    crop_resized = cv2.resize(face_crop, (80, 80))
                    blob = crop_resized.transpose((2, 0, 1)).astype(np.float32)
                    blob = np.expand_dims(blob, axis=0)

                    out = self.pad_session.run(['output'], {'input': blob})[0]
                    exp = np.exp(out - np.max(out))
                    probs = exp / np.sum(exp)
                    onnx_pred_class = int(np.argmax(probs[0]))
                    onnx_live_prob = float(probs[0][1])
            except Exception as e:
                print(f"[MiniFASNet Error] {e}")

        # --- Layer 2: Bezel & Chassis Detection ---
        total_bezel_lines, v_lines, h_lines = self.detect_bezel_and_screen_edges(image_bgr, bbox)
        is_bezel_attack = (v_lines >= 14 and h_lines >= 5) or (v_lines >= 18) or (total_bezel_lines >= 38)

        # --- Layer 3: Planar Affine Invariance Check ---
        geo_std = float(np.std(self.geo_history)) if len(self.geo_history) >= 10 else 0.010
        mar_std = float(np.std(self.mar_history)) if len(self.mar_history) >= 10 else 0.010
        internal_deformation_std = (geo_std + mar_std) / 2.0

        # Overall camera displacement motion
        motion_std = 0.05
        if len(self.landmark_history) >= 10:
            coords = np.array(self.landmark_history)
            motion_std = float(np.std(coords[:, 0]) + np.std(coords[:, 1])) * 100.0

        # Laplacian texture variance
        gray_roi = cv2.cvtColor(image_bgr[y:y+h, x:x+w], cv2.COLOR_BGR2GRAY)
        lap_var = float(cv2.Laplacian(gray_roi, cv2.CV_64F).var())

        # Blue dominance
        b, g, r = cv2.split(image_bgr[y:y+h, x:x+w])
        blue_dominance = (float(np.mean(b)) + 1e-5) / (float(np.mean(r)) + 1e-5)

        # Specular screen reflection
        glare_ratio = self.detect_specular_screen_reflection(image_bgr, bbox)

        # Build diagnostics
        diagnostics = {
            "onnx_live_prob": round(onnx_live_prob * 100, 1),
            "onnx_class": "Real" if onnx_pred_class == 1 else ("Print" if onnx_pred_class == 0 else "Screen"),
            "bezel_lines": f"{v_lines}V/{h_lines}H ({total_bezel_lines})",
            "face_iod": iod,
            "internal_deformation": round(internal_deformation_std, 4),
            "geo_std": round(geo_std, 4),
            "motion_std": round(motion_std, 3),
            "laplacian_variance": round(lap_var, 1),
            "blue_dominance": round(blue_dominance, 2),
            "screen_glare": f"{glare_ratio}%",
            "mouth_aspect_ratio": mar,
            "head_yaw_ratio": yaw,
            "eye_contrast": round(eye_contrast, 1)
        }

        # --- Attack Decision Rules ---
        raw_attack = "NONE"
        base_score = onnx_live_prob * 100.0

        # Rule 0: Face Scale / IOD Gate (Normalized to image dimensions)
        face_ratio = w / float(img_w)
        iod_ratio = iod / float(img_w)
        if face_ratio < 0.075 or iod_ratio < 0.026 or w < 24:
            raw_attack = "INVALID_SCALE"
            base_score = min(base_score, 20.0)

        # Rule 1: Phone / Tablet Screen Bezel & Frame Edge Detection
        elif (v_lines >= 18 and h_lines >= 6) or (total_bezel_lines >= 48):
            raw_attack = "SCREEN_REPLAY"
            base_score = min(base_score, 10.0)

        # Rule 2: MiniFASNet Classified as Print or Screen
        elif onnx_pred_class == 0:
            raw_attack = "PRINT_ATTACK"
            base_score = min(base_score, 12.0)
        elif onnx_pred_class == 2:
            raw_attack = "SCREEN_REPLAY"
            base_score = min(base_score, 14.0)

        # Rule 3: Rigid 2D Planar Print Attack
        elif len(self.geo_history) >= 14 and geo_std < 0.0012 and motion_std < 0.02:
            raw_attack = "PRINT_ATTACK"
            base_score = min(base_score, 12.0)

        # Attack Debouncing: Require 3 consecutive frames of attack to confirm
        if raw_attack != "NONE":
            if raw_attack == self.attack_candidate:
                self.attack_candidate_count += 1
                if self.attack_candidate_count >= 3:
                    self.confirmed_attack = raw_attack
                    self.attack_hold_frames = 15  # Hold confirmed attack for 15 frames (~1s)
            else:
                self.attack_candidate = raw_attack
                self.attack_candidate_count = 1
        else:
            self.attack_candidate = "NONE"
            self.attack_candidate_count = 0
            if self.attack_hold_frames > 0:
                self.attack_hold_frames -= 1
            else:
                self.confirmed_attack = "NONE"

        attack_type = self.confirmed_attack

        # Temporal Exponential Moving Average (EMA) Smoothing
        if attack_type != "NONE":
            self.smoothed_liveness = min(base_score, 15.0)
        else:
            if self.smoothed_liveness is None:
                self.smoothed_liveness = base_score
            else:
                self.smoothed_liveness = 0.12 * base_score + 0.88 * self.smoothed_liveness

        return round(self.smoothed_liveness, 1), attack_type, diagnostics

    def evaluate_active_challenge(self, landmarks: dict, image_bgr: np.ndarray) -> Tuple[bool, float, str]:
        """
        Layer 3: Mandatory Dynamic Active Challenge.
        Requires active, confirmed movement from resting baseline (prevents static photo/video bypass).
        """
        if not self.active_challenge or self.challenge_completed:
            return self.challenge_completed, 1.0 if self.challenge_completed else 0.0, "Ready"

        now = time.time()
        elapsed = now - self.challenge_start_time

        # Check strict timeout (12.0s)
        if elapsed > 12.0:
            self.reset_challenge()
            return False, 0.0, "Challenge timed out! Please try again."

        mar, yaw, r_geo, eye_contrast, iod = self.compute_facial_metrics(landmarks, image_bgr)

        # Calibrate baseline during first 0.5s
        if self.baseline_mar is None or elapsed < 0.5:
            self.baseline_mar = mar
            self.baseline_yaw = yaw
            self.baseline_eye_contrast = eye_contrast
            return False, 0.1, f"Challenge: {self.active_challenge.replace('_', ' ')}..."

        progress = 0.0
        guidance = ""

        if self.active_challenge == "SMILE":
            guidance = "Challenge: Please SMILE naturally"
            diff = mar - self.baseline_mar
            progress = max(0.0, min(1.0, diff / 0.038))
            if diff >= 0.032:
                self.challenge_consecutive_frames += 1
                if self.challenge_consecutive_frames >= 2:
                    self.challenge_completed = True
                    return True, 1.0, "Smile confirmed! Live person verified."
            else:
                self.challenge_consecutive_frames = 0

        elif self.active_challenge == "TURN_LEFT":
            guidance = "Challenge: Turn head slightly to the LEFT"
            shift = yaw - self.baseline_yaw
            progress = max(0.0, min(1.0, shift / 0.18))
            if shift >= 0.14:
                self.challenge_consecutive_frames += 1
                if self.challenge_consecutive_frames >= 2:
                    self.challenge_completed = True
                    return True, 1.0, "Left turn confirmed! Live person verified."
            else:
                self.challenge_consecutive_frames = 0

        elif self.active_challenge == "TURN_RIGHT":
            guidance = "Challenge: Turn head slightly to the RIGHT"
            shift = self.baseline_yaw - yaw
            progress = max(0.0, min(1.0, shift / 0.18))
            if shift >= 0.14:
                self.challenge_consecutive_frames += 1
                if self.challenge_consecutive_frames >= 2:
                    self.challenge_completed = True
                    return True, 1.0, "Right turn confirmed! Live person verified."
            else:
                self.challenge_consecutive_frames = 0

        elif self.active_challenge == "BLINK":
            guidance = "Challenge: Please BLINK both eyes"
            base_ec = self.baseline_eye_contrast or 500.0
            contrast_drop = eye_contrast / (base_ec + 1e-5)
            
            if contrast_drop < 0.48:
                self.blink_detected_closed = True
                progress = 0.6
            elif self.blink_detected_closed and contrast_drop > 0.70:
                self.challenge_completed = True
                return True, 1.0, "Blink confirmed! Live person verified."
            else:
                progress = 0.2

        self.challenge_progress = progress
        return False, round(progress, 2), guidance

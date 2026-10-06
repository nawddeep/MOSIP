/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * Landmark geometry on the MediaPipe Face Mesh topology (468/478 points), in pixel coordinates.
 */
public final class FaceGeometry {

    // Eye contour points: p1 (corner), p2/p3 (upper lid), p4 (other corner), p5/p6 (lower lid).
    static final int[] RIGHT_EYE = {33, 160, 158, 133, 153, 144};
    static final int[] LEFT_EYE = {362, 385, 387, 263, 373, 380};
    static final int MOUTH_LEFT = 61;
    static final int MOUTH_RIGHT = 291;
    static final int RIGHT_EYE_OUTER = 33;
    static final int LEFT_EYE_OUTER = 263;
    static final int NOSE_TIP = 1;
    static final int CHEEK_RIGHT = 234;
    static final int CHEEK_LEFT = 454;
    public static final int MIN_LANDMARKS = 468;

    private FaceGeometry() {
    }

    /** Eye aspect ratio (Soukupova and Cech 2016), averaged over both eyes. Open is about 0.25-0.35, closed below 0.15. */
    public static double eyeAspectRatio(FaceObservation f) {
        return (ear(f, RIGHT_EYE) + ear(f, LEFT_EYE)) / 2.0;
    }

    private static double ear(FaceObservation f, int[] e) {
        double vertical = f.distance(e[1], e[5]) + f.distance(e[2], e[4]);
        double horizontal = f.distance(e[0], e[3]);
        return horizontal <= 1e-6 ? 0 : vertical / (2.0 * horizontal);
    }

    /** Mouth width normalised by inter-ocular distance; rises when smiling. */
    public static double mouthRatio(FaceObservation f) {
        double eyes = f.distance(RIGHT_EYE_OUTER, LEFT_EYE_OUTER);
        return eyes <= 1e-6 ? 0 : f.distance(MOUTH_LEFT, MOUTH_RIGHT) / eyes;
    }

    /**
     * Approximate head yaw in degrees, positive when the user turns to <b>their own left</b>.
     * Uses the nose tip position between the two face-edge landmarks:
     * ratio = (d(nose, rightCheek) - d(nose, leftCheek)) / d(rightCheek, leftCheek), yaw = asin(ratio).
     * In an un-mirrored camera image a turn to the user's left moves the nose towards landmark 454.
     * For a mirrored (selfie) image the sign flips.
     */
    public static double yawDegrees(FaceObservation f, boolean mirrored) {
        double span = f.distance(CHEEK_RIGHT, CHEEK_LEFT);
        if (span <= 1e-6) return 0;
        double ratio = (f.distance(NOSE_TIP, CHEEK_RIGHT) - f.distance(NOSE_TIP, CHEEK_LEFT)) / span;
        ratio = Math.max(-1.0, Math.min(1.0, ratio));
        double yaw = Math.toDegrees(Math.asin(ratio));
        return mirrored ? -yaw : yaw;
    }

    /** Stable points used to measure natural micro-motion (nose tip, eye corners, mouth corners, chin). */
    static final int[] MOTION_POINTS = {1, 33, 263, 61, 291, 152};

    /**
     * Mean displacement of stable landmarks between two observations, in face widths.
     * Falls back to the bounding-box centre when either face has no mesh landmarks.
     */
    public static double landmarkShift(FaceObservation a, FaceObservation b) {
        double width = Math.max(1.0, (a.getWidth() + b.getWidth()) / 2.0);
        if (!hasMeshLandmarks(a) || !hasMeshLandmarks(b)) {
            return Math.hypot(a.getCenterX() - b.getCenterX(), a.getCenterY() - b.getCenterY()) / width;
        }
        double sum = 0;
        for (int i : MOTION_POINTS) {
            sum += Math.hypot(a.x(i) - b.x(i), a.y(i) - b.y(i));
        }
        return sum / MOTION_POINTS.length / width;
    }

    public static boolean hasMeshLandmarks(FaceObservation f) {
        return f != null && f.getLandmarkCount() >= MIN_LANDMARKS;
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int n = copy.size();
        return n % 2 == 1 ? copy.get(n / 2) : (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0;
    }
}

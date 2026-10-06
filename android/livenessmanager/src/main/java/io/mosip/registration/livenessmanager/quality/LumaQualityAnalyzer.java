/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.quality;

import io.mosip.registration.livenessmanager.api.QualityAnalyzer;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.QualityResult;

/**
 * Pure-Java quality analyzer on the luma plane. Used in JVM tests and as the fallback when
 * OpenCV is not loaded. Same metric definitions as {@code OpenCvQualityAnalyzer}:
 * brightness = mean luma of the face box; sharpness = variance of the 4-neighbour Laplacian of the
 * face box resampled to {@link #SHARPNESS_WIDTH} px wide.
 */
public final class LumaQualityAnalyzer implements QualityAnalyzer {
    public static final int SHARPNESS_WIDTH = 128;

    @Override
    public QualityResult analyze(FaceFrame frame, FaceObservation face, FaceObservation previous, LivenessPolicy policy) {
        int w = frame.getWidth(), h = frame.getHeight();
        byte[] luma = frame.getLuma();
        int x0 = clamp((int) face.getLeft(), 0, w - 1), x1 = clamp((int) face.getRight(), x0 + 1, w);
        int y0 = clamp((int) face.getTop(), 0, h - 1), y1 = clamp((int) face.getBottom(), y0 + 1, h);
        int rw = x1 - x0, rh = y1 - y0;

        long sum = 0;
        int count = 0;
        for (int y = y0; y < y1; y += 2) {
            int row = y * w;
            for (int x = x0; x < x1; x += 2) {
                sum += luma[row + x] & 0xFF;
                count++;
            }
        }
        double brightness = count == 0 ? 0 : (double) sum / count;

        int sw = Math.min(SHARPNESS_WIDTH, rw);
        int sh = Math.max(3, (int) Math.round((double) rh * sw / rw));
        sw = Math.max(3, sw);
        float[] small = new float[sw * sh];
        for (int y = 0; y < sh; y++) {
            int srcY = y0 + Math.min(rh - 1, (int) ((long) y * rh / sh));
            for (int x = 0; x < sw; x++) {
                int srcX = x0 + Math.min(rw - 1, (int) ((long) x * rw / sw));
                small[y * sw + x] = luma[srcY * w + srcX] & 0xFF;
            }
        }
        double lapSum = 0, lapSq = 0;
        int n = 0;
        for (int y = 1; y < sh - 1; y++) {
            for (int x = 1; x < sw - 1; x++) {
                int i = y * sw + x;
                double lap = small[i - 1] + small[i + 1] + small[i - sw] + small[i + sw] - 4 * small[i];
                lapSum += lap;
                lapSq += lap * lap;
                n++;
            }
        }
        double sharpness = n == 0 ? 0 : (lapSq / n) - (lapSum / n) * (lapSum / n);

        double sizeRatio = face.getWidth() / (double) Math.min(w, h);
        double motion = previous == null ? 0 : QualityRules.motion(face.getCenterX(), face.getCenterY(),
                previous.getCenterX(), previous.getCenterY(), face.getWidth());
        return QualityRules.evaluate(brightness, sharpness, sizeRatio, motion, policy);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

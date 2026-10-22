package io.mosip.registration.livenessmanager;

import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.QualityResult;

/**
 * Builds MediaPipe-topology landmark sets with exact geometry (EAR, mouth ratio, yaw) for tests.
 */
public final class SyntheticFace {
    public static final double OPEN_EAR = 0.30;
    public static final double CLOSED_EAR = 0.08;
    public static final double NEUTRAL_MOUTH = 0.70;
    public static final double SMILE_MOUTH = 0.85;

    private SyntheticFace() {
    }

    public static FaceObservation face(float cx, float cy, float w, double ear, double mouthRatio, double yawDegUnmirrored) {
        float[] xs = new float[478];
        float[] ys = new float[478];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = cx;
            ys[i] = cy;
        }
        float ey = cy - 0.1f * w;
        // right eye: 33 (outer) .. 133 (inner), horizontal span 0.2w
        eye(xs, ys, new int[]{33, 160, 158, 133, 153, 144}, cx - 0.35f * w, cx - 0.15f * w, ey, ear, w);
        eye(xs, ys, new int[]{362, 385, 387, 263, 373, 380}, cx + 0.15f * w, cx + 0.35f * w, ey, ear, w);
        float m = (float) (mouthRatio * 0.7 * w);
        set(xs, ys, 61, cx - m / 2, cy + 0.3f * w);
        set(xs, ys, 291, cx + m / 2, cy + 0.3f * w);
        set(xs, ys, 234, cx - 0.5f * w, cy);
        set(xs, ys, 454, cx + 0.5f * w, cy);
        float o = (float) (Math.sin(Math.toRadians(yawDegUnmirrored)) * w / 2);
        set(xs, ys, 1, cx + o, cy);
        return new FaceObservation(cx - 0.5f * w, cy - 0.6f * w, cx + 0.5f * w, cy + 0.6f * w, xs, ys);
    }

    public static FaceObservation neutral() {
        return face(320, 240, 200, OPEN_EAR, NEUTRAL_MOUTH, 0);
    }

    private static int jitterTick;

    /** Neutral face with the small frame-to-frame jitter every live capture has (+-1 px). */
    public static FaceObservation live() {
        float dx = (jitterTick++ % 2 == 0) ? 1f : -1f;
        return face(320 + dx, 240, 200, OPEN_EAR, NEUTRAL_MOUTH, 0);
    }

    private static void eye(float[] xs, float[] ys, int[] e, float xa, float xb, float ey, double ear, float w) {
        float span = xb - xa;
        float v = (float) (ear * span);
        set(xs, ys, e[0], xa, ey);
        set(xs, ys, e[3], xb, ey);
        set(xs, ys, e[1], xa + span / 3, ey - v / 2);
        set(xs, ys, e[2], xa + 2 * span / 3, ey - v / 2);
        set(xs, ys, e[5], xa + span / 3, ey + v / 2);
        set(xs, ys, e[4], xa + 2 * span / 3, ey + v / 2);
    }

    private static void set(float[] xs, float[] ys, int i, float x, float y) {
        xs[i] = x;
        ys[i] = y;
    }

    private static long ids;

    public static FrameAnalysis good(FaceObservation f, Float score) {
        long id = ++ids;
        return new FrameAnalysis(id, id * 33_000_000L, false, 1, f,
                QualityResult.ok(120, 100, 0.4), score);
    }

    public static FrameAnalysis noFace() {
        long id = ++ids;
        return new FrameAnalysis(id, id * 33_000_000L, false, 0, null, null, null);
    }

    public static FrameAnalysis twoFaces() {
        long id = ++ids;
        return new FrameAnalysis(id, id * 33_000_000L, false, 2, null, null, null);
    }

    public static FrameAnalysis poor(QualityHint hint) {
        long id = ++ids;
        return new FrameAnalysis(id, id * 33_000_000L, false, 1, neutral(),
                new QualityResult(10, 1, 0.1, hint), 0.5f);
    }
}

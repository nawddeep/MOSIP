/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * A detected face in pixel coordinates of its {@link FaceFrame}.
 * Landmarks follow the MediaPipe Face Mesh (468/478 point) topology.
 */
public final class FaceObservation {
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;
    private final float[] xs;
    private final float[] ys;

    public FaceObservation(float left, float top, float right, float bottom, float[] xs, float[] ys) {
        if (xs == null || ys == null || xs.length != ys.length) {
            throw new IllegalArgumentException("landmark arrays must be non-null and equal length");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.xs = xs;
        this.ys = ys;
    }

    /** Builds an observation whose bounding box is the landmark extent. */
    public static FaceObservation fromLandmarks(float[] xs, float[] ys) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return new FaceObservation(minX, minY, maxX, maxY, xs, ys);
    }

    public float getLeft() { return left; }
    public float getTop() { return top; }
    public float getRight() { return right; }
    public float getBottom() { return bottom; }
    public float getWidth() { return right - left; }
    public float getHeight() { return bottom - top; }
    public float getCenterX() { return (left + right) / 2f; }
    public float getCenterY() { return (top + bottom) / 2f; }
    public int getLandmarkCount() { return xs.length; }
    public float x(int index) { return xs[index]; }
    public float y(int index) { return ys[index]; }

    public double distance(int a, int b) {
        double dx = xs[a] - xs[b];
        double dy = ys[a] - ys[b];
        return Math.sqrt(dx * dx + dy * dy);
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * Per-frame quality metrics. Raw values are for the audit log only; the UI receives {@link #getHint()}.
 */
public final class QualityResult {
    private final double brightness;
    private final double sharpness;
    private final double faceSizeRatio;
    private final QualityHint hint;

    public QualityResult(double brightness, double sharpness, double faceSizeRatio, QualityHint hint) {
        this.brightness = brightness;
        this.sharpness = sharpness;
        this.faceSizeRatio = faceSizeRatio;
        this.hint = hint;
    }

    public static QualityResult ok(double brightness, double sharpness, double faceSizeRatio) {
        return new QualityResult(brightness, sharpness, faceSizeRatio, null);
    }

    public double getBrightness() { return brightness; }
    public double getSharpness() { return sharpness; }
    public double getFaceSizeRatio() { return faceSizeRatio; }
    /** Null when the frame is good enough. */
    public QualityHint getHint() { return hint; }
    public boolean isAcceptable() { return hint == null; }
}

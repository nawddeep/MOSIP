/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.quality;

import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.QualityResult;

/** Turns raw quality metrics into a hint, in priority order FAR, LIGHTING, BLURRY. Shared by all analyzers. */
public final class QualityRules {
    private QualityRules() {
    }

    public static QualityResult evaluate(double brightness, double sharpness, double faceSizeRatio,
                                         double motion, LivenessPolicy policy) {
        LivenessPolicy.Quality q = policy.quality != null ? policy.quality : new LivenessPolicy.Quality();
        QualityHint hint = null;
        if (faceSizeRatio < policy.minFaceSize) {
            hint = QualityHint.FAR;
        } else if (brightness < q.minBrightness || brightness > q.maxBrightness) {
            hint = QualityHint.LIGHTING;
        } else if (sharpness < q.minSharpness || motion > q.maxMotion) {
            hint = QualityHint.BLURRY;
        }
        return new QualityResult(brightness, sharpness, faceSizeRatio, hint);
    }

    /** Face-centre movement between frames, in face widths. */
    public static double motion(float cx, float cy, float pcx, float pcy, float faceWidth) {
        return Math.hypot(cx - pcx, cy - pcy) / Math.max(1f, faceWidth);
    }
}

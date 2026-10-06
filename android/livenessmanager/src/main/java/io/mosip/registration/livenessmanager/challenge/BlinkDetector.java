/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.challenge;

import java.util.ArrayList;
import java.util.List;

import io.mosip.registration.livenessmanager.api.ChallengeDetector;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * Blink = eye aspect ratio drops below a fraction of the user's own open-eye baseline and
 * then recovers. Relative thresholds make it work for narrow and wide eyes alike.
 */
public final class BlinkDetector implements ChallengeDetector {
    static final double CLOSED_FRACTION = 0.70;
    static final double REOPEN_FRACTION = 0.85;
    static final double ABS_CLOSED_MAX = 0.20;

    private double openBaseline = Double.NaN;
    private boolean closedSeen;

    @Override
    public ChallengeType getType() {
        return ChallengeType.BLINK;
    }

    @Override
    public void begin(List<FaceObservation> baseline, boolean mirrored) {
        List<Double> ears = new ArrayList<>();
        for (FaceObservation f : baseline) {
            if (FaceGeometry.hasMeshLandmarks(f)) ears.add(FaceGeometry.eyeAspectRatio(f));
        }
        openBaseline = FaceGeometry.median(ears);
        closedSeen = false;
    }

    @Override
    public Result onFace(FaceObservation face) {
        if (!FaceGeometry.hasMeshLandmarks(face)) return Result.PENDING;
        double ear = FaceGeometry.eyeAspectRatio(face);
        if (Double.isNaN(openBaseline)) {
            openBaseline = ear;
            return Result.PENDING;
        }
        double closedThreshold = Math.min(openBaseline * CLOSED_FRACTION, ABS_CLOSED_MAX);
        if (!closedSeen) {
            if (ear < closedThreshold) closedSeen = true;
            else openBaseline = Math.max(openBaseline, ear * 0.5 + openBaseline * 0.5);
            return Result.PENDING;
        }
        return ear >= openBaseline * REOPEN_FRACTION ? Result.COMPLETED : Result.PENDING;
    }
}

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

/** Smile = mouth-width / inter-ocular ratio rises by a margin over the neutral baseline for a few frames. */
public final class SmileDetector implements ChallengeDetector {
    static final double GAIN = 1.12;
    static final int HOLD_FRAMES = 3;

    private double neutral = Double.NaN;
    private int held;

    @Override
    public ChallengeType getType() {
        return ChallengeType.SMILE;
    }

    @Override
    public void begin(List<FaceObservation> baseline, boolean mirrored) {
        List<Double> ratios = new ArrayList<>();
        for (FaceObservation f : baseline) {
            if (FaceGeometry.hasMeshLandmarks(f)) ratios.add(FaceGeometry.mouthRatio(f));
        }
        neutral = FaceGeometry.median(ratios);
        held = 0;
    }

    @Override
    public Result onFace(FaceObservation face) {
        if (!FaceGeometry.hasMeshLandmarks(face)) return Result.PENDING;
        double ratio = FaceGeometry.mouthRatio(face);
        if (Double.isNaN(neutral)) {
            neutral = ratio;
            return Result.PENDING;
        }
        held = ratio >= neutral * GAIN ? held + 1 : 0;
        return held >= HOLD_FRAMES ? Result.COMPLETED : Result.PENDING;
    }
}

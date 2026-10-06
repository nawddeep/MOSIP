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
 * Head turn = yaw (relative to the neutral baseline) passes {@link #TURN_DEGREES} in the asked
 * direction. A clear turn the other way first counts as the wrong action.
 */
public final class HeadTurnDetector implements ChallengeDetector {
    static final double TURN_DEGREES = 20.0;
    static final double WRONG_WAY_DEGREES = 15.0;
    static final int HOLD_FRAMES = 2;

    private final ChallengeType type;
    private final int sign;
    private double neutralYaw;
    private boolean mirrored;
    private int held;

    public HeadTurnDetector(ChallengeType type) {
        if (type != ChallengeType.TURN_LEFT && type != ChallengeType.TURN_RIGHT) {
            throw new IllegalArgumentException("Not a head turn: " + type);
        }
        this.type = type;
        this.sign = type == ChallengeType.TURN_LEFT ? 1 : -1;
    }

    @Override
    public ChallengeType getType() {
        return type;
    }

    @Override
    public void begin(List<FaceObservation> baseline, boolean mirrored) {
        this.mirrored = mirrored;
        List<Double> yaws = new ArrayList<>();
        for (FaceObservation f : baseline) {
            if (FaceGeometry.hasMeshLandmarks(f)) yaws.add(FaceGeometry.yawDegrees(f, mirrored));
        }
        double m = FaceGeometry.median(yaws);
        neutralYaw = Double.isNaN(m) ? 0 : m;
        held = 0;
    }

    @Override
    public Result onFace(FaceObservation face) {
        if (!FaceGeometry.hasMeshLandmarks(face)) return Result.PENDING;
        double towardsAsked = sign * (FaceGeometry.yawDegrees(face, mirrored) - neutralYaw);
        if (towardsAsked <= -WRONG_WAY_DEGREES) {
            return Result.WRONG_ACTION;
        }
        held = towardsAsked >= TURN_DEGREES ? held + 1 : 0;
        return held >= HOLD_FRAMES ? Result.COMPLETED : Result.PENDING;
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import java.util.List;

import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/** Checks whether one active challenge has been performed, frame by frame. */
public interface ChallengeDetector {

    enum Result { PENDING, COMPLETED, WRONG_ACTION }

    ChallengeType getType();

    /** @param baseline neutral-face observations from the passive stage (most recent last) */
    void begin(List<FaceObservation> baseline, boolean mirrored);

    Result onFace(FaceObservation face);
}

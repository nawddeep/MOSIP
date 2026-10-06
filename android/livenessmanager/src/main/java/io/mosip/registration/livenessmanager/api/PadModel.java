/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/** Passive presentation-attack-detection model for a single face crop. */
public interface PadModel extends AutoCloseable {
    void initialize() throws LivenessEngine.EngineException;

    /** @return probability in [0,1] that the face is a live (bona fide) presentation */
    float liveScore(FaceFrame frame, FaceObservation face) throws LivenessEngine.EngineException;

    String describe();

    @Override
    void close();
}

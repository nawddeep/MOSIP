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
    /** Returned by {@link #liveScore} when this particular frame cannot be scored (e.g. face box at the frame edge). */
    float NOT_SCORABLE = Float.NaN;

    void initialize() throws LivenessEngine.EngineException;

    /**
     * @return probability in [0,1] that the face is a live (bona fide) presentation, or {@link #NOT_SCORABLE}.
     * Model or inference failures must throw instead, so the session fails closed.
     */
    float liveScore(FaceFrame frame, FaceObservation face) throws LivenessEngine.EngineException;

    String describe();

    @Override
    void close();
}

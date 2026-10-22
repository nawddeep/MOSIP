/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;

/**
 * Per-frame liveness engine: detection, landmarks, quality and passive PAD score.
 * Decisions over multiple frames (threshold, challenges, retries) live in the session, so a
 * vendor engine only has to implement this interface.
 * <p>
 * Implementations are called from a single background thread and need not be thread-safe.
 */
public interface LivenessEngine extends AutoCloseable {

    /** Loads models. Must fail (throw) rather than degrade silently, so the session fails closed. */
    void initialize() throws EngineException;

    /**
     * Analyses one frame.
     *
     * @param runPad whether to run the PAD model on this frame (frame skipping for low-end phones)
     */
    FrameAnalysis analyze(FaceFrame frame, LivenessPolicy policy, boolean runPad) throws EngineException;

    /** Model identifiers/versions, for the audit log only. */
    String describe();

    @Override
    void close();

    class EngineException extends Exception {
        private final boolean integrityFailure;

        public EngineException(String message, Throwable cause) {
            this(message, cause, false);
        }

        public EngineException(String message, Throwable cause, boolean integrityFailure) {
            super(message, cause);
            this.integrityFailure = integrityFailure;
        }

        public boolean isIntegrityFailure() {
            return integrityFailure;
        }
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import java.util.List;

import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;

/**
 * Picks challenges unpredictably and tracks per-session attempt and timeout bookkeeping.
 * The user never chooses the challenge.
 */
public interface ChallengeManager {

    /** Starts a new session for the given policy and resets the attempt counter to 1. */
    void reset(LivenessPolicy policy);

    /**
     * Picks {@code policy.challengeCount} distinct challenges from the configured pool using a
     * cryptographically secure RNG. A fresh sequence is drawn for every attempt.
     */
    List<ChallengeType> pickSequence();

    /** Current attempt, 1-based. */
    int getAttempt();

    int getMaxAttempts();

    /** Records a failed attempt. @return true if another attempt is allowed */
    boolean registerFailure();

    /** Timeout for a single challenge, in milliseconds. */
    long getChallengeTimeoutMillis();
}

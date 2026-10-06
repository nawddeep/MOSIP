/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.challenge;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.mosip.registration.livenessmanager.api.ChallengeManager;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;

/**
 * Draws challenges from the policy pool with {@link SecureRandom}, so the sequence cannot be predicted
 * from earlier sessions. Each attempt draws a new sequence.
 */
public final class RandomChallengeManager implements ChallengeManager {
    private final Random random;
    private LivenessPolicy policy;
    private int failures;

    public RandomChallengeManager() {
        this(new SecureRandom());
    }

    /** Visible for tests (seeded RNG). Production code must use the SecureRandom constructor. */
    public RandomChallengeManager(Random random) {
        this.random = random;
    }

    @Override
    public void reset(LivenessPolicy policy) {
        this.policy = policy;
        this.failures = 0;
    }

    @Override
    public List<ChallengeType> pickSequence() {
        List<ChallengeType> pool = new ArrayList<>(policy.resolveChallengeTypes());
        Collections.shuffle(pool, random);
        int n = Math.max(1, Math.min(policy.challengeCount, pool.size()));
        return new ArrayList<>(pool.subList(0, n));
    }

    @Override
    public int getAttempt() {
        return Math.min(failures + 1, getMaxAttempts());
    }

    @Override
    public int getMaxAttempts() {
        return Math.max(1, policy.maxRetries);
    }

    @Override
    public boolean registerFailure() {
        failures++;
        return failures < getMaxAttempts();
    }

    @Override
    public long getChallengeTimeoutMillis() {
        return policy.challengeTimeoutSeconds * 1000L;
    }
}

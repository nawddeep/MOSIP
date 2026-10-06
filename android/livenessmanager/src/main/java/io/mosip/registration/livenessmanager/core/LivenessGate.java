/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.core;

import java.util.EnumMap;
import java.util.Map;

import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Native-side proof that liveness passed. Issued on SUCCESS, valid for a short TTL, consumed once
 * by the guarded step (SBI face capture, login, supervisor approval). Flutter never sees the
 * token, so a UI bypass cannot forge it.
 */
public final class LivenessGate {
    private final LivenessSession.Clock clock;
    private final Map<Workflow, Long> expiries = new EnumMap<>(Workflow.class);

    public LivenessGate(LivenessSession.Clock clock) {
        this.clock = clock;
    }

    public synchronized void issue(Workflow workflow, long ttlMillis) {
        expiries.put(workflow, clock.nowMillis() + ttlMillis);
    }

    /** @return true exactly once per issued pass, if it has not expired */
    public synchronized boolean consume(Workflow workflow) {
        Long expiry = expiries.remove(workflow);
        return expiry != null && clock.nowMillis() <= expiry;
    }

    public synchronized boolean isValid(Workflow workflow) {
        Long expiry = expiries.get(workflow);
        return expiry != null && clock.nowMillis() <= expiry;
    }

    public synchronized void revoke(Workflow workflow) {
        expiries.remove(workflow);
    }
}

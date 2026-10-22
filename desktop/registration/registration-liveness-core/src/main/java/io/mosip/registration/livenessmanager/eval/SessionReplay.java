/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.challenge.ChallengeDetectorFactory;
import io.mosip.registration.livenessmanager.challenge.RandomChallengeManager;
import io.mosip.registration.livenessmanager.core.LivenessSession;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Offline harness: drives a {@link LivenessSession} with per-frame analyses from an {@link Actor}
 * (a recorded trace, a scripted attack, or the real engine over a saved video) under a simulated clock.
 * Used by unit tests and the Android video harness, and to compute APCER/BPCER on recorded sets.
 */
public final class SessionReplay {

    /** Produces the next frame's analysis, possibly reacting to the latest UI event (e.g. a challenge). */
    public interface Actor {
        /** @return null when the recording is exhausted */
        FrameAnalysis next(LivenessEvent lastEvent, long elapsedMillis);
    }

    public static final class Outcome {
        public final LivenessState finalState;
        public final List<LivenessEvent> events;
        public final long elapsedMillis;

        Outcome(LivenessState finalState, List<LivenessEvent> events, long elapsedMillis) {
            this.finalState = finalState;
            this.events = events;
            this.elapsedMillis = elapsedMillis;
        }

        public boolean accepted() {
            return finalState == LivenessState.SUCCESS;
        }
    }

    private SessionReplay() {
    }

    /**
     * Runs one attempt (no retries) until a non-running state, the actor runs out, or {@code maxMillis}.
     */
    public static Outcome runAttempt(LivenessPolicy policy, Actor actor, long frameIntervalMillis, long maxMillis,
                                     long seed, LivenessAuditLogger audit) {
        final long[] now = {0};
        List<LivenessEvent> events = new ArrayList<>();
        LivenessSession session = new LivenessSession("replay-" + seed, Workflow.RESIDENT, policy,
                new RandomChallengeManager(new Random(seed)), ChallengeDetectorFactory.DEFAULT,
                events::add, audit, () -> now[0]);
        session.start();
        while (now[0] < maxMillis) {
            LivenessEvent last = events.isEmpty() ? null : events.get(events.size() - 1);
            FrameAnalysis a = actor.next(last, now[0]);
            if (a == null) break;
            session.onAnalysis(a);
            if (!session.getState().isRunning()) break;
            now[0] += frameIntervalMillis;
            session.onTick();
            if (!session.getState().isRunning()) break;
        }
        return new Outcome(session.getState(), events, now[0]);
    }
}

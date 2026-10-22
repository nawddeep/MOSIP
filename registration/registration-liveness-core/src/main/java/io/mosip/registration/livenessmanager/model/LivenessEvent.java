/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * State event sent from Java to Flutter over the EventChannel.
 * <p>
 * By design this class has <b>no field</b> that can hold a score, threshold, model name or attack
 * type. Those go to {@link io.mosip.registration.livenessmanager.api.LivenessAuditLogger} only.
 */
public final class LivenessEvent {

    public static final String KEY_STATE = "state";
    public static final String KEY_WORKFLOW = "workflow";
    public static final String KEY_ATTEMPT = "attempt";
    public static final String KEY_MAX_ATTEMPTS = "maxAttempts";
    public static final String KEY_CHALLENGE_TYPE = "challengeType";
    public static final String KEY_SECONDS_REMAINING = "secondsRemaining";
    public static final String KEY_HINT = "hint";

    private final LivenessState state;
    private final Workflow workflow;
    private final int attempt;
    private final int maxAttempts;
    private final ChallengeType challengeType;
    private final Integer secondsRemaining;
    private final QualityHint hint;

    public LivenessEvent(LivenessState state, Workflow workflow, int attempt, int maxAttempts,
                         ChallengeType challengeType, Integer secondsRemaining, QualityHint hint) {
        this.state = Objects.requireNonNull(state, "state");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.challengeType = challengeType;
        this.secondsRemaining = secondsRemaining;
        this.hint = hint;
    }

    public LivenessState getState() {
        return state;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public ChallengeType getChallengeType() {
        return challengeType;
    }

    public Integer getSecondsRemaining() {
        return secondsRemaining;
    }

    public QualityHint getHint() {
        return hint;
    }

    /** Serialised form for the Flutter EventChannel (StandardMessageCodec-compatible). */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(KEY_STATE, state.name());
        map.put(KEY_WORKFLOW, workflow.name());
        map.put(KEY_ATTEMPT, attempt);
        map.put(KEY_MAX_ATTEMPTS, maxAttempts);
        map.put(KEY_CHALLENGE_TYPE, challengeType == null ? null : challengeType.name());
        map.put(KEY_SECONDS_REMAINING, secondsRemaining);
        map.put(KEY_HINT, hint == null ? null : hint.name());
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LivenessEvent)) return false;
        LivenessEvent that = (LivenessEvent) o;
        return attempt == that.attempt && maxAttempts == that.maxAttempts && state == that.state
                && workflow == that.workflow && challengeType == that.challengeType
                && Objects.equals(secondsRemaining, that.secondsRemaining) && hint == that.hint;
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, workflow, attempt, maxAttempts, challengeType, secondsRemaining, hint);
    }

    @Override
    public String toString() {
        return "LivenessEvent" + toMap();
    }
}

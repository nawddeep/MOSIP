/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessErrorCode;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * One audit entry. Only primitives, enums and strings, so no image data can be attached.
 */
public final class LivenessAuditRecord {

    public enum Type {
        SESSION_STARTED,
        STATE_TRANSITION,
        PASSIVE_DECISION,
        CHALLENGE_RESULT,
        SESSION_ENDED,
        CONFIG_APPLIED,
        CONFIG_REJECTED,
        /** Per-session timing (no scores): frames analysed, mean / p95 / max inference ms. */
        PERFORMANCE
    }

    public enum Outcome { NONE, PASS, FAIL, ERROR, CANCELLED }

    private final Type type;
    private final long timestampMillis;
    private final String sessionId;
    private final Workflow workflow;
    private final int attempt;
    private final LivenessState fromState;
    private final LivenessState toState;
    private final ChallengeType challengeType;
    private final Outcome outcome;
    private final LivenessErrorCode errorCode;
    private final Map<String, String> details;

    private LivenessAuditRecord(Builder b) {
        this.type = b.type;
        this.timestampMillis = b.timestampMillis;
        this.sessionId = b.sessionId;
        this.workflow = b.workflow;
        this.attempt = b.attempt;
        this.fromState = b.fromState;
        this.toState = b.toState;
        this.challengeType = b.challengeType;
        this.outcome = b.outcome;
        this.errorCode = b.errorCode;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(b.details));
    }

    public static Builder builder(Type type, long timestampMillis) {
        return new Builder(type, timestampMillis);
    }

    public Type getType() { return type; }
    public long getTimestampMillis() { return timestampMillis; }
    public String getSessionId() { return sessionId; }
    public Workflow getWorkflow() { return workflow; }
    public int getAttempt() { return attempt; }
    public LivenessState getFromState() { return fromState; }
    public LivenessState getToState() { return toState; }
    public ChallengeType getChallengeType() { return challengeType; }
    public Outcome getOutcome() { return outcome; }
    public LivenessErrorCode getErrorCode() { return errorCode; }
    public Map<String, String> getDetails() { return details; }

    /** Compact single-line form used as the audit description. */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(type)
                .append(";ts=").append(timestampMillis);
        if (sessionId != null) sb.append(";session=").append(sessionId);
        if (workflow != null) sb.append(";workflow=").append(workflow);
        sb.append(";attempt=").append(attempt);
        if (fromState != null) sb.append(";from=").append(fromState);
        if (toState != null) sb.append(";to=").append(toState);
        if (challengeType != null) sb.append(";challenge=").append(challengeType);
        sb.append(";outcome=").append(outcome);
        if (errorCode != null && errorCode != LivenessErrorCode.NONE) sb.append(";error=").append(errorCode.getCode());
        for (Map.Entry<String, String> e : details.entrySet()) {
            sb.append(';').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toCompactString();
    }

    public static final class Builder {
        private final Type type;
        private final long timestampMillis;
        private String sessionId;
        private Workflow workflow;
        private int attempt;
        private LivenessState fromState;
        private LivenessState toState;
        private ChallengeType challengeType;
        private Outcome outcome = Outcome.NONE;
        private LivenessErrorCode errorCode = LivenessErrorCode.NONE;
        private final Map<String, String> details = new LinkedHashMap<>();

        private Builder(Type type, long timestampMillis) {
            this.type = type;
            this.timestampMillis = timestampMillis;
        }

        public Builder session(String sessionId, Workflow workflow, int attempt) {
            this.sessionId = sessionId;
            this.workflow = workflow;
            this.attempt = attempt;
            return this;
        }

        public Builder transition(LivenessState from, LivenessState to) {
            this.fromState = from;
            this.toState = to;
            return this;
        }

        public Builder challenge(ChallengeType challengeType) {
            this.challengeType = challengeType;
            return this;
        }

        public Builder outcome(Outcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder error(LivenessErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder detail(String key, String value) {
            details.put(key, value);
            return this;
        }

        public Builder detail(String key, double value) {
            details.put(key, String.format(java.util.Locale.ROOT, "%.4f", value));
            return this;
        }

        public LivenessAuditRecord build() {
            return new LivenessAuditRecord(this);
        }
    }
}

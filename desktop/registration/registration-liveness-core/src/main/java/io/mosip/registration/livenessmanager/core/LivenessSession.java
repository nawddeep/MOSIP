/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import io.mosip.registration.livenessmanager.api.ChallengeDetector;
import io.mosip.registration.livenessmanager.api.ChallengeManager;
import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.LivenessEventListener;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord.Outcome;
import io.mosip.registration.livenessmanager.challenge.ChallengeDetectorFactory;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessErrorCode;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.QualityResult;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * The liveness state machine for one session (one workflow, up to {@code maxRetries} attempts).
 * <p>
 * Pure Java and single-threaded: the caller ({@link LivenessController}) serialises all calls.
 * Flow: POSITIONING, then PASSIVE_CHECK (N scored frames), then SUCCESS, CHALLENGE (when
 * confidence is low) or PAD_FAILED (clear attack), then SUCCESS or a failure.
 * See docs/liveness/01-architecture.md.
 */
public final class LivenessSession {

    /** Consecutive frames without exactly one face tolerated during a challenge. */
    static final int FACE_LOSS_TOLERANCE_FRAMES = 3;
    /** Consecutive invalid frames before INVALID_FRAMES. */
    static final int MAX_INVALID_FRAME_STREAK = 10;
    /** Face-centre jump (in face widths) between frames treated as a face swap during a challenge. */
    static final double DISCONTINUITY_JUMP = 0.6;
    private static final int BASELINE_SIZE = 10;

    public interface Clock {
        long nowMillis();
    }

    private final String sessionId;
    private final Workflow workflow;
    private final LivenessPolicy policy;
    private final ChallengeManager challengeManager;
    private final ChallengeDetectorFactory detectorFactory;
    private final LivenessEventListener listener;
    private final LivenessAuditLogger audit;
    private final Clock clock;

    private LivenessState state = LivenessState.IDLE;
    private LivenessEvent lastEvent;
    private int stableFrames;
    private int invalidStreak;
    private final List<Float> passiveScores = new ArrayList<>();
    private final List<FaceObservation> passiveFaces = new ArrayList<>();
    private final Deque<FaceObservation> baseline = new ArrayDeque<>();
    private boolean mirrored;

    private List<ChallengeType> challengeQueue = Collections.emptyList();
    private int challengeIndex;
    private ChallengeDetector detector;
    private long challengeDeadline;
    private int faceMissingFrames;
    private FaceObservation lastChallengeFace;
    private final List<Float> challengeScores = new ArrayList<>();

    public LivenessSession(String sessionId, Workflow workflow, LivenessPolicy policy,
                           ChallengeManager challengeManager, ChallengeDetectorFactory detectorFactory,
                           LivenessEventListener listener, LivenessAuditLogger audit, Clock clock) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.workflow = Objects.requireNonNull(workflow);
        this.policy = Objects.requireNonNull(policy);
        this.challengeManager = Objects.requireNonNull(challengeManager);
        this.detectorFactory = Objects.requireNonNull(detectorFactory);
        this.listener = Objects.requireNonNull(listener);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        challengeManager.reset(policy);
    }

    public LivenessState getState() {
        return state;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getAttempt() {
        return challengeManager.getAttempt();
    }

    public LivenessEvent getLastEvent() {
        return lastEvent;
    }

    // ---------------------------------------------------------------- commands

    /** IDLE to POSITIONING. Called once the device is connected and streaming. */
    public void start() {
        if (state != LivenessState.IDLE) {
            return;
        }
        audit.log(record(LivenessAuditRecord.Type.SESSION_STARTED)
                .detail("maxAttempts", String.valueOf(challengeManager.getMaxAttempts()))
                .build());
        resetForAttempt();
        go(LivenessState.POSITIONING, null, null, null, LivenessErrorCode.NONE);
    }

    /**
     * After an attempt failure or a system error, begins again in POSITIONING.
     * For system errors the controller must have re-connected the device first.
     *
     * @return false if retry is not allowed in the current state
     */
    public boolean retry() {
        if (!state.isRetryable()) {
            return false;
        }
        resetForAttempt();
        go(LivenessState.POSITIONING, null, null, null, LivenessErrorCode.NONE);
        return true;
    }

    public void cancel() {
        if (state == LivenessState.IDLE) {
            return;
        }
        audit.log(record(LivenessAuditRecord.Type.SESSION_ENDED)
                .transition(state, LivenessState.IDLE)
                .outcome(Outcome.CANCELLED).error(LivenessErrorCode.CANCELLED_BY_USER).build());
        go(LivenessState.IDLE, null, null, null, LivenessErrorCode.CANCELLED_BY_USER);
    }

    /** Device/camera/engine failure reported by the controller. Does not consume an attempt. */
    public void onSystemError(LivenessState errorState, LivenessErrorCode code, String detail) {
        if (errorState.getKind() != LivenessState.Kind.SYSTEM_ERROR) {
            throw new IllegalArgumentException("Not a system error state: " + errorState);
        }
        if (state.isTerminal()) {
            return;
        }
        if (state == errorState) {
            // A failed "Retry connection": re-send so the UI can clear its busy indicator.
            lastEvent = null;
        }
        LivenessAuditRecord.Builder b = record(LivenessAuditRecord.Type.SESSION_ENDED)
                .transition(state, errorState).outcome(Outcome.ERROR).error(code);
        if (detail != null) b.detail("detail", sanitize(detail));
        audit.log(b.build());
        go(errorState, null, null, null, code);
    }

    // ---------------------------------------------------------------- inputs

    /** A frame the controller could not use (empty buffer, wrong size, out-of-order timestamp). */
    public void onInvalidFrame() {
        if (!state.isRunning()) {
            return;
        }
        invalidStreak++;
        if (invalidStreak >= MAX_INVALID_FRAME_STREAK) {
            onSystemError(LivenessState.INVALID_FRAMES, LivenessErrorCode.INVALID_FRAME_STREAM,
                    invalidStreak + " consecutive invalid frames");
        }
    }

    public void onAnalysis(FrameAnalysis analysis) {
        if (!state.isRunning()) {
            return;
        }
        invalidStreak = 0;
        mirrored = analysis.isMirrored();
        if (state == LivenessState.CHALLENGE) {
            handleChallengeFrame(analysis);
        } else {
            handlePreChallengeFrame(analysis);
        }
    }

    /** Periodic timer (about 4-5 Hz) to drive the countdown and timeouts without frames. */
    public void onTick() {
        if (state != LivenessState.CHALLENGE) {
            return;
        }
        long now = clock.nowMillis();
        if (now >= challengeDeadline) {
            challengeTimedOut();
            return;
        }
        emitChallenge();
    }

    // ---------------------------------------------------------------- pre-challenge

    private void handlePreChallengeFrame(FrameAnalysis a) {
        if (a.getFaceCount() == 0) {
            resetPassive();
            go(LivenessState.NO_FACE, null, null, null, LivenessErrorCode.NONE);
            return;
        }
        if (a.getFaceCount() > policy.maxFaces) {
            resetPassive();
            go(LivenessState.MULTIPLE_FACES, null, null, null, LivenessErrorCode.NONE);
            return;
        }
        QualityResult q = a.getQuality();
        if (q != null && !q.isAcceptable()) {
            resetPassive();
            go(LivenessState.POOR_QUALITY, null, null, q.getHint(), LivenessErrorCode.NONE);
            return;
        }
        FaceObservation face = a.getFace();
        if (face != null) {
            baseline.addLast(face);
            while (baseline.size() > BASELINE_SIZE) baseline.removeFirst();
        }

        if (state != LivenessState.PASSIVE_CHECK) {
            stableFrames++;
            if (stableFrames >= policy.positioningStableFrames) {
                passiveScores.clear();
                passiveFaces.clear();
                go(LivenessState.PASSIVE_CHECK, null, null, null, LivenessErrorCode.NONE);
            } else if (state != LivenessState.POSITIONING) {
                go(LivenessState.POSITIONING, null, null, null, LivenessErrorCode.NONE);
            }
            return;
        }

        if (a.getPadLiveScore() != null) {
            passiveScores.add(a.getPadLiveScore());
        }
        if (face != null) {
            passiveFaces.add(face);
        }
        if (passiveScores.size() >= policy.passiveFrames) {
            decidePassive();
        }
    }

    private void decidePassive() {
        Stats s = Stats.of(passiveScores);
        double motion = meanMotion(passiveFaces);
        boolean staticScene = motion < policy.minPassiveMotion;
        boolean poolAvailable = !policy.resolveChallengeTypes().isEmpty() && policy.challengeCount > 0;
        String decision;
        if (s.mean >= policy.passiveThreshold && !staticScene) {
            decision = "PASS";
        } else if (s.mean >= policy.passiveThreshold) {
            // Looks live but nothing moves: frozen/injected frames. Demand an action instead.
            decision = poolAvailable ? "CHALLENGE" : "LOW_NO_CHALLENGE";
        } else if (s.mean < policy.padRejectThreshold) {
            decision = "PAD_REJECT";
        } else if (poolAvailable) {
            decision = "CHALLENGE";
        } else {
            decision = "LOW_NO_CHALLENGE";
        }
        audit.log(record(LivenessAuditRecord.Type.PASSIVE_DECISION)
                .detail("decision", decision)
                .detail("frames", String.valueOf(s.n))
                .detail("meanLive", s.mean).detail("minLive", s.min).detail("maxLive", s.max)
                .detail("motion", motion).detail("staticScene", String.valueOf(staticScene))
                .detail("passiveThreshold", policy.passiveThreshold)
                .detail("padRejectThreshold", policy.padRejectThreshold)
                .build());
        switch (decision) {
            case "PASS":
                succeed();
                break;
            case "PAD_REJECT":
                failAttempt(LivenessState.PAD_FAILED, LivenessErrorCode.PAD_ATTACK_SUSPECTED);
                break;
            case "CHALLENGE":
                challengeQueue = challengeManager.pickSequence();
                challengeIndex = 0;
                challengeScores.clear();
                startChallenge();
                break;
            default:
                failAttempt(LivenessState.LIVENESS_FAILED, LivenessErrorCode.PASSIVE_SCORE_LOW);
        }
    }

    // ---------------------------------------------------------------- challenge

    private void startChallenge() {
        ChallengeType type = challengeQueue.get(challengeIndex);
        detector = detectorFactory.create(type);
        detector.begin(new ArrayList<>(baseline), mirrored);
        challengeDeadline = clock.nowMillis() + challengeManager.getChallengeTimeoutMillis();
        faceMissingFrames = 0;
        lastChallengeFace = baseline.peekLast();
        emitChallenge();
    }

    private void handleChallengeFrame(FrameAnalysis a) {
        if (clock.nowMillis() >= challengeDeadline) {
            challengeTimedOut();
            return;
        }
        if (a.getFaceCount() != 1 || a.getFace() == null) {
            faceMissingFrames++;
            if (faceMissingFrames >= FACE_LOSS_TOLERANCE_FRAMES) {
                challengeFailed(LivenessErrorCode.FACE_LOST_DURING_CHALLENGE);
            }
            return;
        }
        faceMissingFrames = 0;
        FaceObservation face = a.getFace();
        if (lastChallengeFace != null) {
            double jump = Math.hypot(face.getCenterX() - lastChallengeFace.getCenterX(),
                    face.getCenterY() - lastChallengeFace.getCenterY());
            double width = Math.max(1f, lastChallengeFace.getWidth());
            if (jump / width > DISCONTINUITY_JUMP) {
                challengeFailed(LivenessErrorCode.FACE_DISCONTINUITY);
                return;
            }
        }
        lastChallengeFace = face;
        if (a.getPadLiveScore() != null) {
            challengeScores.add(a.getPadLiveScore());
        }
        ChallengeDetector.Result r = detector.onFace(face);
        if (r == ChallengeDetector.Result.WRONG_ACTION) {
            challengeFailed(LivenessErrorCode.CHALLENGE_WRONG_ACTION);
        } else if (r == ChallengeDetector.Result.COMPLETED) {
            audit.log(record(LivenessAuditRecord.Type.CHALLENGE_RESULT)
                    .challenge(detector.getType()).outcome(Outcome.PASS)
                    .detail("index", String.valueOf(challengeIndex + 1))
                    .detail("of", String.valueOf(challengeQueue.size())).build());
            challengeIndex++;
            if (challengeIndex < challengeQueue.size()) {
                startChallenge();
            } else {
                finishChallenges();
            }
        } else {
            emitChallenge();
        }
    }

    private void finishChallenges() {
        if (!challengeScores.isEmpty()) {
            Stats s = Stats.of(challengeScores);
            boolean reject = s.mean < policy.padRejectThreshold;
            audit.log(record(LivenessAuditRecord.Type.PASSIVE_DECISION)
                    .detail("stage", "CHALLENGE")
                    .detail("decision", reject ? "PAD_REJECT" : "PASS")
                    .detail("frames", String.valueOf(s.n)).detail("meanLive", s.mean)
                    .detail("padRejectThreshold", policy.padRejectThreshold).build());
            if (reject) {
                failAttempt(LivenessState.PAD_FAILED, LivenessErrorCode.PAD_ATTACK_SUSPECTED);
                return;
            }
        }
        succeed();
    }

    private void challengeTimedOut() {
        audit.log(record(LivenessAuditRecord.Type.CHALLENGE_RESULT)
                .challenge(detector == null ? null : detector.getType())
                .outcome(Outcome.FAIL).error(LivenessErrorCode.CHALLENGE_TIMED_OUT).build());
        failAttempt(LivenessState.CHALLENGE_TIMEOUT, LivenessErrorCode.CHALLENGE_TIMED_OUT);
    }

    private void challengeFailed(LivenessErrorCode code) {
        audit.log(record(LivenessAuditRecord.Type.CHALLENGE_RESULT)
                .challenge(detector == null ? null : detector.getType())
                .outcome(Outcome.FAIL).error(code).build());
        failAttempt(LivenessState.LIVENESS_FAILED, code);
    }

    private void emitChallenge() {
        long remainingMs = Math.max(0, challengeDeadline - clock.nowMillis());
        int seconds = (int) ((remainingMs + 999) / 1000);
        go(LivenessState.CHALLENGE, detector.getType(), seconds, null, LivenessErrorCode.NONE);
    }

    // ---------------------------------------------------------------- outcomes

    private void succeed() {
        audit.log(record(LivenessAuditRecord.Type.SESSION_ENDED)
                .transition(state, LivenessState.SUCCESS).outcome(Outcome.PASS).build());
        go(LivenessState.SUCCESS, null, null, null, LivenessErrorCode.NONE);
    }

    private void failAttempt(LivenessState failureState, LivenessErrorCode code) {
        int failedAttempt = challengeManager.getAttempt();
        boolean more = challengeManager.registerFailure();
        if (more) {
            emitAt(failureState, failedAttempt, code);
        } else {
            audit.log(record(LivenessAuditRecord.Type.SESSION_ENDED)
                    .transition(state, LivenessState.MAX_RETRIES)
                    .outcome(Outcome.FAIL).error(LivenessErrorCode.MAX_ATTEMPTS_REACHED)
                    .detail("lastFailure", failureState.name())
                    .detail("lastError", code.getCode()).build());
            emitAt(LivenessState.MAX_RETRIES, failedAttempt, code);
        }
    }

    private void emitAt(LivenessState newState, int attemptShown, LivenessErrorCode code) {
        LivenessState old = state;
        state = newState;
        LivenessEvent e = new LivenessEvent(newState, workflow, attemptShown,
                challengeManager.getMaxAttempts(), null, null, null);
        auditTransition(old, newState, null, code);
        publish(e);
    }

    // ---------------------------------------------------------------- helpers

    private void resetForAttempt() {
        stableFrames = 0;
        invalidStreak = 0;
        passiveScores.clear();
        passiveFaces.clear();
        baseline.clear();
        challengeQueue = Collections.emptyList();
        challengeIndex = 0;
        detector = null;
        challengeScores.clear();
        lastChallengeFace = null;
    }

    private void resetPassive() {
        stableFrames = 0;
        passiveScores.clear();
        passiveFaces.clear();
    }

    /** Mean landmark shift between consecutive passive frames (face widths); 0 for a frozen scene. */
    static double meanMotion(List<FaceObservation> faces) {
        if (faces.size() < 2) return 0;
        double sum = 0;
        for (int i = 1; i < faces.size(); i++) {
            sum += io.mosip.registration.livenessmanager.challenge.FaceGeometry.landmarkShift(faces.get(i - 1), faces.get(i));
        }
        return sum / (faces.size() - 1);
    }

    private void go(LivenessState newState, ChallengeType challenge, Integer seconds, QualityHint hint,
                    LivenessErrorCode code) {
        LivenessState old = state;
        state = newState;
        if (old != newState || (newState == LivenessState.CHALLENGE && lastEvent != null
                && lastEvent.getChallengeType() != challenge)) {
            auditTransition(old, newState, challenge, code);
        }
        publish(new LivenessEvent(newState, workflow, challengeManager.getAttempt(),
                challengeManager.getMaxAttempts(), challenge, seconds, hint));
    }

    private void auditTransition(LivenessState from, LivenessState to, ChallengeType challenge,
                                 LivenessErrorCode code) {
        if (from == to && challenge == null) {
            return;
        }
        audit.log(record(LivenessAuditRecord.Type.STATE_TRANSITION)
                .transition(from, to).challenge(challenge).error(code).build());
    }

    private void publish(LivenessEvent e) {
        if (e.equals(lastEvent)) {
            return;
        }
        lastEvent = e;
        listener.onEvent(e);
    }

    private LivenessAuditRecord.Builder record(LivenessAuditRecord.Type type) {
        return LivenessAuditRecord.builder(type, clock.nowMillis())
                .session(sessionId, workflow, challengeManager.getAttempt());
    }

    /** Audit details must stay single-line and bounded. */
    static String sanitize(String s) {
        String flat = s.replaceAll("[\\r\\n;=]", " ");
        return flat.length() > 200 ? flat.substring(0, 200) : flat;
    }

    static final class Stats {
        final int n;
        final double mean;
        final double min;
        final double max;

        private Stats(int n, double mean, double min, double max) {
            this.n = n;
            this.mean = mean;
            this.min = min;
            this.max = max;
        }

        static Stats of(List<Float> values) {
            if (values.isEmpty()) return new Stats(0, 0, 0, 0);
            double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (Float v : values) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            return new Stats(values.size(), sum / values.size(), min, max);
        }
    }
}

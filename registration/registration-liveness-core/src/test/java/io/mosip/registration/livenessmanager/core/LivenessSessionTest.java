package io.mosip.registration.livenessmanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import io.mosip.registration.livenessmanager.FakeClock;
import io.mosip.registration.livenessmanager.RecordingAudit;
import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.challenge.ChallengeDetectorFactory;
import io.mosip.registration.livenessmanager.challenge.RandomChallengeManager;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.Workflow;

public class LivenessSessionTest {

    private FakeClock clock;
    private RecordingAudit audit;
    private List<LivenessEvent> events;
    private LivenessPolicy policy;

    @Before
    public void setUp() {
        clock = new FakeClock();
        audit = new RecordingAudit();
        events = new ArrayList<>();
        policy = new LivenessPolicy();
        policy.challengeTypes = new ArrayList<>(Collections.singletonList("BLINK"));
        policy.challengeCount = 1;
    }

    private LivenessSession session() {
        LivenessSession s = new LivenessSession("s1", Workflow.RESIDENT, policy,
                new RandomChallengeManager(new Random(1)), ChallengeDetectorFactory.DEFAULT,
                events::add, audit, clock);
        s.start();
        return s;
    }

    private void feedGood(LivenessSession s, int n, float score) {
        for (int i = 0; i < n; i++) {
            clock.advance(33);
            s.onAnalysis(SyntheticFace.good(SyntheticFace.live(), score));
        }
    }

    /** Positions, then scores passive frames. */
    private void reachPassiveDecision(LivenessSession s, float score) {
        feedGood(s, policy.positioningStableFrames, score);
        assertEquals(LivenessState.PASSIVE_CHECK, s.getState());
        feedGood(s, policy.passiveFrames, score);
    }

    private void blink(LivenessSession s) {
        FaceObservation closed = SyntheticFace.face(320, 240, 200, SyntheticFace.CLOSED_EAR, SyntheticFace.NEUTRAL_MOUTH, 0);
        clock.advance(33);
        s.onAnalysis(SyntheticFace.good(closed, 0.8f));
        feedGood(s, 1, 0.8f);
    }

    private LivenessEvent last() {
        return events.get(events.size() - 1);
    }

    @Test
    public void startEmitsPositioningWithAttemptInfo() {
        session();
        LivenessEvent e = last();
        assertEquals(LivenessState.POSITIONING, e.getState());
        assertEquals(1, e.getAttempt());
        assertEquals(3, e.getMaxAttempts());
        assertNull(e.getChallengeType());
        assertTrue(audit.has(LivenessAuditRecord.Type.SESSION_STARTED));
    }

    @Test
    public void highPassiveScorePassesWithoutChallenge() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.97f);
        assertEquals(LivenessState.SUCCESS, s.getState());
        for (LivenessEvent e : events) assertTrue(e.getState() != LivenessState.CHALLENGE);
    }

    @Test
    public void lowPassiveScoreTriggersChallengeThenSuccess() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        assertEquals(LivenessState.CHALLENGE, s.getState());
        assertEquals(ChallengeType.BLINK, last().getChallengeType());
        assertEquals(Integer.valueOf(8), last().getSecondsRemaining());
        blink(s);
        assertEquals(LivenessState.SUCCESS, s.getState());
    }

    @Test
    public void veryLowScoreIsPadFailureAndConsumesAttempt() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.05f);
        assertEquals(LivenessState.PAD_FAILED, s.getState());
        assertEquals(1, last().getAttempt());
        assertTrue(s.retry());
        assertEquals(LivenessState.POSITIONING, s.getState());
        assertEquals(2, last().getAttempt());
    }

    @Test
    public void challengeTimesOutViaTick() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        clock.advance(3_100);
        s.onTick();
        assertEquals(Integer.valueOf(5), last().getSecondsRemaining());
        clock.advance(5_000);
        s.onTick();
        assertEquals(LivenessState.CHALLENGE_TIMEOUT, s.getState());
    }

    @Test
    public void exhaustingAttemptsEndsInMaxRetries() {
        LivenessSession s = session();
        for (int attempt = 1; attempt <= 3; attempt++) {
            reachPassiveDecision(s, 0.05f);
            if (attempt < 3) {
                assertEquals(LivenessState.PAD_FAILED, s.getState());
                assertTrue(s.retry());
            }
        }
        assertEquals(LivenessState.MAX_RETRIES, s.getState());
        assertEquals(3, last().getAttempt());
        assertFalse(s.retry());
        assertTrue(s.getState().isTerminal());
    }

    @Test
    public void guidanceStatesDoNotConsumeAttempts() {
        LivenessSession s = session();
        s.onAnalysis(SyntheticFace.noFace());
        assertEquals(LivenessState.NO_FACE, s.getState());
        s.onAnalysis(SyntheticFace.twoFaces());
        assertEquals(LivenessState.MULTIPLE_FACES, s.getState());
        s.onAnalysis(SyntheticFace.poor(QualityHint.LIGHTING));
        assertEquals(LivenessState.POOR_QUALITY, s.getState());
        assertEquals(QualityHint.LIGHTING, last().getHint());
        s.onAnalysis(SyntheticFace.good(SyntheticFace.neutral(), 0.9f));
        assertEquals(LivenessState.POSITIONING, s.getState());
        assertEquals(1, last().getAttempt());
    }

    @Test
    public void faceLossDuringPassiveResetsScoring() {
        LivenessSession s = session();
        feedGood(s, policy.positioningStableFrames, 0.97f);
        feedGood(s, policy.passiveFrames - 1, 0.97f);
        s.onAnalysis(SyntheticFace.noFace());
        feedGood(s, policy.positioningStableFrames, 0.97f);
        assertEquals(LivenessState.PASSIVE_CHECK, s.getState());
        feedGood(s, policy.passiveFrames, 0.97f);
        assertEquals(LivenessState.SUCCESS, s.getState());
    }

    @Test
    public void faceLostDuringChallengeFailsAttempt() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        for (int i = 0; i < LivenessSession.FACE_LOSS_TOLERANCE_FRAMES; i++) {
            s.onAnalysis(SyntheticFace.noFace());
        }
        assertEquals(LivenessState.LIVENESS_FAILED, s.getState());
    }

    @Test
    public void faceSwapDuringChallengeFailsAttempt() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        FaceObservation elsewhere = SyntheticFace.face(600, 240, 200, SyntheticFace.OPEN_EAR, SyntheticFace.NEUTRAL_MOUTH, 0);
        s.onAnalysis(SyntheticFace.good(elsewhere, 0.8f));
        assertEquals(LivenessState.LIVENESS_FAILED, s.getState());
    }

    @Test
    public void lowScoresDuringChallengeAreRejectedAsAttack() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.5f);
        FaceObservation closed = SyntheticFace.face(320, 240, 200, SyntheticFace.CLOSED_EAR, SyntheticFace.NEUTRAL_MOUTH, 0);
        s.onAnalysis(SyntheticFace.good(closed, 0.1f));
        s.onAnalysis(SyntheticFace.good(SyntheticFace.neutral(), 0.1f));
        assertEquals(LivenessState.PAD_FAILED, s.getState());
    }

    @Test
    public void noChallengePoolMeansLowScoreFails() {
        policy.challengeTypes = new ArrayList<>();
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        assertEquals(LivenessState.LIVENESS_FAILED, s.getState());
    }

    @Test
    public void invalidFrameStreakRaisesInvalidFrames() {
        LivenessSession s = session();
        for (int i = 0; i < LivenessSession.MAX_INVALID_FRAME_STREAK; i++) s.onInvalidFrame();
        assertEquals(LivenessState.INVALID_FRAMES, s.getState());
        assertTrue(s.retry());
        assertEquals(1, last().getAttempt());
    }

    @Test
    public void systemErrorDoesNotConsumeAttemptAndRepeatsEventOnSecondFailure() {
        LivenessSession s = session();
        s.onSystemError(LivenessState.DEVICE_UNAVAILABLE, io.mosip.registration.livenessmanager.model.LivenessErrorCode.DEVICE_NOT_CONNECTED, "x");
        int n = events.size();
        s.onSystemError(LivenessState.DEVICE_UNAVAILABLE, io.mosip.registration.livenessmanager.model.LivenessErrorCode.DEVICE_NOT_CONNECTED, "x");
        assertEquals(n + 1, events.size());
        assertEquals(1, last().getAttempt());
    }

    @Test
    public void cancelReturnsToIdleAndIgnoresFrames() {
        LivenessSession s = session();
        s.cancel();
        assertEquals(LivenessState.IDLE, s.getState());
        int n = events.size();
        s.onAnalysis(SyntheticFace.good(SyntheticFace.neutral(), 0.99f));
        assertEquals(n, events.size());
    }

    @Test
    public void twoChainedChallengesBothRequired() {
        policy.challengeTypes = new ArrayList<>(Arrays.asList("BLINK", "SMILE"));
        policy.challengeCount = 2;
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        HashSet<ChallengeType> seen = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            ChallengeType t = last().getChallengeType();
            seen.add(t);
            if (t == ChallengeType.BLINK) {
                blink(s);
            } else {
                FaceObservation smile = SyntheticFace.face(320, 240, 200, SyntheticFace.OPEN_EAR, SyntheticFace.SMILE_MOUTH, 0);
                for (int k = 0; k < 3; k++) s.onAnalysis(SyntheticFace.good(smile, 0.8f));
            }
        }
        assertEquals(2, seen.size());
        assertEquals(LivenessState.SUCCESS, s.getState());
    }

    @Test
    public void auditRecordsScoresButEventsNever() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.97f);
        boolean scoreAudited = false;
        for (LivenessAuditRecord r : audit.records) {
            if (r.getType() == LivenessAuditRecord.Type.PASSIVE_DECISION) {
                scoreAudited = r.getDetails().containsKey("meanLive");
            }
        }
        assertTrue(scoreAudited);
        for (LivenessEvent e : events) {
            assertEquals(new HashSet<>(Arrays.asList("state", "workflow", "attempt", "maxAttempts",
                    "challengeType", "secondsRemaining", "hint")), e.toMap().keySet());
        }
    }

    @Test
    public void everyTransitionIsAudited() {
        LivenessSession s = session();
        reachPassiveDecision(s, 0.7f);
        blink(s);
        List<String> transitions = new ArrayList<>();
        for (LivenessAuditRecord r : audit.records) {
            if (r.getType() == LivenessAuditRecord.Type.STATE_TRANSITION) {
                transitions.add(r.getFromState() + ">" + r.getToState());
            }
        }
        assertEquals(Arrays.asList("IDLE>POSITIONING", "POSITIONING>PASSIVE_CHECK",
                "PASSIVE_CHECK>CHALLENGE", "CHALLENGE>SUCCESS"), transitions);
    }

    /** Regression for the on-device finding: an injected still photo scored 0.999 live. */
    @Test
    public void frozenFramesWithHighScoreAreEscalatedToChallenge() {
        LivenessSession s = session();
        for (int i = 0; i < policy.positioningStableFrames + policy.passiveFrames; i++) {
            clock.advance(33);
            s.onAnalysis(SyntheticFace.good(SyntheticFace.neutral(), 0.999f));
        }
        assertEquals(LivenessState.CHALLENGE, s.getState());
        clock.advance(9_000);
        s.onTick();
        assertEquals(LivenessState.CHALLENGE_TIMEOUT, s.getState());
        boolean flagged = false;
        for (LivenessAuditRecord r : audit.records) {
            if (r.getType() == LivenessAuditRecord.Type.PASSIVE_DECISION) {
                flagged = "true".equals(r.getDetails().get("staticScene"));
            }
        }
        assertTrue(flagged);
    }

    @Test
    public void motionCheckCanBeDisabledByPolicy() {
        policy.minPassiveMotion = 0;
        LivenessSession s = session();
        for (int i = 0; i < policy.positioningStableFrames + policy.passiveFrames; i++) {
            s.onAnalysis(SyntheticFace.good(SyntheticFace.neutral(), 0.97f));
        }
        assertEquals(LivenessState.SUCCESS, s.getState());
    }
}

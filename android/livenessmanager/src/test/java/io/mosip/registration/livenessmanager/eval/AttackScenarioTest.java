package io.mosip.registration.livenessmanager.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.audit.NoOpLivenessAuditLogger;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;

/**
 * Scripted presentation scenarios replayed through the real state machine and challenge detectors.
 * PAD score distributions are ASSUMED (synthetic), so these tests prove the decision logic, not model
 * accuracy. Real-model accuracy needs recorded sets (docs/liveness/07-performance-accuracy.md).
 */
public class AttackScenarioTest {

    static final long FRAME_MS = 66; // ~15 fps analysed

    /** Base actor: a face with Gaussian PAD scores that optionally reacts to challenges. */
    static class Actor implements SessionReplay.Actor {
        final Random rnd;
        final double padMean;
        final double padSd;
        final boolean reacts;
        final long reactionMs;
        /** For video replays: spontaneous blink period (0 = never). */
        final long naturalBlinkEveryMs;
        /** Live people, hand-held prints and replayed videos move; injected still frames do not. */
        boolean moves = true;
        long frame;
        long challengeSeenAt = -1;
        ChallengeType current;

        Actor(long seed, double padMean, double padSd, boolean reacts, long naturalBlinkEveryMs) {
            this.rnd = new Random(seed);
            this.padMean = padMean;
            this.padSd = padSd;
            this.reacts = reacts;
            this.reactionMs = 400 + rnd.nextInt(500);
            this.naturalBlinkEveryMs = naturalBlinkEveryMs;
        }

        float score() {
            return (float) Math.max(0, Math.min(1, padMean + rnd.nextGaussian() * padSd));
        }

        @Override
        public FrameAnalysis next(LivenessEvent last, long t) {
            double ear = SyntheticFace.OPEN_EAR, mouth = SyntheticFace.NEUTRAL_MOUTH, yaw = 0;
            if (last != null && last.getState() == LivenessState.CHALLENGE) {
                if (last.getChallengeType() != current) {
                    current = last.getChallengeType();
                    challengeSeenAt = t;
                }
                long since = t - challengeSeenAt;
                if (reacts && since >= reactionMs) {
                    long phase = since - reactionMs;
                    switch (current) {
                        case BLINK:
                            if (phase < 150) ear = SyntheticFace.CLOSED_EAR;
                            break;
                        case SMILE:
                            mouth = SyntheticFace.SMILE_MOUTH;
                            break;
                        case TURN_LEFT:
                            yaw = Math.min(35, phase / 15.0);
                            break;
                        case TURN_RIGHT:
                            yaw = -Math.min(35, phase / 15.0);
                            break;
                        default:
                    }
                }
            }
            if (naturalBlinkEveryMs > 0 && (t % naturalBlinkEveryMs) < 130) {
                ear = SyntheticFace.CLOSED_EAR;
            }
            float dx = moves ? (float) (rnd.nextGaussian() * 1.5) : 0f;
            frame++;
            FaceObservation f = SyntheticFace.face(320 + dx, 240, 200, ear, mouth, yaw);
            return SyntheticFace.good(f, score());
        }
    }

    private static LivenessPolicy policy(int challengeCount) {
        LivenessPolicy p = new LivenessPolicy();
        p.challengeCount = challengeCount;
        return p;
    }

    static Actor still(long seed, double padMean) {
        Actor a = new Actor(seed, padMean, 0.01, false, 0);
        a.moves = false;
        return a;
    }

    private static boolean run(LivenessPolicy p, SessionReplay.Actor a, long seed) {
        return SessionReplay.runAttempt(p, a, FRAME_MS, 60_000, seed, new NoOpLivenessAuditLogger()).accepted();
    }

    @Test
    public void scenarioOutcomesForSinglePolicy() {
        LivenessPolicy p = policy(1);
        // Live, confident PAD
        assertEquals(LivenessState.SUCCESS, SessionReplay.runAttempt(p, new Actor(1, 0.95, 0.02, true, 0),
                FRAME_MS, 60_000, 1, new NoOpLivenessAuditLogger()).finalState);
        // Live, low-confidence PAD (bad light): passes via the challenge
        assertEquals(LivenessState.SUCCESS, SessionReplay.runAttempt(p, new Actor(2, 0.7, 0.05, true, 0),
                FRAME_MS, 60_000, 2, new NoOpLivenessAuditLogger()).finalState);
        // Printed photo, PAD detects it
        assertEquals(LivenessState.PAD_FAILED, SessionReplay.runAttempt(p, new Actor(3, 0.08, 0.05, false, 0),
                FRAME_MS, 60_000, 3, new NoOpLivenessAuditLogger()).finalState);
        // High-quality print that fools PAD into the grey zone: cannot perform the challenge
        assertEquals(LivenessState.CHALLENGE_TIMEOUT, SessionReplay.runAttempt(p, new Actor(4, 0.6, 0.05, false, 0),
                FRAME_MS, 60_000, 4, new NoOpLivenessAuditLogger()).finalState);
        // Injected pristine still photo that fools PAD (seen on device: 0.999) is stopped by the motion rule
        assertEquals(LivenessState.CHALLENGE_TIMEOUT, SessionReplay.runAttempt(p, still(6, 0.99),
                FRAME_MS, 60_000, 6, new NoOpLivenessAuditLogger()).finalState);
        // Screen replay detected by PAD
        assertEquals(LivenessState.PAD_FAILED, SessionReplay.runAttempt(p, new Actor(5, 0.2, 0.05, false, 4000),
                FRAME_MS, 60_000, 5, new NoOpLivenessAuditLogger()).finalState);
    }

    @Test
    public void apcerBpcerOverSyntheticPopulation() {
        int n = 200;
        PadMetrics m1 = new PadMetrics();
        PadMetrics m2 = new PadMetrics();
        for (int i = 0; i < n; i++) {
            long s = 1000 + i;
            for (int c = 1; c <= 2; c++) {
                PadMetrics m = c == 1 ? m1 : m2;
                LivenessPolicy p = policy(c);
                m.addDecision(PadMetrics.BONA_FIDE, run(p, new Actor(s, 0.88, 0.08, true, 0), s));
                m.addDecision("print", run(p, new Actor(s, 0.25, 0.15, false, 0), s));
                m.addDecision("screen_replay_static", run(p, new Actor(s, 0.45, 0.15, false, 0), s));
                // The hard case: video replay of a live person who blinks naturally every ~2.5 s.
                m.addDecision("video_replay_blinking", run(p, new Actor(s, 0.6, 0.1, false, 2500), s));
                m.addDecision("injected_still_photo", run(p, still(s, 0.99), s));
            }
        }
        PadMetrics.Result r1 = m1.evaluateDecisions();
        PadMetrics.Result r2 = m2.evaluateDecisions();
        System.out.println("challengeCount=1: " + r1);
        System.out.println("challengeCount=2: " + r2);

        assertTrue("BPCER " + r1.bpcer, r1.bpcer <= 0.02);
        assertEquals(0.0, r1.apcerBySpecies.get("print"), 0.03);
        assertEquals(0.0, r1.apcerBySpecies.get("screen_replay_static"), 0.03);
        assertEquals(0.0, r1.apcerBySpecies.get("injected_still_photo"), 0.0);
        // Known weakness, kept visible: a single BLINK challenge can be met by a natural blink in a replay.
        assertTrue(r1.apcerBySpecies.get("video_replay_blinking") > 0.0);
        // Two chained challenges cut it sharply.
        assertTrue(r2.apcerBySpecies.get("video_replay_blinking") < r1.apcerBySpecies.get("video_replay_blinking") / 2);
    }

    @Test
    public void thresholdSweepOnScores() {
        PadMetrics m = new PadMetrics();
        Random r = new Random(9);
        for (int i = 0; i < 500; i++) {
            m.addScore(PadMetrics.BONA_FIDE, Math.min(1, Math.max(0, 0.9 + r.nextGaussian() * 0.06)));
            m.addScore("print", Math.min(1, Math.max(0, 0.2 + r.nextGaussian() * 0.12)));
        }
        double t = m.equalErrorThreshold();
        PadMetrics.Result at = m.evaluateAt(t);
        assertTrue(at.apcer < 0.01 && at.bpcer < 0.01);
        double strict = m.thresholdForBpcer(0.05);
        assertTrue(m.evaluateAt(strict).bpcer <= 0.05);
    }
}

package io.mosip.registration.livenessmanager.challenge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;

public class RandomChallengeManagerTest {

    @Test
    public void picksDistinctChallengesFromPool() {
        LivenessPolicy p = new LivenessPolicy();
        p.challengeCount = 3;
        RandomChallengeManager m = new RandomChallengeManager();
        m.reset(p);
        for (int i = 0; i < 50; i++) {
            List<ChallengeType> seq = m.pickSequence();
            assertEquals(3, seq.size());
            assertEquals(3, new HashSet<>(seq).size());
        }
    }

    @Test
    public void selectionIsRoughlyUniform() {
        LivenessPolicy p = new LivenessPolicy();
        RandomChallengeManager m = new RandomChallengeManager();
        m.reset(p);
        Map<ChallengeType, Integer> counts = new EnumMap<>(ChallengeType.class);
        int n = 4000;
        for (int i = 0; i < n; i++) counts.merge(m.pickSequence().get(0), 1, Integer::sum);
        for (ChallengeType t : ChallengeType.values()) {
            int c = counts.getOrDefault(t, 0);
            assertTrue(t + "=" + c, c > n / 4 * 0.8 && c < n / 4 * 1.2);
        }
    }

    @Test
    public void attemptsCountUpToMax() {
        LivenessPolicy p = new LivenessPolicy();
        p.maxRetries = 3;
        RandomChallengeManager m = new RandomChallengeManager();
        m.reset(p);
        assertEquals(1, m.getAttempt());
        assertTrue(m.registerFailure());
        assertEquals(2, m.getAttempt());
        assertTrue(m.registerFailure());
        assertEquals(3, m.getAttempt());
        assertFalse(m.registerFailure());
        assertEquals(3, m.getAttempt());
        assertEquals(8000, m.getChallengeTimeoutMillis());
    }
}

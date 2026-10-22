package io.mosip.registration.livenessmanager.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessErrorCode;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Unit tests verifying that DesktopLivenessAuditLogger correctly:
 * 1. Maps each LivenessAuditRecord type to the corresponding REG-LIV-00x DesktopAuditEvent.
 * 2. Employs record.toCompactString() as description.
 * 3. Never throws or breaks the session when the audit service fails (exception safety).
 * 4. Correctly audits FACE_LIVENESS_GATE_BLOCKED via DesktopLivenessGate.
 */
public class DesktopLivenessAuditLoggerTest {

    private static class AuditCall {
        final DesktopAuditEvent event;
        final String moduleId;
        final String moduleName;
        final String description;

        AuditCall(DesktopAuditEvent event, String moduleId, String moduleName, String description) {
            this.event = event;
            this.moduleId = moduleId;
            this.moduleName = moduleName;
            this.description = description;
        }
    }

    private static class MockAuditService implements DesktopAuditManagerService {
        final List<AuditCall> calls = new ArrayList<>();
        boolean throwException = false;

        @Override
        public void audit(DesktopAuditEvent auditEvent, String moduleId, String moduleName, String description) {
            if (throwException) {
                throw new RuntimeException("Simulated Derby database lock / write failure");
            }
            calls.add(new AuditCall(auditEvent, moduleId, moduleName, description));
        }
    }

    private MockAuditService mockService;
    private DesktopLivenessAuditLogger logger;

    @Before
    public void setUp() {
        mockService = new MockAuditService();
        logger = new DesktopLivenessAuditLogger(mockService);
    }

    @Test
    public void sessionStartedMapsToRegLiv001() {
        LivenessAuditRecord record = LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_STARTED, 1000L)
                .session("test-session", Workflow.RESIDENT, 1)
                .build();

        logger.log(record);

        assertEquals(1, mockService.calls.size());
        AuditCall call = mockService.calls.get(0);
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_SESSION, call.event);
        assertEquals("REG-LIV-001", call.event.getId());
        assertEquals(record.toCompactString(), call.description);
    }

    @Test
    public void sessionEndedAndPerformanceMapToRegLiv001() {
        LivenessAuditRecord endRecord = LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_ENDED, 1050L)
                .session("test-session", Workflow.RESIDENT, 1)
                .outcome(LivenessAuditRecord.Outcome.PASS)
                .build();
        logger.log(endRecord);

        LivenessAuditRecord perfRecord = LivenessAuditRecord.builder(LivenessAuditRecord.Type.PERFORMANCE, 1060L)
                .session("test-session", Workflow.RESIDENT, 1)
                .detail("durationMs", 60)
                .build();
        logger.log(perfRecord);

        assertEquals(2, mockService.calls.size());
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_SESSION, mockService.calls.get(0).event);
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_SESSION, mockService.calls.get(1).event);
    }

    @Test
    public void stateTransitionMapsToRegLiv002() {
        LivenessAuditRecord record = LivenessAuditRecord.builder(LivenessAuditRecord.Type.STATE_TRANSITION, 1100L)
                .transition(LivenessState.POSITIONING, LivenessState.PASSIVE_CHECK)
                .build();

        logger.log(record);

        assertEquals(1, mockService.calls.size());
        AuditCall call = mockService.calls.get(0);
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_STATE, call.event);
        assertEquals("REG-LIV-002", call.event.getId());
    }

    @Test
    public void passiveDecisionMapsToRegLiv003() {
        LivenessAuditRecord record = LivenessAuditRecord.builder(LivenessAuditRecord.Type.PASSIVE_DECISION, 1200L)
                .session("test-session", Workflow.RESIDENT, 1)
                .outcome(LivenessAuditRecord.Outcome.PASS)
                .detail("score", 0.94)
                .detail("threshold", 0.90)
                .build();

        logger.log(record);

        assertEquals(1, mockService.calls.size());
        AuditCall call = mockService.calls.get(0);
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_DECISION, call.event);
        assertEquals("REG-LIV-003", call.event.getId());
        assertTrue(call.description.contains("score=0.94"));
    }

    @Test
    public void challengeResultMapsToRegLiv004() {
        LivenessAuditRecord record = LivenessAuditRecord.builder(LivenessAuditRecord.Type.CHALLENGE_RESULT, 1300L)
                .challenge(ChallengeType.BLINK)
                .outcome(LivenessAuditRecord.Outcome.PASS)
                .build();

        logger.log(record);

        assertEquals(1, mockService.calls.size());
        AuditCall call = mockService.calls.get(0);
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_CHALLENGE, call.event);
        assertEquals("REG-LIV-004", call.event.getId());
    }

    @Test
    public void configAppliedAndRejectedMapToRegLiv005() {
        LivenessAuditRecord applied = LivenessAuditRecord.builder(LivenessAuditRecord.Type.CONFIG_APPLIED, 1400L)
                .outcome(LivenessAuditRecord.Outcome.PASS)
                .detail("configVersion", "2.1")
                .build();
        logger.log(applied);

        LivenessAuditRecord rejected = LivenessAuditRecord.builder(LivenessAuditRecord.Type.CONFIG_REJECTED, 1450L)
                .outcome(LivenessAuditRecord.Outcome.FAIL)
                .error(LivenessErrorCode.CONFIG_REJECTED)
                .detail("reason", "tampered SHA-256")
                .build();
        logger.log(rejected);

        assertEquals(2, mockService.calls.size());
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_CONFIG, mockService.calls.get(0).event);
        assertEquals("REG-LIV-005", mockService.calls.get(0).event.getId());
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_CONFIG, mockService.calls.get(1).event);
        assertEquals("REG-LIV-005", mockService.calls.get(1).event.getId());
    }

    @Test
    public void throwingAuditServiceNeverBreaksCaptureSession() {
        mockService.throwException = true;

        LivenessAuditRecord record = LivenessAuditRecord.builder(LivenessAuditRecord.Type.PASSIVE_DECISION, 1500L)
                .outcome(LivenessAuditRecord.Outcome.PASS)
                .build();

        // Must not throw exception
        logger.log(record);
        assertEquals(0, mockService.calls.size());
    }

    @Test
    public void gateBlocksCaptureWhenNoLivenessPassAndAuditsRegLiv006() {
        DesktopLivenessGate gate = new DesktopLivenessGate(mockService, () -> 1000L);

        // When policy is disabled, capture is allowed without audit
        boolean allowedDisabled = gate.checkAndConsumePass(Workflow.RESIDENT, false, "faceField");
        assertTrue(allowedDisabled);
        assertEquals(0, mockService.calls.size());

        // When policy is enabled but no pass issued, capture is blocked and audited
        boolean allowedBlocked = gate.checkAndConsumePass(Workflow.RESIDENT, true, "individualBiometrics_bio_CBEFF");
        assertFalse(allowedBlocked);
        assertEquals(1, mockService.calls.size());
        AuditCall call = mockService.calls.get(0);
        assertEquals(DesktopAuditEvent.FACE_LIVENESS_GATE_BLOCKED, call.event);
        assertEquals("REG-LIV-006", call.event.getId());
        assertTrue(call.description.contains("individualBiometrics_bio_CBEFF"));

        // When valid pass is issued, capture is allowed and consumed
        gate.issuePass(Workflow.RESIDENT, 60000L);
        boolean allowedWithPass = gate.checkAndConsumePass(Workflow.RESIDENT, true, "individualBiometrics_bio_CBEFF");
        assertTrue(allowedWithPass);

        // Single-use guarantee: second attempt without re-issuing is blocked
        boolean secondAttemptBlocked = gate.checkAndConsumePass(Workflow.RESIDENT, true, "individualBiometrics_bio_CBEFF");
        assertFalse(secondAttemptBlocked);
        assertEquals(2, mockService.calls.size());
    }
}

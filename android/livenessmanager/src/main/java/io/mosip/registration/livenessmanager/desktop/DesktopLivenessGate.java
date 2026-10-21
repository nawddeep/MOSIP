package io.mosip.registration.livenessmanager.desktop;

import java.util.Objects;
import io.mosip.registration.livenessmanager.core.LivenessGate;
import io.mosip.registration.livenessmanager.core.LivenessSession;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Gating component for Desktop face capture.
 * Ensures that face capture only proceeds when a fresh, unexpired, single-use liveness pass
 * exists. If liveness is required by policy but no valid pass exists, face capture is blocked
 * and an audit record {@link DesktopAuditEvent#FACE_LIVENESS_GATE_BLOCKED} is recorded in Derby.
 */
public final class DesktopLivenessGate {

    private final LivenessGate gate;
    private final DesktopAuditManagerService auditManagerService;

    public DesktopLivenessGate(DesktopAuditManagerService auditManagerService, LivenessSession.Clock clock) {
        this.auditManagerService = Objects.requireNonNull(auditManagerService, "auditManagerService cannot be null");
        this.gate = new LivenessGate(clock == null ? System::currentTimeMillis : clock);
    }

    public DesktopLivenessGate(DesktopAuditManagerService auditManagerService) {
        this(auditManagerService, System::currentTimeMillis);
    }

    /**
     * Issues a liveness pass token for the given workflow with the specified TTL.
     */
    public void issuePass(Workflow workflow, long ttlMillis) {
        gate.issue(workflow, ttlMillis);
    }

    /**
     * Verifies and consumes the pass for the given workflow.
     * If the workflow policy is enabled and no valid unexpired pass exists,
     * writes a {@link DesktopAuditEvent#FACE_LIVENESS_GATE_BLOCKED} audit to Derby
     * and returns {@code false}.
     *
     * @param workflow the target workflow (RESIDENT, OPERATOR, SUPERVISOR)
     * @param isPolicyEnabled whether face liveness is active for this workflow
     * @param fieldId the registration field identifier (e.g., "individualBiometrics_bio_CBEFF")
     * @return {@code true} if allowed, {@code false} if blocked
     */
    public boolean checkAndConsumePass(Workflow workflow, boolean isPolicyEnabled, String fieldId) {
        if (!isPolicyEnabled) {
            return true;
        }

        boolean passValid = gate.consume(workflow);
        if (!passValid) {
            try {
                auditManagerService.audit(
                        DesktopAuditEvent.FACE_LIVENESS_GATE_BLOCKED,
                        DesktopLivenessAuditLogger.MODULE_ID,
                        DesktopLivenessAuditLogger.MODULE_NAME,
                        "Face capture blocked, liveness not passed for field: " + (fieldId != null ? fieldId : "unknown")
                );
            } catch (Exception ignored) {
            }
            return false;
        }

        return true;
    }

    /**
     * Exposes the underlying LivenessGate.
     */
    public LivenessGate getGate() {
        return gate;
    }
}

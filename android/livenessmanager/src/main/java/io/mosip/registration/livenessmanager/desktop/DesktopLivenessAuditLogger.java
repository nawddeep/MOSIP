package io.mosip.registration.livenessmanager.desktop;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;

/**
 * Desktop implementation of {@link LivenessAuditLogger} that routes liveness audit records
 * into the Desktop Registration Client's {@link DesktopAuditManagerService}.
 * <p>
 * Rows land in the Apache Derby audit table.
 * Each record is rendered as {@code record.toCompactString()}.
 * All audit calls are wrapped in a try/catch block so a failed audit write never interrupts
 * face capture or liveness assessment.
 */
public final class DesktopLivenessAuditLogger implements LivenessAuditLogger {

    private static final Logger LOGGER = Logger.getLogger(DesktopLivenessAuditLogger.class.getName());
    public static final String MODULE_ID = "REGISTRATION";
    public static final String MODULE_NAME = "Registration";

    private final DesktopAuditManagerService auditManagerService;
    private FallbackAuditListener fallbackListener;

    /** Optional listener for diagnostics or mock capture during unit testing. */
    public interface FallbackAuditListener {
        void onAuditFailure(LivenessAuditRecord record, Exception cause);
    }

    public DesktopLivenessAuditLogger(DesktopAuditManagerService auditManagerService) {
        this.auditManagerService = Objects.requireNonNull(auditManagerService, "auditManagerService cannot be null");
    }

    public void setFallbackListener(FallbackAuditListener fallbackListener) {
        this.fallbackListener = fallbackListener;
    }

    @Override
    public void log(LivenessAuditRecord record) {
        if (record == null) {
            return;
        }

        try {
            DesktopAuditEvent event = eventFor(record.getType());
            String compactDescription = record.toCompactString();
            auditManagerService.audit(event, MODULE_ID, MODULE_NAME, compactDescription);
        } catch (Exception e) {
            // As required by the specification: a failed audit write must never break face capture.
            // Log only the exception class name.
            LOGGER.log(Level.WARNING, "Desktop liveness audit write failed: " + e.getClass().getSimpleName());
            if (fallbackListener != null) {
                try {
                    fallbackListener.onAuditFailure(record, e);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Maps each {@link LivenessAuditRecord.Type} to its corresponding {@link DesktopAuditEvent}.
     */
    public static DesktopAuditEvent eventFor(LivenessAuditRecord.Type type) {
        if (type == null) {
            return DesktopAuditEvent.FACE_LIVENESS_STATE;
        }

        switch (type) {
            case SESSION_STARTED:
            case SESSION_ENDED:
            case PERFORMANCE:
                return DesktopAuditEvent.FACE_LIVENESS_SESSION;
            case PASSIVE_DECISION:
                return DesktopAuditEvent.FACE_LIVENESS_DECISION;
            case CHALLENGE_RESULT:
                return DesktopAuditEvent.FACE_LIVENESS_CHALLENGE;
            case CONFIG_APPLIED:
            case CONFIG_REJECTED:
                return DesktopAuditEvent.FACE_LIVENESS_CONFIG;
            case STATE_TRANSITION:
            default:
                return DesktopAuditEvent.FACE_LIVENESS_STATE;
        }
    }
}

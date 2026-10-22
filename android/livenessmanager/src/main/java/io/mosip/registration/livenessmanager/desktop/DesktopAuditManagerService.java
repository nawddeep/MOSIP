package io.mosip.registration.livenessmanager.desktop;

/**
 * Service contract matching the Desktop Registration Client's {@code AuditManagerService}
 * (from {@code registration-services}). Writes audit entries into Apache Derby's audit table.
 */
public interface DesktopAuditManagerService {

    /**
     * Audits an event with details.
     *
     * @param auditEvent the audit event (e.g., REG-LIV-001 to REG-LIV-006)
     * @param moduleId the module identifier (e.g., "REGISTRATION")
     * @param moduleName the module display name (e.g., "Registration")
     * @param description the description string (from {@code record.toCompactString()})
     */
    void audit(DesktopAuditEvent auditEvent, String moduleId, String moduleName, String description);
}

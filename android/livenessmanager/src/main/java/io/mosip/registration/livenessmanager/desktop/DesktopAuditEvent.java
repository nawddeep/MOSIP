package io.mosip.registration.livenessmanager.desktop;

/**
 * Audit events for Face Liveness and Presentation Attack Detection (PAD)
 * matching MOSIP Desktop Registration Client {@code io.mosip.registration.constants.AuditEvent}.
 * <p>
 * REG-LIV-001 to REG-LIV-006 are stored directly into Apache Derby's audit table.
 * No face images, crops, thumbnails, or embeddings are ever stored.
 */
public enum DesktopAuditEvent {

    /**
     * REG-LIV-001: Session started, session ended, and performance latency timings.
     */
    FACE_LIVENESS_SESSION("REG-LIV-001", "USER_EVENT", "FACE_LIVENESS_SESSION", "Face liveness session: %s"),

    /**
     * REG-LIV-002: State transitions (POSITIONING, PASSIVE_CHECK, CHALLENGE, etc.).
     */
    FACE_LIVENESS_STATE("REG-LIV-002", "SYSTEM_EVENT", "FACE_LIVENESS_STATE", "Face liveness state: %s"),

    /**
     * REG-LIV-003: Passive decision with mean/min/max score and thresholds applied.
     */
    FACE_LIVENESS_DECISION("REG-LIV-003", "SYSTEM_EVENT", "FACE_LIVENESS_DECISION", "Face liveness decision: %s"),

    /**
     * REG-LIV-004: Challenge result (BLINK, SMILE, TURN_LEFT, TURN_RIGHT).
     */
    FACE_LIVENESS_CHALLENGE("REG-LIV-004", "USER_EVENT", "FACE_LIVENESS_CHALLENGE", "Face liveness challenge: %s"),

    /**
     * REG-LIV-005: Config applied or rejected.
     */
    FACE_LIVENESS_CONFIG("REG-LIV-005", "SYSTEM_EVENT", "FACE_LIVENESS_CONFIG", "Face liveness config: %s"),

    /**
     * REG-LIV-006: Face capture blocked because liveness had not passed.
     */
    FACE_LIVENESS_GATE_BLOCKED("REG-LIV-006", "SYSTEM_EVENT", "FACE_LIVENESS_GATE_BLOCKED", "Face capture blocked, liveness not passed: %s");

    private final String id;
    private final String type;
    private final String name;
    private final String description;

    DesktopAuditEvent(String id, String type, String name, String description) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}

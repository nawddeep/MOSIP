package io.mosip.registration.liveness;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.Components;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditEvent;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditManagerService;

/**
 * Writes face liveness audit records through MOSIP's real {@link AuditManagerService}, so they land in
 * {@code reg.app_audit_log} like every other client audit event (REG-LIV-001..006). No new table.
 *
 * <p>
 * The core's {@link DesktopAuditManagerService} contract carries no reference id, so the liveness session id
 * is taken from the compact record ({@code session=<id>;...}) and stored as {@code ref_id} with
 * {@code ref_id_type = LIVENESS_SESSION_ID}. Records without a session id use {@code LIVENESS}.
 *
 * <p>
 * Failures are thrown to the caller; {@code DesktopLivenessAuditLogger} catches them, so a failed audit write
 * never breaks a liveness session.
 *
 * <p>
 * Privacy: records only ever contain state names, outcomes and scores. As a last line of defence a
 * description that looks like image data (long base64 run, {@code data:image}, JPEG base64 header) is
 * replaced instead of written.
 */
@Component
public class MosipDesktopAuditManagerService implements DesktopAuditManagerService {

	/** {@code ref_id_type} used for liveness rows. */
	public static final String REF_ID_TYPE = "LIVENESS_SESSION_ID";
	static final String NO_SESSION_REF_ID = "LIVENESS";
	static final String REDACTED = "redacted: payload resembled image data";

	private static final int MAX_REF_ID_LENGTH = 64;
	private static final Pattern SESSION = Pattern.compile("(?:^|;)session=([^;]+)");
	private static final Pattern WORKFLOW = Pattern.compile("(?:^|;)workflow=([A-Z_]+)");
	private static final Pattern IMAGE_LIKE = Pattern.compile("[A-Za-z0-9+/=]{200,}|data:image|/9j/");

	private final AuditManagerService auditManagerService;

	@Autowired
	public MosipDesktopAuditManagerService(AuditManagerService auditManagerService) {
		this.auditManagerService = Objects.requireNonNull(auditManagerService, "auditManagerService");
	}

	@Override
	public void audit(DesktopAuditEvent auditEvent, String moduleId, String moduleName, String description) {
		auditManagerService.audit(toAuditEvent(auditEvent), componentFor(description), refIdOf(description),
				REF_ID_TYPE, sanitize(description));
	}

	static AuditEvent toAuditEvent(DesktopAuditEvent event) {
		if (event == null) {
			throw new IllegalArgumentException("auditEvent cannot be null");
		}
		switch (event) {
		case FACE_LIVENESS_SESSION:
			return AuditEvent.FACE_LIVENESS_SESSION;
		case FACE_LIVENESS_STATE:
			return AuditEvent.FACE_LIVENESS_STATE;
		case FACE_LIVENESS_DECISION:
			return AuditEvent.FACE_LIVENESS_DECISION;
		case FACE_LIVENESS_CHALLENGE:
			return AuditEvent.FACE_LIVENESS_CHALLENGE;
		case FACE_LIVENESS_CONFIG:
			return AuditEvent.FACE_LIVENESS_CONFIG;
		case FACE_LIVENESS_GATE_BLOCKED:
			return AuditEvent.FACE_LIVENESS_GATE_BLOCKED;
		default:
			throw new IllegalArgumentException("Unmapped liveness audit event: " + event);
		}
	}

	/**
	 * Picks an existing MOSIP module for the row (no new {@link Components} entry): biometrics for resident,
	 * login for operator, OS authentication for supervisor.
	 */
	static Components componentFor(String description) {
		String workflow = match(WORKFLOW, description);
		if ("OPERATOR".equals(workflow)) {
			return Components.LOGIN;
		}
		if ("SUPERVISOR".equals(workflow)) {
			return Components.REG_OS_AUTH;
		}
		return Components.REG_BIOMETRICS;
	}

	static String refIdOf(String description) {
		String session = match(SESSION, description);
		if (session == null || session.trim().isEmpty()) {
			return NO_SESSION_REF_ID;
		}
		return session.length() <= MAX_REF_ID_LENGTH ? session : session.substring(0, MAX_REF_ID_LENGTH);
	}

	static String sanitize(String description) {
		if (description == null) {
			return "";
		}
		return IMAGE_LIKE.matcher(description).find() ? REDACTED : description;
	}

	private static String match(Pattern pattern, String text) {
		if (text == null) {
			return null;
		}
		Matcher m = pattern.matcher(text);
		return m.find() ? m.group(1) : null;
	}
}

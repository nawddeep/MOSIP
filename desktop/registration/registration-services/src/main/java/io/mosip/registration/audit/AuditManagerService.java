package io.mosip.registration.audit;

import org.springframework.data.auditing.AuditingHandler;

import io.mosip.kernel.auditmanager.builder.AuditRequestBuilder;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.Components;
import io.mosip.registration.dto.ResponseDTO;

/**
 * The wrapper interface to log the audits
 * 
 * @author Balaji Sridharan
 * @since 1.0.0
 */
public interface AuditManagerService {

	/**
	 * Audits the events across Registration-Client Module.
	 * <p>
	 * This method takes {@link AuditEvent}, {@link Components}, audit
	 * description, refId and refIdType as inputs, other values from Session Context object
	 * namely createdBy, sessionUserId and sessionUserName to build the
	 * {@link AuditRequestBuilder} object. This {@link AuditRequestBuilder} object will be passed
	 * to the {@link AuditingHandler} which will persist the audit event in
	 * database.
	 * 
	 * @param auditEventEnum
	 *            this {@code Enum} contains the event details namely eventId,
	 *            eventType and eventName
	 * @param appModuleEnum
	 *            this {@code Enum} contains the application module details namely
	 *            moduleId and moduleName
	 * @param refId
	 *            the ref id of the audit event
	 * @param refIdType
	 *            the ref id type of the audit event
	 */
	void audit(AuditEvent auditEventEnum, Components appModuleEnum, String refId,
			String refIdType);

	/**
	 * Audits an event with a caller-supplied description (for example per-record
	 * detail). The description is truncated to 2048 characters (the size of
	 * {@code log_desc}). When a description is supplied the caller also owns the
	 * reference: {@code refId} and {@code refIdType} are stored as given instead of
	 * being replaced by the logged-in user id / registration id. Passing a
	 * {@code null} description behaves exactly like
	 * {@link #audit(AuditEvent, Components, String, String)}.
	 *
	 * @param auditEventEnum
	 *            the event
	 * @param appModuleEnum
	 *            the module
	 * @param refId
	 *            the ref id of the audit event
	 * @param refIdType
	 *            the ref id type of the audit event
	 * @param description
	 *            audit description, or {@code null} to use the event description
	 */
	void audit(AuditEvent auditEventEnum, Components appModuleEnum, String refId,
			String refIdType, String description);
	
	/**
	 * Delete Audit Logs
	 * 
	 * @return response of deletion
	 */
	ResponseDTO deleteAuditLogs();

}
package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.Components;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditEvent;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessAuditLogger;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

public class MosipDesktopAuditManagerServiceTest {

	private AuditManagerService audit;
	private MosipDesktopAuditManagerService adapter;

	@Before
	public void setUp() {
		audit = mock(AuditManagerService.class);
		adapter = new MosipDesktopAuditManagerService(audit);
	}

	@Test
	public void everyDesktopEventMapsToTheSameRegLivIdNameAndType() {
		for (DesktopAuditEvent e : DesktopAuditEvent.values()) {
			AuditEvent mapped = MosipDesktopAuditManagerService.toAuditEvent(e);
			assertEquals(e.getId(), mapped.getId());
			assertEquals(e.getName(), mapped.getName());
			// MOSIP stores event_type as USER / SYSTEM (AuditEventType codes); the core's enum spells it
			// USER_EVENT / SYSTEM_EVENT. The class (user vs system) must agree.
			assertEquals(e.getType().startsWith("USER") ? "USER" : "SYSTEM", mapped.getType());
		}
		assertEquals(
				Arrays.asList("REG-LIV-001", "REG-LIV-002", "REG-LIV-003", "REG-LIV-004", "REG-LIV-005",
						"REG-LIV-006"),
				Arrays.asList(AuditEvent.FACE_LIVENESS_SESSION.getId(), AuditEvent.FACE_LIVENESS_STATE.getId(),
						AuditEvent.FACE_LIVENESS_DECISION.getId(), AuditEvent.FACE_LIVENESS_CHALLENGE.getId(),
						AuditEvent.FACE_LIVENESS_CONFIG.getId(), AuditEvent.FACE_LIVENESS_GATE_BLOCKED.getId()));
	}

	@Test
	public void eachRecordTypeReachesTheRightEventWithTheSessionAsReference() {
		DesktopLivenessAuditLogger logger = new DesktopLivenessAuditLogger(adapter);
		for (LivenessAuditRecord.Type type : LivenessAuditRecord.Type.values()) {
			LivenessAuditRecord record = LivenessAuditRecord.builder(type, 1000L)
					.session("sess-1", Workflow.OPERATOR, 1)
					.transition(LivenessState.POSITIONING, LivenessState.PASSIVE_CHECK).build();
			logger.log(record);

			ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
			ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
			verify(audit, atLeastOnce()).audit(event.capture(), eq(Components.LOGIN), eq("sess-1"),
					eq(MosipDesktopAuditManagerService.REF_ID_TYPE), description.capture());
			assertEquals(DesktopLivenessAuditLogger.eventFor(type).getId(), event.getValue().getId());
			assertEquals(record.toCompactString(), description.getValue());
			reset(audit);
		}
	}

	@Test
	public void workflowPicksAnExistingMosipModule() {
		assertEquals(Components.REG_BIOMETRICS, MosipDesktopAuditManagerService.componentFor("workflow=RESIDENT"));
		assertEquals(Components.LOGIN, MosipDesktopAuditManagerService.componentFor("a=1;workflow=OPERATOR"));
		assertEquals(Components.REG_OS_AUTH,
				MosipDesktopAuditManagerService.componentFor("workflow=SUPERVISOR;x=1"));
		assertEquals(Components.REG_BIOMETRICS, MosipDesktopAuditManagerService.componentFor(null));
	}

	@Test
	public void recordWithoutSessionUsesAFixedReference() {
		adapter.audit(DesktopAuditEvent.FACE_LIVENESS_CONFIG, "m", "n", "type=CONFIG_APPLIED;ts=1");
		verify(audit).audit(eq(AuditEvent.FACE_LIVENESS_CONFIG), any(Components.class), eq("LIVENESS"),
				eq(MosipDesktopAuditManagerService.REF_ID_TYPE), eq("type=CONFIG_APPLIED;ts=1"));
	}

	@Test
	public void sessionReferenceIsCappedAtTheColumnSize() {
		StringBuilder longId = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			longId.append('a');
		}
		assertEquals(64, MosipDesktopAuditManagerService.refIdOf("session=" + longId).length());
	}

	@Test
	public void imageLikeDescriptionIsNeverWritten() {
		StringBuilder base64 = new StringBuilder("session=s;frame=");
		for (int i = 0; i < 400; i++) {
			base64.append('A');
		}
		adapter.audit(DesktopAuditEvent.FACE_LIVENESS_STATE, "m", "n", base64.toString());
		ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
		verify(audit).audit(any(AuditEvent.class), any(Components.class), anyString(), anyString(),
				description.capture());
		assertEquals(MosipDesktopAuditManagerService.REDACTED, description.getValue());

		reset(audit);
		adapter.audit(DesktopAuditEvent.FACE_LIVENESS_STATE, "m", "n", "x=data:image/jpeg;base64,/9j/4AAQ");
		verify(audit).audit(any(AuditEvent.class), any(Components.class), anyString(), anyString(),
				eq(MosipDesktopAuditManagerService.REDACTED));
	}

	@Test
	public void normalRecordsAreNotMistakenForImages() {
		String record = LivenessAuditRecord.builder(LivenessAuditRecord.Type.PASSIVE_DECISION, 5L)
				.session("0c8c8d3e-1111-2222-3333-444455556666", Workflow.RESIDENT, 2)
				.outcome(LivenessAuditRecord.Outcome.PASS).detail("score", 0.94).build().toCompactString();
		String sanitized = MosipDesktopAuditManagerService.sanitize(record);
		assertFalse(sanitized, MosipDesktopAuditManagerService.REDACTED.equals(sanitized));
		assertTrue(sanitized.contains("score=0.94"));
	}

	@Test
	public void aFailingAuditWriteNeverBreaksTheLivenessLogger() {
		doThrow(new RuntimeException("derby down")).when(audit).audit(any(AuditEvent.class),
				any(Components.class), anyString(), anyString(), anyString());
		DesktopLivenessAuditLogger logger = new DesktopLivenessAuditLogger(adapter);
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_STARTED, 1L)
				.session("s", Workflow.RESIDENT, 1).build());
		verify(audit).audit(any(AuditEvent.class), any(Components.class), anyString(), anyString(), anyString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullEventIsRejected() {
		adapter.audit(null, "m", "n", "d");
	}

	@Test
	public void nothingIsWrittenForANullRecord() {
		new DesktopLivenessAuditLogger(adapter).log(null);
		verify(audit, never()).audit(any(AuditEvent.class), any(Components.class), anyString(), anyString(),
				anyString());
	}
}

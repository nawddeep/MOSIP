package io.mosip.registration.test.audit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.mosip.kernel.auditmanager.request.AuditRequestDto;
import io.mosip.kernel.core.auditmanager.spi.AuditHandler;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.audit.AuditManagerSerivceImpl;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.dao.AuditDAO;
import io.mosip.registration.dto.RegistrationDTO;

/**
 * The description overload of {@code AuditManagerService#audit}: existing four-argument calls behave exactly as
 * before; a supplied description is stored (truncated to 2048) together with the caller's own reference.
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*" })
@PrepareForTest({ InetAddress.class, SessionContext.class, ApplicationContext.class, DateUtils.class })
public class AuditManagerDescriptionOverloadTest {

	@Rule
	public MockitoRule mockitoRule = MockitoJUnit.rule();

	@Mock
	private AuditHandler<AuditRequestDto> auditHandler;

	@Mock
	private AuditDAO auditDAO;

	@Mock
	private SessionContext.SecurityContext securityContext;

	@InjectMocks
	private AuditManagerSerivceImpl auditManager;

	@Before
	public void init() throws Exception {
		PowerMockito.mockStatic(InetAddress.class, SessionContext.class);
		PowerMockito.when(SessionContext.securityContext()).thenReturn(securityContext);
		Map<String, Object> session = new HashMap<>();
		session.put(RegistrationConstants.REGISTRATION_DATA, new RegistrationDTO());
		PowerMockito.when(securityContext.getUserId()).thenReturn("user");
		PowerMockito.when(SessionContext.map()).thenReturn(session);
		PowerMockito.doReturn("userId").when(SessionContext.class, "userId");
		PowerMockito.doReturn("userName").when(SessionContext.class, "userName");

		Map<String, Object> map = new HashMap<>();
		map.put(RegistrationConstants.DEFAULT_HOST_IP, "127.0.0.0");
		map.put(RegistrationConstants.DEFAULT_HOST_NAME, "LOCALHOST");
		map.put(RegistrationConstants.APP_NAME, "REGISTRATION");
		map.put(RegistrationConstants.APP_ID, "REG");
		PowerMockito.mockStatic(ApplicationContext.class);
		when(ApplicationContext.map()).thenReturn(map);
		when(auditHandler.addAudit(Mockito.any(AuditRequestDto.class))).thenReturn(true);
	}

	private AuditRequestDto captured() {
		ArgumentCaptor<AuditRequestDto> captor = ArgumentCaptor.forClass(AuditRequestDto.class);
		verify(auditHandler).addAudit(captor.capture());
		return captor.getValue();
	}

	@Test
	public void fourArgumentCallIsUnchanged() {
		auditManager.audit(AuditEvent.PACKET_APPROVED, Components.PACKET_CREATOR, "id", "ref");

		AuditRequestDto dto = captured();
		assertEquals(AuditEvent.PACKET_APPROVED.getDescription(), dto.getDescription());
		// The logged-in user still replaces the reference, as before.
		assertEquals("userId", dto.getId());
		assertEquals(AuditReferenceIdTypes.USER_ID.getReferenceTypeId(), dto.getIdType());
	}

	@Test
	public void aNullDescriptionBehavesLikeTheFourArgumentCall() {
		auditManager.audit(AuditEvent.PACKET_APPROVED, Components.PACKET_CREATOR, "id", "ref", null);

		AuditRequestDto dto = captured();
		assertEquals(AuditEvent.PACKET_APPROVED.getDescription(), dto.getDescription());
		assertEquals("userId", dto.getId());
		assertEquals(AuditReferenceIdTypes.USER_ID.getReferenceTypeId(), dto.getIdType());
	}

	@Test
	public void aSuppliedDescriptionAndReferenceAreStoredAsGiven() {
		auditManager.audit(AuditEvent.FACE_LIVENESS_SESSION, Components.REG_BIOMETRICS, "sess-9",
				"LIVENESS_SESSION_ID", "type=SESSION_STARTED;session=sess-9");

		AuditRequestDto dto = captured();
		assertEquals("REG-LIV-001", dto.getEventId());
		assertEquals("type=SESSION_STARTED;session=sess-9", dto.getDescription());
		assertEquals("sess-9", dto.getId());
		assertEquals("LIVENESS_SESSION_ID", dto.getIdType());
		assertEquals(Components.REG_BIOMETRICS.getId(), dto.getModuleId());
	}

	@Test
	public void descriptionsAreTruncatedTo2048Characters() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 5000; i++) {
			sb.append((char) ('a' + i % 26));
		}
		auditManager.audit(AuditEvent.FACE_LIVENESS_STATE, Components.REG_BIOMETRICS, "s", "T", sb.toString());

		AuditRequestDto dto = captured();
		assertEquals(2048, dto.getDescription().length());
		assertTrue(sb.toString().startsWith(dto.getDescription()));
	}

	@Test
	public void aDescriptionOfExactly2048CharactersIsKeptWhole() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 2048; i++) {
			sb.append('x');
		}
		auditManager.audit(AuditEvent.FACE_LIVENESS_STATE, Components.REG_BIOMETRICS, "s", "T", sb.toString());

		assertEquals(sb.toString(), captured().getDescription());
	}
}

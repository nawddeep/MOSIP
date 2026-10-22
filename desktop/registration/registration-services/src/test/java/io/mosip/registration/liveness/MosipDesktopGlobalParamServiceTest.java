package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import io.mosip.registration.entity.GlobalParam;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessConfigAdapter;
import io.mosip.registration.service.config.GlobalParamService;

public class MosipDesktopGlobalParamServiceTest {

	private static final String KEY = DesktopLivenessConfigAdapter.GLOBAL_PARAM_KEY;

	private GlobalParamService globalParams;
	private MosipDesktopGlobalParamService adapter;

	@Before
	public void setUp() {
		globalParams = mock(GlobalParamService.class);
		adapter = new MosipDesktopGlobalParamService(globalParams);
	}

	private static GlobalParam row(String val, Boolean deleted) {
		GlobalParam p = new GlobalParam();
		p.setVal(val);
		p.setIsDeleted(deleted);
		return p;
	}

	@Test
	public void keyIsTheDocumentedGlobalParam() {
		assertEquals("mosip.registration.face.liveness.config", KEY);
	}

	@Test
	public void returnsTheValueOfALiveRow() {
		when(globalParams.getGlobalParam(KEY)).thenReturn(row("{\"schemaVersion\":1}", false));
		assertEquals("{\"schemaVersion\":1}", adapter.getGlobalParam(KEY));
	}

	@Test
	public void rowWithoutDeletedFlagCountsAsLive() {
		when(globalParams.getGlobalParam(KEY)).thenReturn(row("v", null));
		assertEquals("v", adapter.getGlobalParam(KEY));
	}

	@Test
	public void deletedRowCountsAsAbsent() {
		when(globalParams.getGlobalParam(KEY)).thenReturn(row("{\"schemaVersion\":1}", true));
		assertNull(adapter.getGlobalParam(KEY));
	}

	@Test
	public void missingOrBlankRowCountsAsAbsent() {
		assertNull(adapter.getGlobalParam(KEY));
		when(globalParams.getGlobalParam(KEY)).thenReturn(row("   ", false));
		assertNull(adapter.getGlobalParam(KEY));
		when(globalParams.getGlobalParam(KEY)).thenReturn(row(null, false));
		assertNull(adapter.getGlobalParam(KEY));
	}

	@Test
	public void aReadFailureFallsBackInsteadOfThrowing() {
		when(globalParams.getGlobalParam(KEY)).thenThrow(new IllegalStateException("db"));
		assertNull(adapter.getGlobalParam(KEY));
	}
}

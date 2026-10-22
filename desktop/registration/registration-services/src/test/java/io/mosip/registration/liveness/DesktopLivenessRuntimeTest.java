package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.mosip.registration.livenessmanager.config.CanonicalJson;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditEvent;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditManagerService;
import io.mosip.registration.livenessmanager.desktop.DesktopGlobalParamService;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessConfigAdapter;
import io.mosip.registration.livenessmanager.model.Workflow;

public class DesktopLivenessRuntimeTest {

	/** Only what differs from the defaults: global_param.val is VARCHAR(512). */
	private static final String COMPACT = "{\"schemaVersion\":1,\"configVersion\":\"c1\",\"policies\":{"
			+ "\"resident\":{\"enabled\":true,\"challengeCount\":2},"
			+ "\"operator\":{\"enabled\":true},\"supervisor\":{\"enabled\":true}}}";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static class Params implements DesktopGlobalParamService {
		String value;

		@Override
		public String getGlobalParam(String code) {
			return DesktopLivenessConfigAdapter.GLOBAL_PARAM_KEY.equals(code) ? value : null;
		}
	}

	private static class Audit implements DesktopAuditManagerService {
		final List<DesktopAuditEvent> events = new ArrayList<>();
		final List<String> descriptions = new ArrayList<>();

		@Override
		public void audit(DesktopAuditEvent event, String moduleId, String moduleName, String description) {
			events.add(event);
			descriptions.add(description);
		}
	}

	private Params params;
	private Audit audit;
	private File dir;
	private DesktopLivenessRuntime runtime;

	@Before
	public void setUp() throws Exception {
		params = new Params();
		audit = new Audit();
		dir = tmp.newFolder("liveness");
		runtime = new DesktopLivenessRuntime(params, audit, dir);
	}

	private static String sign(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		o.remove("integrity");
		JsonObject integrity = new JsonObject();
		integrity.addProperty("algorithm", "SHA-256");
		integrity.addProperty("value", CanonicalJson.integrityDigest(o));
		o.add("integrity", integrity);
		return o.toString();
	}

	// ---- config ---------------------------------------------------------------------------------------------

	@Test
	public void aSignedCompactConfigFitsTheGlobalParamColumn() {
		String signed = sign(COMPACT);
		assertTrue("length " + signed.length(), signed.length() <= 512);
	}

	@Test
	public void bundledDefaultAppliesUntilSomethingIsSynced() {
		assertTrue(runtime.isEnabled(Workflow.RESIDENT));
		assertFalse(runtime.isEnabled(Workflow.OPERATOR));
		assertFalse(runtime.isEnabled(Workflow.SUPERVISOR));
	}

	@Test
	public void aSyncedCompactConfigIsAppliedAndAudited() {
		params.value = sign(COMPACT);
		assertTrue(runtime.refreshConfig(true));
		assertEquals("c1", runtime.getConfig().getConfig().configVersion);
		assertTrue(runtime.isEnabled(Workflow.OPERATOR));
		assertTrue(runtime.isEnabled(Workflow.SUPERVISOR));
		assertEquals(2, runtime.getConfig().getPolicy(Workflow.RESIDENT).challengeCount);
		assertTrue(audit.events.contains(DesktopAuditEvent.FACE_LIVENESS_CONFIG));
	}

	@Test
	public void aTamperedConfigIsRejectedAndTheOldOneStays() {
		params.value = sign(COMPACT).replace("\"challengeCount\":2", "\"challengeCount\":3");
		assertFalse(runtime.refreshConfig(true));
		assertFalse(runtime.isEnabled(Workflow.OPERATOR));
		assertTrue(audit.events.contains(DesktopAuditEvent.FACE_LIVENESS_CONFIG));
		assertTrue(audit.descriptions.toString(), audit.descriptions.toString().contains("CONFIG_REJECTED"));
	}

	@Test
	public void anUnknownKeyIsRejected() {
		params.value = sign(COMPACT.replace("\"configVersion\"", "\"surprise\":1,\"configVersion\""));
		assertFalse(runtime.refreshConfig(true));
		assertFalse(runtime.isEnabled(Workflow.OPERATOR));
	}

	@Test
	public void anOutOfRangeValueIsRejected() {
		params.value = sign(COMPACT.replace("\"challengeCount\":2", "\"challengeCount\":9"));
		assertFalse(runtime.refreshConfig(true));
		assertFalse(runtime.isEnabled(Workflow.OPERATOR));
	}

	@Test
	public void anOfflineRestartUsesTheLastValidSyncedConfig() {
		params.value = sign(COMPACT);
		assertTrue(runtime.refreshConfig(true));

		// "Restart": new runtime on the same data folder, nothing synced any more.
		DesktopLivenessRuntime restarted = new DesktopLivenessRuntime(new Params(), new Audit(), dir);
		assertEquals("c1", restarted.getConfig().getConfig().configVersion);
		assertTrue(restarted.isEnabled(Workflow.OPERATOR));
	}

	// ---- gate -----------------------------------------------------------------------------------------------

	@Test
	public void aWorkflowWithLivenessDisabledNeedsNoPassAndIsNotAudited() {
		assertTrue(runtime.checkAndConsumePass(Workflow.OPERATOR, "login"));
		assertFalse(audit.events.contains(DesktopAuditEvent.FACE_LIVENESS_GATE_BLOCKED));
	}

	@Test
	public void anEnabledWorkflowIsBlockedWithoutAPassAndTheBlockIsAudited() {
		assertFalse(runtime.checkAndConsumePass(Workflow.RESIDENT, "individualBiometrics"));
		int i = audit.events.indexOf(DesktopAuditEvent.FACE_LIVENESS_GATE_BLOCKED);
		assertTrue(i >= 0);
		assertTrue(audit.descriptions.get(i), audit.descriptions.get(i).contains("individualBiometrics"));
	}

	@Test
	public void aPassIsSingleUse() {
		runtime.getGate().issue(Workflow.RESIDENT, 60_000);
		assertTrue(runtime.checkAndConsumePass(Workflow.RESIDENT, "face"));
		assertFalse(runtime.checkAndConsumePass(Workflow.RESIDENT, "face"));
	}

	@Test
	public void aPassExpiresAfterItsTtl() throws Exception {
		runtime.getGate().issue(Workflow.RESIDENT, 1);
		Thread.sleep(30);
		assertFalse(runtime.checkAndConsumePass(Workflow.RESIDENT, "face"));
	}

	@Test
	public void aPassForOneWorkflowDoesNotOpenAnother() {
		params.value = sign(COMPACT);
		runtime.refreshConfig(true);
		runtime.getGate().issue(Workflow.OPERATOR, 60_000);
		assertFalse(runtime.checkAndConsumePass(Workflow.SUPERVISOR, "approval"));
		assertTrue(runtime.checkAndConsumePass(Workflow.OPERATOR, "login"));
	}
}

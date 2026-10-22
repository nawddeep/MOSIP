package io.mosip.registration.livenessmanager.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.config.CanonicalJson;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.Workflow;

public class DesktopLivenessConfigAdapterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static class RecordingAuditLogger implements LivenessAuditLogger {
        final List<LivenessAuditRecord> records = new ArrayList<>();

        @Override
        public void log(LivenessAuditRecord record) {
            records.add(record);
        }
    }

    private static class MutableParamService implements DesktopGlobalParamService {
        String value;

        @Override
        public String getGlobalParam(String code) {
            if (DesktopLivenessConfigAdapter.GLOBAL_PARAM_KEY.equals(code)) {
                return value;
            }
            return null;
        }
    }

    private MutableParamService paramService;
    private RecordingAuditLogger auditLogger;
    private File cacheDir;

    @Before
    public void setUp() throws Exception {
        paramService = new MutableParamService();
        auditLogger = new RecordingAuditLogger();
        cacheDir = tempFolder.newFolder("liveness_cache");
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

    private static String sampleConfig(String version, double passiveThreshold, int challengeCount) {
        return "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"configVersion\": \"" + version + "\",\n" +
                "  \"livenessTokenTtlSeconds\": 60,\n" +
                "  \"performance\": {\"frameSkip\": 1, \"padEveryNthFrame\": 1},\n" +
                "  \"policies\": {\n" +
                "    \"resident\": {\n" +
                "      \"enabled\": true,\n" +
                "      \"passiveThreshold\": " + passiveThreshold + ",\n" +
                "      \"padRejectThreshold\": 0.30,\n" +
                "      \"challengeTypes\": [\"BLINK\", \"SMILE\", \"TURN_LEFT\", \"TURN_RIGHT\"],\n" +
                "      \"challengeCount\": " + challengeCount + ",\n" +
                "      \"challengeTimeoutSeconds\": 8,\n" +
                "      \"maxRetries\": 3,\n" +
                "      \"minFaceSize\": 0.25,\n" +
                "      \"maxFaces\": 1,\n" +
                "      \"passiveFrames\": 5,\n" +
                "      \"positioningStableFrames\": 5,\n" +
                "      \"minPassiveMotion\": 0.002,\n" +
                "      \"quality\": {\"minBrightness\": 50, \"maxBrightness\": 220, \"minSharpness\": 25, \"maxMotion\": 0.15}\n" +
                "    },\n" +
                "    \"operator\": {\n" +
                "      \"enabled\": false,\n" +
                "      \"passiveThreshold\": 0.90,\n" +
                "      \"padRejectThreshold\": 0.30,\n" +
                "      \"challengeTypes\": [\"BLINK\"],\n" +
                "      \"challengeCount\": 1,\n" +
                "      \"challengeTimeoutSeconds\": 8,\n" +
                "      \"maxRetries\": 3,\n" +
                "      \"minFaceSize\": 0.25,\n" +
                "      \"maxFaces\": 1,\n" +
                "      \"passiveFrames\": 5,\n" +
                "      \"positioningStableFrames\": 5,\n" +
                "      \"minPassiveMotion\": 0.002,\n" +
                "      \"quality\": {\"minBrightness\": 50, \"maxBrightness\": 220, \"minSharpness\": 25, \"maxMotion\": 0.15}\n" +
                "    },\n" +
                "    \"supervisor\": {\n" +
                "      \"enabled\": false,\n" +
                "      \"passiveThreshold\": 0.90,\n" +
                "      \"padRejectThreshold\": 0.30,\n" +
                "      \"challengeTypes\": [\"BLINK\"],\n" +
                "      \"challengeCount\": 1,\n" +
                "      \"challengeTimeoutSeconds\": 8,\n" +
                "      \"maxRetries\": 3,\n" +
                "      \"minFaceSize\": 0.25,\n" +
                "      \"maxFaces\": 1,\n" +
                "      \"passiveFrames\": 5,\n" +
                "      \"positioningStableFrames\": 5,\n" +
                "      \"minPassiveMotion\": 0.002,\n" +
                "      \"quality\": {\"minBrightness\": 50, \"maxBrightness\": 220, \"minSharpness\": 25, \"maxMotion\": 0.15}\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    @Test
    public void bundledDefaultIsLoadedInitially() {
        String bundled = sampleConfig("1.0-default", 0.90, 2);
        DesktopLivenessConfigAdapter adapter = new DesktopLivenessConfigAdapter(
                paramService, cacheDir, bundled, auditLogger);

        LivenessConfig config = adapter.getConfig();
        assertNotNull(config);
        assertEquals("1.0-default", config.configVersion);
        assertEquals(0.90, adapter.getPolicy(Workflow.RESIDENT).passiveThreshold, 0.001);
        assertEquals(2, adapter.getPolicy(Workflow.RESIDENT).challengeCount);
    }

    @Test
    public void validSyncedConfigFromDerbyIsAppliedAndAudited() {
        String bundled = sampleConfig("1.0-default", 0.90, 2);
        DesktopLivenessConfigAdapter adapter = new DesktopLivenessConfigAdapter(
                paramService, cacheDir, bundled, auditLogger);

        // Update synced global param with a signed version 2.0 config
        String updatedConfig = sign(sampleConfig("2.0-derby-synced", 0.95, 3));
        paramService.value = updatedConfig;

        boolean refreshed = adapter.refresh(true);
        assertTrue(refreshed);

        // Verify config updated
        assertEquals("2.0-derby-synced", adapter.getConfig().configVersion);
        assertEquals(0.95, adapter.getPolicy(Workflow.RESIDENT).passiveThreshold, 0.001);
        assertEquals(3, adapter.getPolicy(Workflow.RESIDENT).challengeCount);

        // Verify audit event CONFIG_APPLIED was logged
        assertTrue(auditLogger.records.stream().anyMatch(r ->
                r.getType() == LivenessAuditRecord.Type.CONFIG_APPLIED &&
                        "2.0-derby-synced".equals(r.getDetails().get("configVersion"))
        ));
    }

    @Test
    public void tamperedOrInvalidConfigIsRejectedAndPreviousConfigStaysActive() {
        String bundled = sampleConfig("1.0-default", 0.90, 2);
        DesktopLivenessConfigAdapter adapter = new DesktopLivenessConfigAdapter(
                paramService, cacheDir, bundled, auditLogger);

        // Provide a config with an invalid / mismatched SHA-256 integrity hash
        String tampered = sampleConfig("3.0-tampered", 0.70, 1)
                .replace("\"quality\"", "\"integrity\":{\"algorithm\":\"SHA-256\",\"value\":\"badhash12345678\"},\"quality\"");
        paramService.value = tampered;

        boolean refreshed = adapter.refresh(true);
        assertFalse(refreshed);

        // Verify active config was NOT replaced by the tampered version
        assertEquals("1.0-default", adapter.getConfig().configVersion);
        assertEquals(0.90, adapter.getPolicy(Workflow.RESIDENT).passiveThreshold, 0.001);

        // Verify audit event CONFIG_REJECTED was logged
        assertTrue(auditLogger.records.stream().anyMatch(r ->
                r.getType() == LivenessAuditRecord.Type.CONFIG_REJECTED
        ));
    }

    @Test
    public void offlineFallbackPreservesLastCachedConfig() {
        String bundled = sampleConfig("1.0-default", 0.90, 2);
        DesktopLivenessConfigAdapter adapter1 = new DesktopLivenessConfigAdapter(
                paramService, cacheDir, bundled, auditLogger);

        // Sync valid config
        paramService.value = sign(sampleConfig("2.0-cached", 0.92, 2));
        assertTrue(adapter1.refresh(true));

        // Simulate client restart completely offline
        paramService.value = null; // No network, no fresh sync
        DesktopLivenessConfigAdapter adapter2 = new DesktopLivenessConfigAdapter(
                paramService, cacheDir, bundled, auditLogger);

        // Verify loaded from local offline cache
        assertEquals("2.0-cached", adapter2.getConfig().configVersion);
        assertEquals(0.92, adapter2.getPolicy(Workflow.RESIDENT).passiveThreshold, 0.001);
    }
}

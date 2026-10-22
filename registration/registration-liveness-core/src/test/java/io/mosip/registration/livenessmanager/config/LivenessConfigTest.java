package io.mosip.registration.livenessmanager.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.mosip.registration.livenessmanager.RecordingAudit;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.Workflow;

public class LivenessConfigTest {

    static String bundled() throws Exception {
        Path p = Paths.get("src/main/resources/liveness/default_liveness_config.json");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Signs a config the way the server-side tooling must (see docs/liveness/liveness-config.md). */
    static String sign(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        o.remove("integrity");
        JsonObject integrity = new JsonObject();
        integrity.addProperty("algorithm", "SHA-256");
        integrity.addProperty("value", CanonicalJson.integrityDigest(o));
        o.add("integrity", integrity);
        return o.toString();
    }

    private final LivenessConfigParser parser = new LivenessConfigParser();

    @Test
    public void bundledDefaultIsValid() throws Exception {
        LivenessConfig c = parser.parse(bundled(), false);
        assertTrue(c.policyFor(Workflow.RESIDENT).enabled);
        assertFalse(c.policyFor(Workflow.OPERATOR).enabled);
        assertEquals(2, c.policyFor(Workflow.SUPERVISOR).challengeCount);
        assertEquals(3, c.policyFor(Workflow.OPERATOR).maxRetries);
        assertNotNull(c.policyFor(Workflow.OPERATOR).quality);
    }

    @Test
    public void signedConfigVerifies() throws Exception {
        assertNotNull(parser.parse(sign(bundled()), true));
    }

    @Test
    public void tamperedConfigFailsIntegrity() throws Exception {
        String signed = sign(bundled());
        String tampered = signed.replace("\"passiveThreshold\":0.9,", "\"passiveThreshold\":0.1,");
        assertTrue(!tampered.equals(signed));
        expectError(tampered, true, "digest mismatch");
    }

    @Test
    public void syncedConfigWithoutIntegrityIsRejected() throws Exception {
        expectError(bundled(), true, "integrity: required");
    }

    @Test
    public void digestIsIndependentOfKeyOrderAndNumberFormat() {
        JsonObject a = JsonParser.parseString("{\"b\":1.50,\"a\":{\"y\":true,\"x\":\"s\"}}").getAsJsonObject();
        JsonObject b = JsonParser.parseString("{ \"a\": {\"x\":\"s\", \"y\":true}, \"b\": 1.5 }").getAsJsonObject();
        assertEquals(CanonicalJson.integrityDigest(a), CanonicalJson.integrityDigest(b));
        assertEquals("{\"a\":{\"x\":\"s\",\"y\":true},\"b\":1.5}", CanonicalJson.of(a));
    }

    @Test
    public void unknownKeysAndBadRangesAreRejected() throws Exception {
        String typo = bundled().replace("\"maxRetries\": 3,\n      \"minFaceSize\": 0.25,\n      \"maxFaces\": 1,\n      \"passiveFrames\"",
                "\"maxRetrys\": 3,\n      \"minFaceSize\": 0.25,\n      \"maxFaces\": 1,\n      \"passiveFrames\"");
        expectError(typo, false, "maxRetrys: unknown key");
        expectError(bundled().replace("\"padRejectThreshold\": 0.3", "\"padRejectThreshold\": 0.95"), false,
                "padRejectThreshold: must be <= passiveThreshold");
        expectError(bundled().replace("\"challengeCount\": 2", "\"challengeCount\": 9"), false, "challengeCount");
        expectError(bundled().replace("\"TURN_LEFT\", \"TURN_RIGHT\"]", "\"JUMP\"]"), false, "unknown JUMP");
        expectError("[]", false, "root must be a JSON object");
        expectError("{not json", false, "invalid JSON");
        expectError(bundled().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"), false, "schemaVersion");
    }

    @Test
    public void missingWorkflowPolicyIsRejected() throws Exception {
        JsonObject o = JsonParser.parseString(bundled()).getAsJsonObject();
        o.getAsJsonObject("policies").remove("supervisor");
        expectError(o.toString(), false, "policies.supervisor: required");
    }

    private void expectError(String json, boolean integrity, String fragment) {
        try {
            parser.parse(json, integrity);
            fail("expected rejection containing: " + fragment);
        } catch (LivenessConfigParser.ConfigException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    // ------------------------------------------------------------- provider

    static final class MemCache implements CachedLivenessConfigProvider.ConfigCache {
        String value;
        int saves;

        @Override
        public String load() {
            return value;
        }

        @Override
        public void save(String json) {
            value = json;
            saves++;
        }
    }

    @Test
    public void offlineRefreshIsNoOpAndKeepsCachedConfig() throws Exception {
        MemCache cache = new MemCache();
        String[] synced = {sign(bundled().replace("\"bundled-1.0.0\"", "\"v2\""))};
        RecordingAudit audit = new RecordingAudit();
        CachedLivenessConfigProvider p = new CachedLivenessConfigProvider(() -> synced[0], cache, bundled(), audit, () -> 1L);
        assertEquals("bundled-1.0.0", p.getConfig().configVersion);
        assertFalse(p.refresh(false));
        assertEquals("bundled-1.0.0", p.getConfig().configVersion);
        assertTrue(p.refresh(true));
        assertEquals("v2", p.getConfig().configVersion);
        assertEquals(1, cache.saves);
        assertTrue(audit.has(LivenessAuditRecord.Type.CONFIG_APPLIED));
        assertFalse("same value is not re-applied", p.refresh(true));

        // A new provider (app restart, offline) starts from the cache.
        CachedLivenessConfigProvider restarted = new CachedLivenessConfigProvider(() -> null, cache, bundled(), audit, () -> 1L);
        assertEquals("v2", restarted.getConfig().configVersion);
    }

    @Test
    public void badUpdateNeverReplacesGoodConfig() throws Exception {
        MemCache cache = new MemCache();
        RecordingAudit audit = new RecordingAudit();
        String good = sign(bundled().replace("\"bundled-1.0.0\"", "\"v2\""));
        String[] synced = {good};
        CachedLivenessConfigProvider p = new CachedLivenessConfigProvider(() -> synced[0], cache, bundled(), audit, () -> 1L);
        assertTrue(p.refresh(true));
        synced[0] = good.replace("\"v2\"", "\"v3\"");
        assertFalse(p.refresh(true));
        assertEquals("v2", p.getConfig().configVersion);
        assertEquals(good, cache.value);
        assertTrue(audit.has(LivenessAuditRecord.Type.CONFIG_REJECTED));
    }

    @Test
    public void corruptCacheFallsBackToBundled() throws Exception {
        MemCache cache = new MemCache();
        cache.value = "{garbage";
        RecordingAudit audit = new RecordingAudit();
        CachedLivenessConfigProvider p = new CachedLivenessConfigProvider(() -> null, cache, bundled(), audit, () -> 1L);
        assertEquals("bundled-1.0.0", p.getConfig().configVersion);
        assertTrue(audit.has(LivenessAuditRecord.Type.CONFIG_REJECTED));
    }
}

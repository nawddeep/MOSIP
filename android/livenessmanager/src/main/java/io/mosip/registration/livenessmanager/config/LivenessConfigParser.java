/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Parses and validates the liveness config (docs/liveness/liveness-config.schema.json).
 * Rejects unknown keys so a typo cannot silently fall back to a default.
 */
public final class LivenessConfigParser {

    private static final Set<String> ROOT_KEYS = set("schemaVersion", "configVersion",
            "livenessTokenTtlSeconds", "performance", "policies", "integrity");
    private static final Set<String> PERFORMANCE_KEYS = set("frameSkip", "padEveryNthFrame");
    private static final Set<String> POLICY_KEYS = set("enabled", "passiveThreshold", "padRejectThreshold",
            "challengeTypes", "challengeCount", "challengeTimeoutSeconds", "maxRetries", "minFaceSize",
            "maxFaces", "passiveFrames", "positioningStableFrames", "minPassiveMotion", "quality");
    private static final Set<String> QUALITY_KEYS = set("minBrightness", "maxBrightness", "minSharpness", "maxMotion");
    private static final Set<String> INTEGRITY_KEYS = set("algorithm", "value");

    private final Gson gson = new Gson();

    public static final class ConfigException extends Exception {
        private final List<String> errors;

        public ConfigException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * @param requireIntegrity true for configs received through sync (must carry a matching digest);
     *                         false for the bundled default shipped inside the APK
     */
    public LivenessConfig parse(String json, boolean requireIntegrity) throws ConfigException {
        List<String> errors = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) throw new ConfigException(java.util.Collections.singletonList("root must be a JSON object"));
            root = el.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ConfigException(java.util.Collections.singletonList("invalid JSON: " + e.getClass().getSimpleName()));
        }

        unknownKeys(root, ROOT_KEYS, "", errors);
        if (root.has("performance") && root.get("performance").isJsonObject()) {
            unknownKeys(root.getAsJsonObject("performance"), PERFORMANCE_KEYS, "performance.", errors);
        }
        if (root.has("integrity") && root.get("integrity").isJsonObject()) {
            unknownKeys(root.getAsJsonObject("integrity"), INTEGRITY_KEYS, "integrity.", errors);
        }
        if (root.has("policies") && root.get("policies").isJsonObject()) {
            JsonObject policies = root.getAsJsonObject("policies");
            for (String key : policies.keySet()) {
                if (!isWorkflowKey(key)) {
                    errors.add("policies." + key + ": unknown workflow");
                    continue;
                }
                if (policies.get(key).isJsonObject()) {
                    JsonObject p = policies.getAsJsonObject(key);
                    unknownKeys(p, POLICY_KEYS, "policies." + key + ".", errors);
                    if (p.has("quality") && p.get("quality").isJsonObject()) {
                        unknownKeys(p.getAsJsonObject("quality"), QUALITY_KEYS, "policies." + key + ".quality.", errors);
                    }
                }
            }
        }
        if (!errors.isEmpty()) throw new ConfigException(errors);

        LivenessConfig config;
        try {
            config = gson.fromJson(root, LivenessConfig.class);
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new ConfigException(java.util.Collections.singletonList("type error: " + e.getMessage()));
        }
        validate(config, errors);

        if (requireIntegrity) {
            if (config.integrity == null || config.integrity.value == null) {
                errors.add("integrity: required for synced config");
            } else if (!"SHA-256".equals(config.integrity.algorithm)) {
                errors.add("integrity.algorithm: only SHA-256 supported");
            } else if (!CanonicalJson.integrityDigest(root).equals(config.integrity.value)) {
                errors.add("integrity.value: digest mismatch");
            }
        }
        if (!errors.isEmpty()) throw new ConfigException(errors);
        return config;
    }

    private void validate(LivenessConfig c, List<String> errors) {
        if (c.schemaVersion != LivenessConfig.SUPPORTED_SCHEMA_VERSION) {
            errors.add("schemaVersion: unsupported " + c.schemaVersion);
        }
        if (c.configVersion == null || c.configVersion.isEmpty() || c.configVersion.length() > 64) {
            errors.add("configVersion: required, 1-64 chars");
        }
        range(errors, "livenessTokenTtlSeconds", c.livenessTokenTtlSeconds, 5, 300);
        if (c.performance == null) {
            c.performance = new LivenessConfig.Performance();
        }
        range(errors, "performance.frameSkip", c.performance.frameSkip, 1, 10);
        range(errors, "performance.padEveryNthFrame", c.performance.padEveryNthFrame, 1, 10);
        if (c.policies == null) {
            errors.add("policies: required");
            return;
        }
        for (Workflow w : Workflow.values()) {
            LivenessPolicy p = c.policies.get(w.getConfigKey());
            if (p == null) {
                errors.add("policies." + w.getConfigKey() + ": required");
                continue;
            }
            validatePolicy("policies." + w.getConfigKey() + ".", p, errors);
        }
    }

    private void validatePolicy(String path, LivenessPolicy p, List<String> errors) {
        range(errors, path + "passiveThreshold", p.passiveThreshold, 0, 1);
        range(errors, path + "padRejectThreshold", p.padRejectThreshold, 0, 1);
        if (p.padRejectThreshold > p.passiveThreshold) {
            errors.add(path + "padRejectThreshold: must be <= passiveThreshold");
        }
        if (p.challengeTypes == null || p.challengeTypes.isEmpty()) {
            errors.add(path + "challengeTypes: at least one required");
        } else {
            Set<String> seen = new HashSet<>();
            for (String t : p.challengeTypes) {
                try {
                    ChallengeType.valueOf(t);
                } catch (IllegalArgumentException | NullPointerException e) {
                    errors.add(path + "challengeTypes: unknown " + t);
                }
                if (!seen.add(t)) errors.add(path + "challengeTypes: duplicate " + t);
            }
            if (p.challengeCount > p.challengeTypes.size()) {
                errors.add(path + "challengeCount: exceeds number of challengeTypes");
            }
        }
        range(errors, path + "challengeCount", p.challengeCount, 1, 4);
        range(errors, path + "challengeTimeoutSeconds", p.challengeTimeoutSeconds, 3, 60);
        range(errors, path + "maxRetries", p.maxRetries, 1, 10);
        if (!(p.minFaceSize > 0 && p.minFaceSize <= 1)) errors.add(path + "minFaceSize: must be in (0,1]");
        range(errors, path + "maxFaces", p.maxFaces, 1, 1);
        range(errors, path + "passiveFrames", p.passiveFrames, 1, 30);
        range(errors, path + "positioningStableFrames", p.positioningStableFrames, 1, 30);
        range(errors, path + "minPassiveMotion", p.minPassiveMotion, 0, 0.1);
        if (p.quality == null) {
            p.quality = new LivenessPolicy.Quality();
        }
        range(errors, path + "quality.minBrightness", p.quality.minBrightness, 0, 255);
        range(errors, path + "quality.maxBrightness", p.quality.maxBrightness, 0, 255);
        if (p.quality.minBrightness >= p.quality.maxBrightness) {
            errors.add(path + "quality: minBrightness must be < maxBrightness");
        }
        if (p.quality.minSharpness < 0) errors.add(path + "quality.minSharpness: must be >= 0");
        if (!(p.quality.maxMotion > 0 && p.quality.maxMotion <= 1)) errors.add(path + "quality.maxMotion: must be in (0,1]");
    }

    private static void range(List<String> errors, String path, double v, double lo, double hi) {
        if (Double.isNaN(v) || v < lo || v > hi) errors.add(path + ": must be in [" + fmt(lo) + "," + fmt(hi) + "]");
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void unknownKeys(JsonObject o, Set<String> allowed, String prefix, List<String> errors) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (!allowed.contains(e.getKey())) errors.add(prefix + e.getKey() + ": unknown key");
        }
    }

    private static boolean isWorkflowKey(String key) {
        for (Workflow w : Workflow.values()) if (w.getConfigKey().equals(key)) return true;
        return false;
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }
}

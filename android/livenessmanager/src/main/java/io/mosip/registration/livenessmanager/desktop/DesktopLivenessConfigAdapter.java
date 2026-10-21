package io.mosip.registration.livenessmanager.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.LivenessConfigProvider;
import io.mosip.registration.livenessmanager.config.CachedLivenessConfigProvider;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Desktop adapter for Face Liveness configuration.
 * Reads {@code mosip.registration.face.liveness.config} from Derby's {@code GLOBAL_PARAM} table
 * via {@link DesktopGlobalParamService}, validates SHA-256 integrity, maintains an offline-first
 * cache, and falls back to bundled default configuration.
 */
public final class DesktopLivenessConfigAdapter implements LivenessConfigProvider {

    public static final String GLOBAL_PARAM_KEY = CachedLivenessConfigProvider.GLOBAL_PARAM_KEY;
    private final CachedLivenessConfigProvider delegate;

    public DesktopLivenessConfigAdapter(DesktopGlobalParamService globalParamService,
                                        File cacheDirectory,
                                        String bundledJson,
                                        LivenessAuditLogger auditLogger) {
        Objects.requireNonNull(globalParamService, "globalParamService cannot be null");
        Objects.requireNonNull(bundledJson, "bundledJson cannot be null");
        Objects.requireNonNull(auditLogger, "auditLogger cannot be null");

        CachedLivenessConfigProvider.SyncedConfigSource syncedSource = () -> globalParamService.getGlobalParam(GLOBAL_PARAM_KEY);
        CachedLivenessConfigProvider.ConfigCache configCache = new DesktopFileConfigCache(cacheDirectory);

        this.delegate = new CachedLivenessConfigProvider(
                syncedSource,
                configCache,
                bundledJson,
                auditLogger,
                System::currentTimeMillis
        );
    }

    public DesktopLivenessConfigAdapter(CachedLivenessConfigProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public LivenessPolicy getPolicy(Workflow workflow) {
        return delegate.getPolicy(workflow);
    }

    @Override
    public LivenessConfig getConfig() {
        return delegate.getConfig();
    }

    @Override
    public boolean refresh(boolean online) {
        return delegate.refresh(online);
    }

    /**
     * Reads bundled default config from resources or classpath.
     */
    public static String readBundledConfigFromClasspath() {
        String resourcePath = "/liveness/default_liveness_config.json";
        try (InputStream in = DesktopLivenessConfigAdapter.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                // Fallback attempt without leading slash
                try (InputStream in2 = DesktopLivenessConfigAdapter.class.getResourceAsStream("liveness/default_liveness_config.json")) {
                    if (in2 == null) {
                        return getMinimalDefaultConfigJson();
                    }
                    return readString(in2);
                }
            }
            return readString(in);
        } catch (Exception e) {
            return getMinimalDefaultConfigJson();
        }
    }

    private static String readString(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
        }
        return sb.toString();
    }

    private static String getMinimalDefaultConfigJson() {
        return "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"configVersion\": \"1.0-default\",\n" +
                "  \"livenessTokenTtlSeconds\": 60,\n" +
                "  \"performance\": {\"frameSkip\": 1, \"padEveryNthFrame\": 1},\n" +
                "  \"policies\": {\n" +
                "    \"resident\": {\n" +
                "      \"enabled\": true,\n" +
                "      \"passiveThreshold\": 0.90,\n" +
                "      \"padRejectThreshold\": 0.30,\n" +
                "      \"challengeTypes\": [\"BLINK\", \"SMILE\", \"TURN_LEFT\", \"TURN_RIGHT\"],\n" +
                "      \"challengeCount\": 2,\n" +
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
                "      \"challengeTypes\": [\"BLINK\", \"SMILE\"],\n" +
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
                "      \"challengeTypes\": [\"BLINK\", \"SMILE\"],\n" +
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

    /**
     * File cache implementation for Desktop offline persistence.
     */
    public static final class DesktopFileConfigCache implements CachedLivenessConfigProvider.ConfigCache {
        private final File cacheFile;

        public DesktopFileConfigCache(File directory) {
            if (directory == null) {
                this.cacheFile = null;
            } else {
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                this.cacheFile = new File(directory, "liveness_config_cache.json");
            }
        }

        @Override
        public String load() {
            if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
                return null;
            }
            try (FileInputStream fis = new FileInputStream(cacheFile)) {
                return readString(fis);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void save(String json) {
            if (cacheFile == null || json == null) {
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
    }
}

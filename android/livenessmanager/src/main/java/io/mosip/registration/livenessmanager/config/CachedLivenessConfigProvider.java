/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.config;

import java.util.Objects;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.LivenessConfigProvider;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.LivenessErrorCode;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Offline-first config provider. Precedence:
 * <ol>
 *   <li>last synced config that passed validation + integrity (persisted in {@link ConfigCache})</li>
 *   <li>bundled default from the APK</li>
 * </ol>
 * {@link #refresh(boolean)} reads the synced value (MOSIP global param) and applies it only when online
 * and only if it validates. A bad update never replaces a good config.
 */
public final class CachedLivenessConfigProvider implements LivenessConfigProvider {

    /** Global param key holding the config JSON. */
    public static final String GLOBAL_PARAM_KEY = "mosip.registration.face.liveness.config";

    public interface SyncedConfigSource {
        /** @return the latest synced JSON string, or null when never synced */
        String read();
    }

    public interface ConfigCache {
        String load();

        void save(String json);
    }

    public interface Clock {
        long nowMillis();
    }

    private final SyncedConfigSource synced;
    private final ConfigCache cache;
    private final LivenessAuditLogger audit;
    private final Clock clock;
    private final LivenessConfigParser parser = new LivenessConfigParser();

    private volatile LivenessConfig active;
    private volatile String activeRaw;

    public CachedLivenessConfigProvider(SyncedConfigSource synced, ConfigCache cache, String bundledJson,
                                        LivenessAuditLogger audit, Clock clock) {
        this.synced = Objects.requireNonNull(synced);
        this.cache = Objects.requireNonNull(cache);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        try {
            this.active = parser.parse(bundledJson, false);
            this.activeRaw = null;
        } catch (LivenessConfigParser.ConfigException e) {
            throw new IllegalStateException("Bundled liveness config is invalid: " + e.getMessage(), e);
        }
        String cached = safeLoad();
        if (cached != null) {
            try {
                this.active = parser.parse(cached, true);
                this.activeRaw = cached;
            } catch (LivenessConfigParser.ConfigException e) {
                reject("cache", e.getMessage());
            }
        }
    }

    @Override
    public LivenessPolicy getPolicy(Workflow workflow) {
        return active.policyFor(workflow);
    }

    @Override
    public LivenessConfig getConfig() {
        return active;
    }

    @Override
    public synchronized boolean refresh(boolean online) {
        if (!online) {
            return false;
        }
        String raw;
        try {
            raw = synced.read();
        } catch (RuntimeException e) {
            reject("sync-read", e.getClass().getSimpleName());
            return false;
        }
        if (raw == null || raw.trim().isEmpty() || raw.equals(activeRaw)) {
            return false;
        }
        try {
            LivenessConfig parsed = parser.parse(raw, true);
            active = parsed;
            activeRaw = raw;
            cache.save(raw);
            audit.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.CONFIG_APPLIED, clock.nowMillis())
                    .outcome(LivenessAuditRecord.Outcome.PASS)
                    .detail("configVersion", parsed.configVersion).build());
            return true;
        } catch (LivenessConfigParser.ConfigException e) {
            reject("sync", e.getMessage());
            return false;
        }
    }

    private String safeLoad() {
        try {
            return cache.load();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void reject(String source, String reason) {
        String r = reason == null ? "" : reason.replaceAll("[\\r\\n;=]", " ");
        audit.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.CONFIG_REJECTED, clock.nowMillis())
                .outcome(LivenessAuditRecord.Outcome.FAIL).error(LivenessErrorCode.CONFIG_REJECTED)
                .detail("source", source)
                .detail("reason", r.length() > 200 ? r.substring(0, 200) : r).build());
    }
}

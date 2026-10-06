/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root of the liveness config JSON. Parsed and validated by
 * {@link io.mosip.registration.livenessmanager.config.LivenessConfigParser}.
 */
public class LivenessConfig {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public int schemaVersion = SUPPORTED_SCHEMA_VERSION;
    public String configVersion = "bundled-default";
    /** Validity of a SUCCESS result for gating the subsequent capture/login step. */
    public int livenessTokenTtlSeconds = 60;
    public Performance performance = new Performance();
    public Map<String, LivenessPolicy> policies = new LinkedHashMap<>();
    public Integrity integrity;

    public static class Performance {
        /** Analyse every Nth frame (1 = every frame). */
        public int frameSkip = 1;
        /** Run the PAD model on every Nth analysed frame; landmarks still run every analysed frame. */
        public int padEveryNthFrame = 1;
    }

    public static class Integrity {
        public String algorithm = "SHA-256";
        /** Lower-case hex digest of the canonical JSON of this document without the "integrity" member. */
        public String value;
    }

    public LivenessPolicy policyFor(Workflow workflow) {
        LivenessPolicy p = policies == null ? null : policies.get(workflow.getConfigKey());
        return p != null ? p : new LivenessPolicy();
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * The three places a liveness check is required. Each has its own policy in the config.
 */
public enum Workflow {
    RESIDENT("resident"),
    OPERATOR("operator"),
    SUPERVISOR("supervisor");

    private final String configKey;

    Workflow(String configKey) {
        this.configKey = configKey;
    }

    /** Key of this workflow's policy inside the config JSON {@code policies} object. */
    public String getConfigKey() {
        return configKey;
    }

    public static Workflow fromValue(String value) {
        for (Workflow w : values()) {
            if (w.name().equalsIgnoreCase(value) || w.configKey.equalsIgnoreCase(value)) {
                return w;
            }
        }
        throw new IllegalArgumentException("Unknown workflow: " + value);
    }
}

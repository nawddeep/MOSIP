/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Supplies the active liveness configuration. Works fully offline: the last valid config is cached
 * locally, and a bundled default is used when nothing has been synced yet.
 */
public interface LivenessConfigProvider {

    LivenessPolicy getPolicy(Workflow workflow);

    LivenessConfig getConfig();

    /**
     * Re-reads the synced config. When {@code online} is false this is a no-op and the cached config
     * stays active. An update is applied only if it validates and its integrity digest matches.
     *
     * @return true if a new config was applied
     */
    boolean refresh(boolean online);
}

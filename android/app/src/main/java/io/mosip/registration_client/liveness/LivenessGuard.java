/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration_client.liveness;

import io.mosip.registration.livenessmanager.model.Workflow;

/** Native check that a liveness pass exists before a guarded step runs. */
public interface LivenessGuard {
    /**
     * @return true if the workflow's policy is disabled, or a fresh, unused liveness pass exists
     *         (the pass is consumed)
     */
    boolean consumePass(Workflow workflow);
}

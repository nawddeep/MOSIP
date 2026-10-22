/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.audit;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;

/** Discards records. For previews and tests only. */
public final class NoOpLivenessAuditLogger implements LivenessAuditLogger {
    @Override
    public void log(LivenessAuditRecord record) {
        // intentionally empty
    }
}

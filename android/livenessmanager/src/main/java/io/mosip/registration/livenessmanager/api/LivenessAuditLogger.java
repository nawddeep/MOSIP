/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;

/**
 * Sink for liveness audit records. This is the only place scores, thresholds and model details
 * may go. Implementations must never receive frames ({@link LivenessAuditRecord} cannot hold any).
 */
public interface LivenessAuditLogger {
    void log(LivenessAuditRecord record);
}

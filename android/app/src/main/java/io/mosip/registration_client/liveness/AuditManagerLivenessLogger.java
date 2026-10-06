/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration_client.liveness;

import android.util.Log;

import io.mosip.registration.clientmanager.constant.AuditEvent;
import io.mosip.registration.clientmanager.constant.Components;
import io.mosip.registration.clientmanager.spi.AuditManagerService;
import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;

/**
 * Writes liveness audit records into the standard registration-client audit table (uploaded with the
 * other client audits). Each record becomes one REG-LIV-00x event. No image data can reach here.
 */
public final class AuditManagerLivenessLogger implements LivenessAuditLogger {
    private static final String TAG = "FaceLivenessAudit";
    private final AuditManagerService auditManagerService;

    public AuditManagerLivenessLogger(AuditManagerService auditManagerService) {
        this.auditManagerService = auditManagerService;
    }

    @Override
    public void log(LivenessAuditRecord record) {
        if (record.getType() == LivenessAuditRecord.Type.PERFORMANCE && io.mosip.registration_client.BuildConfig.DEBUG) {
            // Timing only (no scores, no frames): lets developers read performance from logcat.
            Log.i(TAG, record.toCompactString());
        }
        try {
            auditManagerService.auditWithArguments(eventFor(record.getType()),
                    Components.REGISTRATION.getId(), Components.REGISTRATION.getName(), record.toCompactString());
        } catch (RuntimeException e) {
            // Auditing must never break the capture flow; the failure itself is logged without details.
            Log.e(TAG, "audit write failed: " + e.getClass().getSimpleName());
        }
    }

    static AuditEvent eventFor(LivenessAuditRecord.Type type) {
        switch (type) {
            case SESSION_STARTED:
            case SESSION_ENDED:
            case PERFORMANCE:
                return AuditEvent.FACE_LIVENESS_SESSION;
            case PASSIVE_DECISION:
                return AuditEvent.FACE_LIVENESS_DECISION;
            case CHALLENGE_RESULT:
                return AuditEvent.FACE_LIVENESS_CHALLENGE;
            case CONFIG_APPLIED:
            case CONFIG_REJECTED:
                return AuditEvent.FACE_LIVENESS_CONFIG;
            case STATE_TRANSITION:
            default:
                return AuditEvent.FACE_LIVENESS_STATE;
        }
    }
}

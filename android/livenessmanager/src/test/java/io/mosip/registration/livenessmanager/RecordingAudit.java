package io.mosip.registration.livenessmanager;

import java.util.ArrayList;
import java.util.List;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;

public final class RecordingAudit implements LivenessAuditLogger {
    public final List<LivenessAuditRecord> records = new ArrayList<>();

    @Override
    public synchronized void log(LivenessAuditRecord record) {
        records.add(record);
    }

    public boolean has(LivenessAuditRecord.Type type) {
        for (LivenessAuditRecord r : records) if (r.getType() == type) return true;
        return false;
    }
}

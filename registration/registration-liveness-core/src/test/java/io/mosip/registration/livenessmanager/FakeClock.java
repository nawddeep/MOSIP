package io.mosip.registration.livenessmanager;

import io.mosip.registration.livenessmanager.core.LivenessSession;

public final class FakeClock implements LivenessSession.Clock {
    public long now = 1_000_000L;

    @Override
    public long nowMillis() {
        return now;
    }

    public void advance(long ms) {
        now += ms;
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.core;

import java.util.Arrays;

/** Bounded, thread-safe inference timing collector (no per-frame data is kept beyond 512 samples). */
final class PerfStats {
    private static final int MAX_SAMPLES = 512;
    private final long[] samples = new long[MAX_SAMPLES];
    private int count;
    private int padCount;
    private long sum;
    private long max;

    static final class Snapshot {
        final int count;
        final int padCount;
        final double meanMs;
        final double p95Ms;
        final double maxMs;

        Snapshot(int count, int padCount, double meanMs, double p95Ms, double maxMs) {
            this.count = count;
            this.padCount = padCount;
            this.meanMs = meanMs;
            this.p95Ms = p95Ms;
            this.maxMs = maxMs;
        }
    }

    synchronized void add(long nanos, boolean withPad) {
        if (count < MAX_SAMPLES) samples[count] = nanos;
        count++;
        if (withPad) padCount++;
        sum += nanos;
        max = Math.max(max, nanos);
    }

    synchronized Snapshot snapshotAndReset() {
        if (count == 0) return null;
        int n = Math.min(count, MAX_SAMPLES);
        long[] sorted = Arrays.copyOf(samples, n);
        Arrays.sort(sorted);
        long p95 = sorted[Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)];
        Snapshot s = new Snapshot(count, padCount, sum / 1e6 / count, p95 / 1e6, max / 1e6);
        count = 0;
        padCount = 0;
        sum = 0;
        max = 0;
        return s;
    }
}

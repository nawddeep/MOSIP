/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * ISO/IEC 30107-3 style PAD metrics over labelled session outcomes.
 * <ul>
 *   <li>APCER(species) = attack presentations of that species wrongly accepted / attack presentations of that species</li>
 *   <li>APCER (reported) = max over species (the standard's worst-case convention)</li>
 *   <li>BPCER = bona fide presentations wrongly rejected / bona fide presentations</li>
 * </ul>
 * Inputs are either final accept/reject decisions (end-to-end, including challenges) or passive
 * scores (for threshold sweeps).
 */
public final class PadMetrics {

    public static final String BONA_FIDE = "bona_fide";

    public static final class Sample {
        final String species;
        final double score;
        final boolean accepted;

        private Sample(String species, double score, boolean accepted) {
            this.species = species;
            this.score = score;
            this.accepted = accepted;
        }
    }

    public static final class Result {
        public final Map<String, Double> apcerBySpecies;
        public final double apcer;
        public final double bpcer;
        public final int bonaFideCount;
        public final int attackCount;

        Result(Map<String, Double> apcerBySpecies, double apcer, double bpcer, int bonaFideCount, int attackCount) {
            this.apcerBySpecies = apcerBySpecies;
            this.apcer = apcer;
            this.bpcer = bpcer;
            this.bonaFideCount = bonaFideCount;
            this.attackCount = attackCount;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "APCER=%.4f BPCER=%.4f (bona fide n=%d, attacks n=%d) perSpecies=%s",
                    apcer, bpcer, bonaFideCount, attackCount, apcerBySpecies);
        }
    }

    private final List<Sample> samples = new ArrayList<>();

    /** Adds an end-to-end decision. {@code species} = {@link #BONA_FIDE} or an attack instrument name. */
    public PadMetrics addDecision(String species, boolean accepted) {
        samples.add(new Sample(species, Double.NaN, accepted));
        return this;
    }

    /** Adds a live score; acceptance is decided at evaluation time against a threshold. */
    public PadMetrics addScore(String species, double liveScore) {
        samples.add(new Sample(species, liveScore, false));
        return this;
    }

    /** Metrics over recorded decisions. */
    public Result evaluateDecisions() {
        return compute(Double.NaN);
    }

    /** Metrics over scores, accepting when score >= threshold. */
    public Result evaluateAt(double threshold) {
        return compute(threshold);
    }

    /** Sweeps candidate thresholds (every distinct score) and returns the one minimising max(APCER, BPCER). */
    public double equalErrorThreshold() {
        TreeSet<Double> candidates = new TreeSet<>();
        for (Sample s : samples) if (!Double.isNaN(s.score)) candidates.add(s.score);
        double best = 0.5, bestCost = Double.MAX_VALUE;
        for (double t : candidates) {
            Result r = compute(t);
            double cost = Math.max(r.apcer, r.bpcer);
            if (cost < bestCost) {
                bestCost = cost;
                best = t;
            }
        }
        return best;
    }

    /** Highest threshold whose BPCER stays at most {@code maxBpcer} (a higher threshold accepts fewer attacks). */
    public double thresholdForBpcer(double maxBpcer) {
        TreeSet<Double> candidates = new TreeSet<>();
        for (Sample s : samples) if (!Double.isNaN(s.score)) candidates.add(s.score);
        double chosen = 0;
        for (double t : candidates) {
            if (compute(t).bpcer <= maxBpcer) chosen = t;
            else break;
        }
        return chosen;
    }

    private Result compute(double threshold) {
        Map<String, int[]> perSpecies = new LinkedHashMap<>();
        int bf = 0, bfRejected = 0, attacks = 0;
        for (Sample s : samples) {
            boolean accepted = Double.isNaN(threshold) ? s.accepted : s.score >= threshold;
            if (BONA_FIDE.equals(s.species)) {
                bf++;
                if (!accepted) bfRejected++;
            } else {
                attacks++;
                int[] c = perSpecies.computeIfAbsent(s.species, k -> new int[2]);
                c[0]++;
                if (accepted) c[1]++;
            }
        }
        Map<String, Double> apcerBySpecies = new LinkedHashMap<>();
        double maxApcer = 0;
        for (Map.Entry<String, int[]> e : perSpecies.entrySet()) {
            double a = (double) e.getValue()[1] / e.getValue()[0];
            apcerBySpecies.put(e.getKey(), a);
            maxApcer = Math.max(maxApcer, a);
        }
        return new Result(apcerBySpecies, maxApcer, bf == 0 ? 0 : (double) bfRejected / bf, bf, attacks);
    }
}

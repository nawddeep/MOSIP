package org.minifasnet;

import java.util.List;

public final class PadResult {
    /** Mean class-1 (real) probability over the models, 0..1. Threshold on this. */
    public final double realScore;
    /** "real" or "fake": argmax of the summed softmax, as upstream test.py. */
    public final String label;
    /** Upstream's printed score: summed[label] / nModels (confidence of the winning class). */
    public final double labelConfidence;
    public final List<ModelScore> perModelScores;

    PadResult(double realScore, String label, double labelConfidence, List<ModelScore> perModelScores) {
        this.realScore = realScore; this.label = label;
        this.labelConfidence = labelConfidence; this.perModelScores = perModelScores;
    }

    @Override public String toString() {
        return String.format("PadResult{realScore=%.6f, label=%s, labelConfidence=%.6f}", realScore, label, labelConfidence);
    }
}

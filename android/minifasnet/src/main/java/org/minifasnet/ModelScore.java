package org.minifasnet;

/** Per-model output: softmax over the 3 classes, plus the class-1 (real) probability. */
public final class ModelScore {
    public final String name;
    public final double scale;
    public final double realProb;
    private final double[] probs;

    ModelScore(String name, double scale, double[] probs, double realProb) {
        this.name = name; this.scale = scale; this.probs = probs.clone(); this.realProb = realProb;
    }

    /** Softmax over the 3 classes (a copy; index 1 = real). */
    public double[] getProbs() { return probs.clone(); }
}

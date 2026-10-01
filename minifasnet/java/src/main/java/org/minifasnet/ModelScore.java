package org.minifasnet;

/** Per-model output: softmax over the 3 classes, plus the class-1 (real) probability. */
public final class ModelScore {
    public final String name;
    public final double scale;
    public final double[] probs;
    public final double realProb;

    ModelScore(String name, double scale, double[] probs, double realProb) {
        this.name = name; this.scale = scale; this.probs = probs; this.realProb = realProb;
    }
}

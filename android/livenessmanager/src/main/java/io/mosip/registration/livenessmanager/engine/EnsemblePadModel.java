/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.engine;

import org.minifasnet.PadException;

import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.PadModel;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * {@link PadModel} backed by the vendored MiniFASNet ensemble ({@code org.minifasnet}, module {@code :minifasnet}):
 * MiniFASNetV2 (crop 2.7) + MiniFASNetV1SE (crop 4.0), live score = mean class-1 softmax probability.
 * <p>
 * This class only adapts data: RGBA frame to BGR bytes, landmark box to {x, y, w, h}, library error codes to engine
 * errors. All preprocessing (crop, canonical bilinear resize, tensor layout) stays inside the parity-tested library.
 */
public final class EnsemblePadModel implements PadModel {

    /** Scores one BGR frame + face box. Production: {@code MiniFasNetPad.predict(...).realScore}. */
    public interface Scorer extends AutoCloseable {
        double realScore(byte[] bgr, int width, int height, double[] box);

        @Override
        void close();
    }

    public interface ScorerFactory {
        /** Loads the models; throws (fail closed) when they are missing, tampered or unreadable. */
        Scorer open() throws LivenessEngine.EngineException;
    }

    private final ScorerFactory factory;
    private final String description;
    private Scorer scorer;
    private byte[] bgr;

    public EnsemblePadModel(ScorerFactory factory, String description) {
        this.factory = factory;
        this.description = description;
    }

    @Override
    public void initialize() throws LivenessEngine.EngineException {
        if (scorer == null) {
            scorer = factory.open();
        }
    }

    @Override
    public float liveScore(FaceFrame frame, FaceObservation face) throws LivenessEngine.EngineException {
        if (scorer == null) {
            throw new LivenessEngine.EngineException("PAD model not initialized", null);
        }
        bgr = FrameConversions.rgbaToBgr(frame.getRgba(), frame.getWidth() * frame.getHeight(), bgr);
        double[] box = {face.getLeft(), face.getTop(), face.getWidth(), face.getHeight()};
        try {
            return (float) scorer.realScore(bgr, frame.getWidth(), frame.getHeight(), box);
        } catch (PadException e) {
            switch (e.getCode()) {
                case INVALID_FRAME:
                case INVALID_BOX:
                case FACE_TOO_SMALL:
                    // This frame cannot be scored (face at the edge, too small): skip it, it is not an attack signal.
                    return PadModel.NOT_SCORABLE;
                case MODEL_LOAD:
                    throw new LivenessEngine.EngineException("PAD model unavailable", e, true);
                case INFERENCE:
                default:
                    throw new LivenessEngine.EngineException("PAD inference failed", e);
            }
        }
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public void close() {
        if (scorer != null) {
            scorer.close();
            scorer = null;
        }
        bgr = null;
    }
}

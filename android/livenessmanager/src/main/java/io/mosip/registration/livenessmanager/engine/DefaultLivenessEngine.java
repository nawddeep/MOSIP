/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.engine;

import java.util.List;

import io.mosip.registration.livenessmanager.api.FaceDetector;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.PadModel;
import io.mosip.registration.livenessmanager.api.QualityAnalyzer;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.QualityResult;

/**
 * Composes a face detector, a PAD model and a quality analyzer. Any of the three can be replaced
 * (another model, a vendor SDK) without touching the session or the UI.
 */
public final class DefaultLivenessEngine implements LivenessEngine {
    private final FaceDetector detector;
    private final PadModel padModel;
    private final QualityAnalyzer qualityAnalyzer;
    private FaceObservation previousFace;
    private boolean initialized;

    public DefaultLivenessEngine(FaceDetector detector, PadModel padModel, QualityAnalyzer qualityAnalyzer) {
        this.detector = detector;
        this.padModel = padModel;
        this.qualityAnalyzer = qualityAnalyzer;
    }

    @Override
    public void initialize() throws EngineException {
        if (initialized) return;
        detector.initialize();
        padModel.initialize();
        initialized = true;
    }

    @Override
    public FrameAnalysis analyze(FaceFrame frame, LivenessPolicy policy, boolean runPad) throws EngineException {
        if (!initialized) {
            throw new EngineException("Engine not initialized", null);
        }
        try {
            List<FaceObservation> faces = detector.detect(frame);
            int count = faces.size();
            if (count != 1) {
                previousFace = null;
                return new FrameAnalysis(frame.getFrameId(), frame.getTimestampNanos(), frame.isMirrored(),
                        count, null, null, null);
            }
            FaceObservation face = faces.get(0);
            QualityResult quality = qualityAnalyzer.analyze(frame, face, previousFace, policy);
            previousFace = face;
            Float score = null;
            // PAD needs a usable crop; a face that is too small gives meaningless scores.
            if (runPad && quality.getHint() != QualityHint.FAR) {
                float s = padModel.liveScore(frame, face);
                if (Float.isNaN(s)) {
                    score = null; // PadModel.NOT_SCORABLE: this frame simply is not counted
                } else if (s < 0f || s > 1f) {
                    throw new EngineException("PAD model returned out-of-range score", null);
                } else {
                    score = s;
                }
            }
            return new FrameAnalysis(frame.getFrameId(), frame.getTimestampNanos(), frame.isMirrored(),
                    1, face, quality, score);
        } catch (EngineException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EngineException("Inference failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public String describe() {
        return "detector=" + detector.describe() + ",pad=" + padModel.describe()
                + ",quality=" + qualityAnalyzer.getClass().getSimpleName();
    }

    @Override
    public void close() {
        initialized = false;
        previousFace = null;
        try {
            detector.close();
        } finally {
            padModel.close();
        }
    }
}

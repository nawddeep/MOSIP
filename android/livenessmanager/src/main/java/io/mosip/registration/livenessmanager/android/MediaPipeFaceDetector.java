/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import io.mosip.registration.livenessmanager.api.FaceDetector;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * MediaPipe Face Landmarker (VIDEO mode, CPU). Detects up to 2 faces so "multiple faces" can be
 * reported; returns 478 landmarks per face in pixel coordinates.
 */
public final class MediaPipeFaceDetector implements FaceDetector {
    /** Detect one more than allowed, so extra people are seen. */
    static final int NUM_FACES = 2;

    private final Context context;
    private final ModelManifest.Entry model;
    private FaceLandmarker landmarker;
    private long lastTimestampMs = -1;

    public MediaPipeFaceDetector(Context context, ModelManifest.Entry model) {
        this.context = context.getApplicationContext();
        this.model = model;
    }

    @Override
    public void initialize() throws LivenessEngine.EngineException {
        if (landmarker != null) return;
        byte[] bytes = ModelManifest.loadVerified(context.getAssets(), model);
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        buf.put(bytes).rewind();
        try {
            FaceLandmarker.FaceLandmarkerOptions options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buf).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumFaces(NUM_FACES)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setOutputFaceBlendshapes(false)
                    .setOutputFacialTransformationMatrixes(false)
                    .build();
            landmarker = FaceLandmarker.createFromOptions(context, options);
        } catch (RuntimeException e) {
            throw new LivenessEngine.EngineException("Face landmarker init failed", e);
        }
    }

    @Override
    public List<FaceObservation> detect(FaceFrame frame) throws LivenessEngine.EngineException {
        if (landmarker == null) throw new LivenessEngine.EngineException("Detector not initialized", null);
        if (!(frame.getPlatformImage() instanceof Bitmap)) {
            throw new LivenessEngine.EngineException("Frame has no bitmap", null);
        }
        MPImage image = new BitmapImageBuilder((Bitmap) frame.getPlatformImage()).build();
        long ts = frame.getTimestampNanos() / 1_000_000L;
        if (ts <= lastTimestampMs) ts = lastTimestampMs + 1; // VIDEO mode needs strictly increasing time
        lastTimestampMs = ts;
        FaceLandmarkerResult result = landmarker.detectForVideo(image, ts);
        List<FaceObservation> faces = new ArrayList<>();
        int w = frame.getWidth(), h = frame.getHeight();
        for (List<NormalizedLandmark> lms : result.faceLandmarks()) {
            float[] xs = new float[lms.size()];
            float[] ys = new float[lms.size()];
            for (int i = 0; i < lms.size(); i++) {
                xs[i] = lms.get(i).x() * w;
                ys[i] = lms.get(i).y() * h;
            }
            faces.add(FaceObservation.fromLandmarks(xs, ys));
        }
        return faces;
    }

    @Override
    public String describe() {
        return "mediapipe-face-landmarker:" + model.version;
    }

    @Override
    public void close() {
        if (landmarker != null) {
            landmarker.close();
            landmarker = null;
        }
        lastTimestampMs = -1;
    }
}

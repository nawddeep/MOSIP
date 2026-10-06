/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.content.Context;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.PadModel;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * MiniFASNetV2 (Silent-Face-Anti-Spoofing, Apache-2.0) on ONNX Runtime.
 * Preprocessing follows the upstream reference: expand the face box by {@code cropScale} (clamped to
 * the image), resize to 80x80, BGR, CHW float, multiply by {@code inputScale}. Output logits are
 * soft-maxed; the live score is p[realClassIndex].
 * Buffers (Mats, the input FloatBuffer, the HWC float array) are allocated once and reused.
 */
public final class OnnxMiniFasNetPadModel implements PadModel {
    private final Context context;
    private final ModelManifest.PadEntry model;
    private final OpenCvFrameCache frames;
    private final boolean useNnapi;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private FloatBuffer input;
    private float[] hwc;
    private final Mat resized = new Mat();
    private final Mat bgr = new Mat();
    private final Mat asFloat = new Mat();

    OnnxMiniFasNetPadModel(Context context, ModelManifest.PadEntry model, OpenCvFrameCache frames, boolean useNnapi) {
        this.context = context.getApplicationContext();
        this.model = model;
        this.frames = frames;
        this.useNnapi = useNnapi;
    }

    @Override
    public void initialize() throws LivenessEngine.EngineException {
        if (session != null) return;
        byte[] bytes = ModelManifest.loadVerified(context.getAssets(), model);
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(2);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            if (useNnapi) {
                try {
                    options.addNnapi();
                } catch (OrtException e) {
                    // NNAPI not available: CPU is fine
                }
            }
            session = env.createSession(bytes, options);
            inputName = session.getInputNames().iterator().next();
        } catch (OrtException e) {
            throw new LivenessEngine.EngineException("PAD model load failed", e);
        }
        int n = model.inputSize;
        input = ByteBuffer.allocateDirect(3 * n * n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        hwc = new float[3 * n * n];
    }

    @Override
    public float liveScore(FaceFrame frame, FaceObservation face) throws LivenessEngine.EngineException {
        if (session == null) throw new LivenessEngine.EngineException("PAD model not initialized", null);
        Mat rgba = frames.get(frame);
        Rect crop = expandedBox(face, frame.getWidth(), frame.getHeight(), model.cropScale);
        Mat roi = rgba.submat(crop);
        int n = model.inputSize;
        Imgproc.resize(roi, resized, new Size(n, n), 0, 0, Imgproc.INTER_LINEAR);
        roi.release();
        Imgproc.cvtColor(resized, bgr, "RGB".equals(model.channelOrder) ? Imgproc.COLOR_RGBA2RGB : Imgproc.COLOR_RGBA2BGR);
        bgr.convertTo(asFloat, CvType.CV_32FC3, model.inputScale);
        asFloat.get(0, 0, hwc);

        input.rewind();
        int plane = n * n;
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < plane; i++) {
                input.put(c * plane + i, hwc[i * 3 + c]);
            }
        }
        input.rewind();

        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input, new long[]{1, 3, n, n});
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            float[][] logits = (float[][]) result.get(0).getValue();
            return softmax(logits[0])[model.realClassIndex];
        } catch (OrtException | ClassCastException | ArrayIndexOutOfBoundsException e) {
            throw new LivenessEngine.EngineException("PAD inference failed", e);
        }
    }

    /** Upstream CropImage._get_new_box: scale the box around its centre, clamp the scale, shift inside. */
    static Rect expandedBox(FaceObservation f, int srcW, int srcH, double scale) {
        double boxW = Math.max(1, f.getWidth()), boxH = Math.max(1, f.getHeight());
        scale = Math.min((srcH - 1) / boxH, Math.min((srcW - 1) / boxW, scale));
        double newW = boxW * scale, newH = boxH * scale;
        double cx = f.getLeft() + boxW / 2, cy = f.getTop() + boxH / 2;
        double x0 = cx - newW / 2, y0 = cy - newH / 2, x1 = cx + newW / 2, y1 = cy + newH / 2;
        if (x0 < 0) { x1 -= x0; x0 = 0; }
        if (y0 < 0) { y1 -= y0; y0 = 0; }
        if (x1 > srcW - 1) { x0 -= x1 - srcW + 1; x1 = srcW - 1; }
        if (y1 > srcH - 1) { y0 -= y1 - srcH + 1; y1 = srcH - 1; }
        int ix = (int) Math.max(0, x0), iy = (int) Math.max(0, y0);
        int iw = (int) Math.max(1, Math.min(srcW - ix, x1 - x0 + 1));
        int ih = (int) Math.max(1, Math.min(srcH - iy, y1 - y0 + 1));
        return new Rect(ix, iy, iw, ih);
    }

    static float[] softmax(float[] logits) {
        float max = -Float.MAX_VALUE;
        for (float v : logits) max = Math.max(max, v);
        double sum = 0;
        float[] out = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            out[i] = (float) Math.exp(logits[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) out[i] /= (float) sum;
        return out;
    }

    @Override
    public String describe() {
        return "minifasnet:" + model.version + (useNnapi ? "+nnapi" : "");
    }

    @Override
    public void close() {
        try {
            if (session != null) session.close();
        } catch (OrtException ignored) {
            // closing
        }
        session = null;
        resized.release();
        bgr.release();
        asFloat.release();
        frames.release();
    }
}

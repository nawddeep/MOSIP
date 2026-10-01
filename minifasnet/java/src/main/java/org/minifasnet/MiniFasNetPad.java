package org.minifasnet;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passive-liveness wrapper around the MiniFASNet ONNX models. Same API shape as the Python reference
 * (python/minifasnet_pad.py). Does NOT detect faces: the caller supplies the face box.
 */
public final class MiniFasNetPad implements AutoCloseable {
    private static final int REAL_CLASS_INDEX = 1;   // upstream test.py: label == 1 is a real face
    // '2.7_80x80_MiniFASNetV2.onnx' -> scale 2.7 ; '4_0_0_80x80_...' -> scale 4 (first '_' token, as upstream)
    private static final Pattern NAME = Pattern.compile("^([0-9.]+)_(?:\\d+_)*?(\\d+)x(\\d+)_.+\\.onnx$");

    private static final class Model {
        String name; double scale; int w, h; OrtSession session;
    }

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final List<Model> models = new ArrayList<>();
    private final int minFaceSize;

    /** @param modelDir directory containing the *.onnx files (configurable, works fully offline). */
    public MiniFasNetPad(String modelDir, int minFaceSize) {
        this.minFaceSize = minFaceSize;
        File dir = new File(modelDir);
        File[] files = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".onnx")) : null;
        if (files == null || files.length == 0)
            throw new PadException(PadException.Code.MODEL_LOAD, "no .onnx models in: " + modelDir);
        Arrays.sort(files);
        for (File f : files) {
            Matcher m = NAME.matcher(f.getName());
            if (!m.matches())
                throw new PadException(PadException.Code.MODEL_LOAD, "cannot parse model file name: " + f.getName());
            Model md = new Model();
            md.name = f.getName();
            md.scale = Double.parseDouble(m.group(1));
            md.h = Integer.parseInt(m.group(2));
            md.w = Integer.parseInt(m.group(3));
            try {
                md.session = env.createSession(f.getPath(), new OrtSession.SessionOptions());
            } catch (OrtException e) {
                throw new PadException(PadException.Code.MODEL_LOAD, "failed to load " + f.getName() + ": " + e.getMessage(), e);
            }
            models.add(md);
        }
    }

    /**
     * @param bgr    frame bytes, row-major HxWx3, BGR order (OpenCV order), length == width*height*3
     * @param box    {x, y, w, h} of the face, in frame pixels, supplied by the caller
     */
    public PadResult predict(byte[] bgr, int width, int height, double[] box) {
        validate(bgr, width, height, box);
        double[] total = new double[3];
        List<ModelScore> per = new ArrayList<>();
        for (Model md : models) {
            int[] r = ImageOps.getNewBox(width, height, box[0], box[1], box[2], box[3], md.scale);
            if (r[0] < 0 || r[1] < 0 || r[2] < r[0] || r[3] < r[1] || r[2] >= width || r[3] >= height)
                throw new PadException(PadException.Code.INVALID_BOX, "crop window is empty/out of frame for box " + Arrays.toString(box));
            byte[] patch = ImageOps.cropResize(bgr, width, r[0], r[1], r[2], r[3], md.w, md.h);
            float[] tensor = ImageOps.toTensor(patch, md.w, md.h);
            double[] probs;
            try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), new long[] {1, 3, md.h, md.w});
                 OrtSession.Result out = md.session.run(Collections.singletonMap("input", in))) {
                float[] logits = ((float[][]) out.get(0).getValue())[0];
                probs = softmax(logits);
            } catch (OrtException e) {
                throw new PadException(PadException.Code.INFERENCE, "inference failed for " + md.name + ": " + e.getMessage(), e);
            }
            for (int i = 0; i < 3; i++) total[i] += probs[i];
            per.add(new ModelScore(md.name, md.scale, probs, probs[REAL_CLASS_INDEX]));
        }
        int n = models.size();
        int idx = 0;
        for (int i = 1; i < 3; i++) if (total[i] > total[idx]) idx = i;
        return new PadResult(total[REAL_CLASS_INDEX] / n, idx == REAL_CLASS_INDEX ? "real" : "fake", total[idx] / n,
                Collections.unmodifiableList(per));
    }

    private void validate(byte[] bgr, int width, int height, double[] box) {
        if (bgr == null || width < 2 || height < 2 || bgr.length == 0)
            throw new PadException(PadException.Code.INVALID_FRAME, "frame is null/empty/too small");
        if ((long) width * height * 3 != bgr.length)
            throw new PadException(PadException.Code.INVALID_FRAME,
                    "frame length " + bgr.length + " != width*height*3 = " + ((long) width * height * 3));
        if (box == null || box.length != 4)
            throw new PadException(PadException.Code.INVALID_BOX, "box must be {x, y, w, h}");
        for (double v : box)
            if (Double.isNaN(v) || Double.isInfinite(v))
                throw new PadException(PadException.Code.INVALID_BOX, "box contains NaN/inf");
        if (box[2] <= 0 || box[3] <= 0)
            throw new PadException(PadException.Code.INVALID_BOX, "box has non-positive size: " + Arrays.toString(box));
        double cx = box[0] + box[2] / 2, cy = box[1] + box[3] / 2;
        if (cx < 0 || cx >= width || cy < 0 || cy >= height)
            throw new PadException(PadException.Code.INVALID_BOX, "box center is outside the " + width + "x" + height + " frame");
        if (Math.min(box[2], box[3]) < minFaceSize)
            throw new PadException(PadException.Code.FACE_TOO_SMALL, "face " + box[2] + "x" + box[3] + " < minFaceSize " + minFaceSize);
    }

    private static double[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float l : logits) max = Math.max(max, l);
        double sum = 0;
        double[] e = new double[logits.length];
        for (int i = 0; i < logits.length; i++) { e[i] = Math.exp(logits[i] - max); sum += e[i]; }
        for (int i = 0; i < e.length; i++) e[i] /= sum;
        return e;
    }

    @Override public void close() {
        for (Model m : models) {
            try { m.session.close(); } catch (OrtException ignored) { /* nothing useful to do on close */ }
        }
    }
}

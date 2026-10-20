package org.minifasnet;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passive-liveness wrapper around the MiniFASNet ONNX models. Same API shape as the Python reference
 * (python/minifasnet_pad.py). Does NOT detect faces: the caller supplies the face box.
 * Thread-safe for concurrent predict() calls (ONNX Runtime sessions are; no other shared mutable state).
 */
public final class MiniFasNetPad implements AutoCloseable {
    private static final int REAL_CLASS_INDEX = 1;   // upstream test.py: label == 1 is a real face
    private static final int NUM_CLASSES = 3;
    private static final String INPUT_NAME = "input";   // names fixed by python/export_onnx.py
    private static final String OUTPUT_NAME = "logits";
    // '2.7_80x80_MiniFASNetV2.onnx' -> scale 2.7 ; '4_0_0_80x80_...' -> scale 4 (first '_' token, as upstream)
    private static final Pattern NAME = Pattern.compile("^([0-9.]+)_(?:\\d+_)*?(\\d+)x(\\d+)_.+\\.onnx$");

    private static final class Model {
        final String name; final double scale; final int w, h; final OrtSession session;

        Model(String name, double scale, int w, int h, OrtSession session) {
            this.name = name; this.scale = scale; this.w = w; this.h = h; this.session = session;
        }
    }

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final List<Model> models = new ArrayList<>();
    private final int minFaceSize;
    private volatile boolean closed;

    /** Same as {@code MiniFasNetPad(modelDir, minFaceSize, 0)} (ONNX Runtime default thread count). */
    public MiniFasNetPad(String modelDir, int minFaceSize) {
        this(modelDir, minFaceSize, 0);
    }

    /**
     * @param modelDir       directory containing the *.onnx files (configurable, works fully offline)
     * @param minFaceSize    min(box w, box h) in pixels; smaller boxes are rejected (FACE_TOO_SMALL)
     * @param intraOpThreads ONNX Runtime threads per model; 0 = ORT default, 1-2 is sensible on low-end devices
     */
    public MiniFasNetPad(String modelDir, int minFaceSize, int intraOpThreads) {
        if (minFaceSize < 0) throw new IllegalArgumentException("minFaceSize must be >= 0: " + minFaceSize);
        if (intraOpThreads < 0) throw new IllegalArgumentException("intraOpThreads must be >= 0: " + intraOpThreads);
        this.minFaceSize = minFaceSize;
        File dir = new File(modelDir == null ? "" : modelDir);
        File[] files = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".onnx")) : null;
        if (files == null || files.length == 0)
            throw new PadException(PadException.Code.MODEL_LOAD, "no .onnx models in: " + modelDir);
        Arrays.sort(files);
        try {
            for (File f : files) models.add(load(f, intraOpThreads));
        } catch (RuntimeException e) {
            close(); // do not leak the sessions that did load
            throw e;
        }
    }

    private Model load(File f, int intraOpThreads) {
        Matcher m = NAME.matcher(f.getName());
        if (!m.matches())
            throw new PadException(PadException.Code.MODEL_LOAD,
                    "cannot parse model file name (expected '<scale>_<H>x<W>_<name>.onnx'): " + f.getName());
        double scale;
        int h, w;
        try {
            scale = Double.parseDouble(m.group(1));
            h = Integer.parseInt(m.group(2));
            w = Integer.parseInt(m.group(3));
        } catch (NumberFormatException e) {
            throw new PadException(PadException.Code.MODEL_LOAD, "invalid scale/size in model file name: " + f.getName(), e);
        }
        if (!(scale > 0) || h <= 0 || w <= 0)
            throw new PadException(PadException.Code.MODEL_LOAD, "scale and input size must be positive: " + f.getName());
        OrtSession session;
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            if (intraOpThreads > 0) opts.setIntraOpNumThreads(intraOpThreads);
            session = env.createSession(f.getPath(), opts);   // options are copied into the session
        } catch (OrtException e) {
            throw new PadException(PadException.Code.MODEL_LOAD, "failed to load " + f.getName() + ": " + e.getMessage(), e);
        }
        try {
            checkIo(f.getName(), session, h, w);
        } catch (RuntimeException e) {
            closeQuietly(session);
            throw e;
        }
        return new Model(f.getName(), scale, w, h, session);
    }

    /** Reject files whose graph does not match the name (wrong/renamed/corrupted model). */
    private static void checkIo(String name, OrtSession s, int h, int w) {
        try {
            Map<String, NodeInfo> in = s.getInputInfo(), out = s.getOutputInfo();
            long[] inShape = in.size() == 1 && in.containsKey(INPUT_NAME) && in.get(INPUT_NAME).getInfo() instanceof TensorInfo
                    ? ((TensorInfo) in.get(INPUT_NAME).getInfo()).getShape() : null;
            long[] outShape = out.size() == 1 && out.containsKey(OUTPUT_NAME) && out.get(OUTPUT_NAME).getInfo() instanceof TensorInfo
                    ? ((TensorInfo) out.get(OUTPUT_NAME).getInfo()).getShape() : null;
            if (inShape == null || inShape.length != 4 || inShape[1] != 3 || inShape[2] != h || inShape[3] != w)
                throw new PadException(PadException.Code.MODEL_LOAD,
                        name + ": expected one input '" + INPUT_NAME + "' [N,3," + h + "," + w + "], got " + in.keySet());
            if (outShape == null || outShape.length != 2 || outShape[1] != NUM_CLASSES)
                throw new PadException(PadException.Code.MODEL_LOAD,
                        name + ": expected one output '" + OUTPUT_NAME + "' [N," + NUM_CLASSES + "], got " + out.keySet());
        } catch (OrtException e) {
            throw new PadException(PadException.Code.MODEL_LOAD, name + ": cannot read model I/O: " + e.getMessage(), e);
        }
    }

    /**
     * @param bgr    frame bytes, row-major HxWx3, BGR order (OpenCV order), length == width*height*3
     * @param box    {x, y, w, h} of the face, in frame pixels, supplied by the caller
     */
    public PadResult predict(byte[] bgr, int width, int height, double[] box) {
        if (closed) throw new IllegalStateException("MiniFasNetPad is closed");
        validate(bgr, width, height, box);
        double[] total = new double[NUM_CLASSES];
        List<ModelScore> per = new ArrayList<>();
        for (Model md : models) {
            int[] r = ImageOps.getNewBox(width, height, box[0], box[1], box[2], box[3], md.scale);
            if (r[0] < 0 || r[1] < 0 || r[2] < r[0] || r[3] < r[1] || r[2] >= width || r[3] >= height)
                throw new PadException(PadException.Code.INVALID_BOX, "crop window is empty/out of frame for box " + Arrays.toString(box));
            byte[] patch = ImageOps.cropResize(bgr, width, r[0], r[1], r[2], r[3], md.w, md.h);
            float[] tensor = ImageOps.toTensor(patch, md.w, md.h);
            double[] probs;
            try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), new long[] {1, 3, md.h, md.w});
                 OrtSession.Result out = md.session.run(Collections.singletonMap(INPUT_NAME, in))) {
                Object value = out.get(0).getValue();
                if (!(value instanceof float[][]) || ((float[][]) value).length != 1 || ((float[][]) value)[0].length != NUM_CLASSES)
                    throw new PadException(PadException.Code.INFERENCE, md.name + ": unexpected output");
                probs = softmax(((float[][]) value)[0]);
            } catch (OrtException e) {
                throw new PadException(PadException.Code.INFERENCE, "inference failed for " + md.name + ": " + e.getMessage(), e);
            }
            for (double p : probs)
                if (!Double.isFinite(p)) throw new PadException(PadException.Code.INFERENCE, md.name + ": non-finite output");
            for (int i = 0; i < NUM_CLASSES; i++) total[i] += probs[i];
            per.add(new ModelScore(md.name, md.scale, probs, probs[REAL_CLASS_INDEX]));
        }
        int n = models.size();
        int idx = 0;
        for (int i = 1; i < NUM_CLASSES; i++) if (total[i] > total[idx]) idx = i;
        return new PadResult(total[REAL_CLASS_INDEX] / n, idx == REAL_CLASS_INDEX ? "real" : "fake", total[idx] / n,
                Collections.unmodifiableList(per));
    }

    private void validate(byte[] bgr, int width, int height, double[] box) {
        if (bgr == null || bgr.length == 0)
            throw new PadException(PadException.Code.INVALID_FRAME, "frame is null/empty");
        if (width < 2 || height < 2)
            throw new PadException(PadException.Code.INVALID_FRAME, "frame too small: " + width + "x" + height);
        if ((long) width * height * 3 != bgr.length)
            throw new PadException(PadException.Code.INVALID_FRAME,
                    "frame length " + bgr.length + " != width*height*3 = " + ((long) width * height * 3));
        if (box == null || box.length != 4)
            throw new PadException(PadException.Code.INVALID_BOX, "box must be {x, y, w, h}");
        for (double v : box)
            if (!Double.isFinite(v))
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

    private static void closeQuietly(OrtSession s) {
        try { s.close(); } catch (OrtException ignored) { /* nothing useful to do on close */ }
    }

    @Override public void close() {
        closed = true;
        for (Model m : models) closeQuietly(m.session);
        models.clear();
    }
}

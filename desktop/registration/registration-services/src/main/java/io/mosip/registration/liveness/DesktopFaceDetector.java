package io.mosip.registration.liveness;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import io.mosip.registration.livenessmanager.api.FaceDetector;
import io.mosip.registration.livenessmanager.api.LivenessEngine.EngineException;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * Face box + 468-point landmarks on ONNX Runtime (CPU) with MediaPipe models: BlazeFace (short range) finds the
 * face and the eye keypoints, a rotated 256x256 crop goes through Face Mesh, and the points are mapped back to
 * pixel coordinates of the frame. The first 468 points follow the Face Mesh topology that
 * {@code FaceGeometry} expects.
 *
 * <p>
 * Only the primary (highest score) face gets landmarks; further faces are reported as box-only observations
 * so the engine's "more than one face" guidance still works without running Face Mesh for each of them.
 *
 * <p>
 * Models come from {@link DesktopModelStore}, i.e. only after their SHA-256 matched the manifest. Frames are used
 * in memory and never stored.
 */
public final class DesktopFaceDetector implements FaceDetector {

	static final int DETECTOR_SIZE = 128;
	static final int LANDMARK_SIZE = 256;
	static final float MIN_SCORE = 0.5f;
	static final float NMS_IOU = 0.3f;
	static final float ROI_SCALE = 1.5f;
	static final int MAX_FACES = 4;
	static final int MIN_LANDMARKS = 468;
	/** Detector input is normalised to [-1, 1]; letterbox padding is a black pixel. */
	private static final float PAD_VALUE = -1f;
	private static final int ANCHORS = 896;

	private final DesktopModelStore store;

	private OrtEnvironment env;
	private OrtSession detector;
	private OrtSession landmarks;
	private String detectorInput;
	private String landmarkInput;
	private String landmarkOutput;
	private String presenceOutput;
	private float[] anchorX;
	private float[] anchorY;
	private String description = "uninitialized";

	public DesktopFaceDetector(DesktopModelStore store) {
		this.store = store;
	}

	@Override
	public synchronized void initialize() throws EngineException {
		if (detector != null) {
			return;
		}
		byte[] detectorBytes = store.loadVerified(store.getFaceDetector());
		byte[] landmarkBytes = store.loadVerified(store.getFaceLandmarks());
		try {
			env = OrtEnvironment.getEnvironment();
			try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
				options.setIntraOpNumThreads(2);
				detector = env.createSession(detectorBytes, options);
				landmarks = env.createSession(landmarkBytes, options);
			}
			detectorInput = detector.getInputNames().iterator().next();
			landmarkInput = landmarks.getInputNames().iterator().next();
			for (Map.Entry<String, NodeInfo> e : landmarks.getOutputInfo().entrySet()) {
				long[] shape = ((TensorInfo) e.getValue().getInfo()).getShape();
				long last = shape[shape.length - 1];
				if (last >= MIN_LANDMARKS * 3L) {
					landmarkOutput = e.getKey();
				} else if (last == 1 && shape.length == 4) {
					presenceOutput = e.getKey();
				}
			}
			if (landmarkOutput == null || presenceOutput == null) {
				throw new EngineException("Unexpected landmark model outputs", null, true);
			}
		} catch (OrtException | RuntimeException e) {
			close();
			throw new EngineException("Face models failed to load", e, true);
		} catch (EngineException e) {
			close();
			throw e;
		}
		buildAnchors();
		description = "desktop-blazeface+facemesh:" + store.getFaceDetector().version + "+"
				+ store.getFaceLandmarks().version;
	}

	@Override
	public String describe() {
		return description;
	}

	@Override
	public synchronized void close() {
		closeQuietly(detector);
		closeQuietly(landmarks);
		detector = null;
		landmarks = null;
	}

	private static void closeQuietly(OrtSession session) {
		if (session != null) {
			try {
				session.close();
			} catch (OrtException ignored) {
				// nothing useful to do
			}
		}
	}

	@Override
	public synchronized List<FaceObservation> detect(FaceFrame frame) throws EngineException {
		if (detector == null) {
			throw new EngineException("Face detector not initialized", null);
		}
		if (frame == null || !frame.isValid()) {
			return Collections.emptyList();
		}
		try {
			List<Detection> faces = detectFaces(frame);
			if (faces.isEmpty()) {
				return Collections.emptyList();
			}
			FaceObservation primary = landmarksFor(frame, faces.get(0));
			if (primary == null) {
				return Collections.emptyList();
			}
			List<FaceObservation> out = new ArrayList<>(faces.size());
			out.add(primary);
			for (int i = 1; i < faces.size(); i++) {
				Detection d = faces.get(i);
				out.add(new FaceObservation(d.cx - d.w / 2, d.cy - d.h / 2, d.cx + d.w / 2, d.cy + d.h / 2,
						new float[0], new float[0]));
			}
			return out;
		} catch (OrtException e) {
			throw new EngineException("Face inference failed", e);
		}
	}

	// ---- detector ---------------------------------------------------------------------------------------------

	private static final class Detection {
		float score;
		float cx;
		float cy;
		float w;
		float h;
		/** six keypoints: right eye, left eye, nose, mouth, right ear, left ear (x, y pairs). */
		final float[] kp = new float[12];
	}

	private List<Detection> detectFaces(FaceFrame frame) throws OrtException {
		int w = frame.getWidth();
		int h = frame.getHeight();
		float scale = Math.min((float) DETECTOR_SIZE / w, (float) DETECTOR_SIZE / h);
		float ox = (DETECTOR_SIZE - w * scale) / 2f;
		float oy = (DETECTOR_SIZE - h * scale) / 2f;
		float[] in = new float[DETECTOR_SIZE * DETECTOR_SIZE * 3];
		float[] px = new float[3];
		for (int y = 0; y < DETECTOR_SIZE; y++) {
			for (int x = 0; x < DETECTOR_SIZE; x++) {
				float sx = (x + 0.5f - ox) / scale - 0.5f;
				float sy = (y + 0.5f - oy) / scale - 0.5f;
				int i = (y * DETECTOR_SIZE + x) * 3;
				if (sx < -0.5f || sy < -0.5f || sx > w - 0.5f || sy > h - 0.5f) {
					in[i] = PAD_VALUE;
					in[i + 1] = PAD_VALUE;
					in[i + 2] = PAD_VALUE;
				} else {
					bilinear(frame, sx, sy, px);
					in[i] = (px[0] - 127.5f) / 127.5f;
					in[i + 1] = (px[1] - 127.5f) / 127.5f;
					in[i + 2] = (px[2] - 127.5f) / 127.5f;
				}
			}
		}
		float[][][] regressors;
		float[][][] scores;
		try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(in),
				new long[] { 1, DETECTOR_SIZE, DETECTOR_SIZE, 3 });
				OrtSession.Result result = detector.run(Collections.singletonMap(detectorInput, input))) {
			regressors = (float[][][]) result.get("regressors").get().getValue();
			scores = (float[][][]) result.get("classificators").get().getValue();
		}
		List<Detection> candidates = new ArrayList<>();
		for (int a = 0; a < ANCHORS; a++) {
			float s = sigmoid(scores[0][a][0]);
			if (s < MIN_SCORE) {
				continue;
			}
			float[] r = regressors[0][a];
			Detection d = new Detection();
			d.score = s;
			// Normalised coordinates of the letterboxed 128x128 input.
			d.cx = r[0] / DETECTOR_SIZE + anchorX[a];
			d.cy = r[1] / DETECTOR_SIZE + anchorY[a];
			d.w = r[2] / DETECTOR_SIZE;
			d.h = r[3] / DETECTOR_SIZE;
			for (int k = 0; k < 6; k++) {
				d.kp[2 * k] = r[4 + 2 * k] / DETECTOR_SIZE + anchorX[a];
				d.kp[2 * k + 1] = r[5 + 2 * k] / DETECTOR_SIZE + anchorY[a];
			}
			candidates.add(d);
		}
		List<Detection> merged = weightedNms(candidates);
		// Back to frame pixels.
		for (Detection d : merged) {
			d.cx = (d.cx * DETECTOR_SIZE - ox) / scale;
			d.cy = (d.cy * DETECTOR_SIZE - oy) / scale;
			d.w = d.w * DETECTOR_SIZE / scale;
			d.h = d.h * DETECTOR_SIZE / scale;
			for (int k = 0; k < 6; k++) {
				d.kp[2 * k] = (d.kp[2 * k] * DETECTOR_SIZE - ox) / scale;
				d.kp[2 * k + 1] = (d.kp[2 * k + 1] * DETECTOR_SIZE - oy) / scale;
			}
		}
		return merged.size() > MAX_FACES ? merged.subList(0, MAX_FACES) : merged;
	}

	/** MediaPipe "weighted" suppression: overlapping detections are merged by score. */
	private static List<Detection> weightedNms(List<Detection> in) {
		List<Detection> remaining = new ArrayList<>(in);
		remaining.sort(Comparator.comparingDouble((Detection d) -> d.score).reversed());
		List<Detection> out = new ArrayList<>();
		while (!remaining.isEmpty()) {
			Detection best = remaining.remove(0);
			List<Detection> group = new ArrayList<>();
			group.add(best);
			for (int i = remaining.size() - 1; i >= 0; i--) {
				if (iou(best, remaining.get(i)) > NMS_IOU) {
					group.add(remaining.remove(i));
				}
			}
			Detection m = new Detection();
			m.score = best.score;
			float total = 0;
			for (Detection d : group) {
				total += d.score;
			}
			for (Detection d : group) {
				float wgt = d.score / total;
				m.cx += d.cx * wgt;
				m.cy += d.cy * wgt;
				m.w += d.w * wgt;
				m.h += d.h * wgt;
				for (int k = 0; k < 12; k++) {
					m.kp[k] += d.kp[k] * wgt;
				}
			}
			out.add(m);
		}
		return out;
	}

	private static float iou(Detection a, Detection b) {
		float ax0 = a.cx - a.w / 2, ax1 = a.cx + a.w / 2, ay0 = a.cy - a.h / 2, ay1 = a.cy + a.h / 2;
		float bx0 = b.cx - b.w / 2, bx1 = b.cx + b.w / 2, by0 = b.cy - b.h / 2, by1 = b.cy + b.h / 2;
		float iw = Math.max(0, Math.min(ax1, bx1) - Math.max(ax0, bx0));
		float ih = Math.max(0, Math.min(ay1, by1) - Math.max(ay0, by0));
		float inter = iw * ih;
		float union = a.w * a.h + b.w * b.h - inter;
		return union <= 0 ? 0 : inter / union;
	}

	private void buildAnchors() {
		// BlazeFace short range: two anchors per cell on a 16x16 grid, six on an 8x8 grid (896 in total).
		anchorX = new float[ANCHORS];
		anchorY = new float[ANCHORS];
		int n = 0;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				for (int k = 0; k < 2; k++) {
					anchorX[n] = (x + 0.5f) / 16f;
					anchorY[n++] = (y + 0.5f) / 16f;
				}
			}
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				for (int k = 0; k < 6; k++) {
					anchorX[n] = (x + 0.5f) / 8f;
					anchorY[n++] = (y + 0.5f) / 8f;
				}
			}
		}
	}

	// ---- landmarks --------------------------------------------------------------------------------------------

	private FaceObservation landmarksFor(FaceFrame frame, Detection d) throws OrtException {
		// Rotate the crop so the eyes are level (keypoints 0 and 1 are the eyes).
		double angle = Math.atan2(d.kp[3] - d.kp[1], d.kp[2] - d.kp[0]);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		float side = Math.max(d.w, d.h) * ROI_SCALE;
		float[] in = new float[LANDMARK_SIZE * LANDMARK_SIZE * 3];
		float[] px = new float[3];
		int w = frame.getWidth();
		int h = frame.getHeight();
		for (int y = 0; y < LANDMARK_SIZE; y++) {
			for (int x = 0; x < LANDMARK_SIZE; x++) {
				double u = (x + 0.5) / LANDMARK_SIZE - 0.5;
				double v = (y + 0.5) / LANDMARK_SIZE - 0.5;
				float sx = (float) (d.cx + side * (u * cos - v * sin)) - 0.5f;
				float sy = (float) (d.cy + side * (u * sin + v * cos)) - 0.5f;
				int i = (y * LANDMARK_SIZE + x) * 3;
				if (sx < -0.5f || sy < -0.5f || sx > w - 0.5f || sy > h - 0.5f) {
					in[i] = 0f;
					in[i + 1] = 0f;
					in[i + 2] = 0f;
				} else {
					bilinear(frame, sx, sy, px);
					in[i] = px[0] / 255f;
					in[i + 1] = px[1] / 255f;
					in[i + 2] = px[2] / 255f;
				}
			}
		}
		float[] flat;
		float presence;
		try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(in),
				new long[] { 1, LANDMARK_SIZE, LANDMARK_SIZE, 3 });
				OrtSession.Result result = landmarks.run(Collections.singletonMap(landmarkInput, input))) {
			flat = flatten(result.get(landmarkOutput).get());
			presence = flatten(result.get(presenceOutput).get())[0];
		}
		if (presence <= 0f || flat.length < MIN_LANDMARKS * 3) {
			return null; // the model does not see a face in the crop
		}
		int count = flat.length / 3;
		float[] xs = new float[count];
		float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			double lx = flat[3 * i] / LANDMARK_SIZE - 0.5;
			double ly = flat[3 * i + 1] / LANDMARK_SIZE - 0.5;
			xs[i] = (float) (d.cx + side * (lx * cos - ly * sin));
			ys[i] = (float) (d.cy + side * (lx * sin + ly * cos));
		}
		return FaceObservation.fromLandmarks(xs, ys);
	}

	private static float[] flatten(OnnxValue value) throws OrtException {
		List<Float> out = new ArrayList<>();
		collect(value.getValue(), out);
		float[] r = new float[out.size()];
		for (int i = 0; i < r.length; i++) {
			r[i] = out.get(i);
		}
		return r;
	}

	private static void collect(Object o, List<Float> out) {
		if (o instanceof float[]) {
			for (float f : (float[]) o) {
				out.add(f);
			}
		} else if (o instanceof Object[]) {
			for (Object x : (Object[]) o) {
				collect(x, out);
			}
		}
	}

	// ---- helpers ----------------------------------------------------------------------------------------------

	private static float sigmoid(float x) {
		float c = Math.max(-100f, Math.min(100f, x));
		return (float) (1.0 / (1.0 + Math.exp(-c)));
	}

	/** Bilinear sample of the RGBA frame at (x, y) in pixel-centre coordinates; writes r, g, b in 0..255. */
	private static void bilinear(FaceFrame f, float x, float y, float[] out) {
		int w = f.getWidth();
		int h = f.getHeight();
		byte[] rgba = f.getRgba();
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = x - x0;
		float fy = y - y0;
		int xa = Math.max(0, Math.min(w - 1, x0));
		int xb = Math.max(0, Math.min(w - 1, x0 + 1));
		int ya = Math.max(0, Math.min(h - 1, y0));
		int yb = Math.max(0, Math.min(h - 1, y0 + 1));
		for (int c = 0; c < 3; c++) {
			float p00 = rgba[(ya * w + xa) * 4 + c] & 0xFF;
			float p10 = rgba[(ya * w + xb) * 4 + c] & 0xFF;
			float p01 = rgba[(yb * w + xa) * 4 + c] & 0xFF;
			float p11 = rgba[(yb * w + xb) * 4 + c] & 0xFF;
			float top = p00 + (p10 - p00) * fx;
			float bottom = p01 + (p11 - p01) * fx;
			out[c] = top + (bottom - top) * fy;
		}
	}
}

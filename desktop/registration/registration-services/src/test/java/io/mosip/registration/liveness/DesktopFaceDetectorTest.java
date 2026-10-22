package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.mosip.registration.livenessmanager.api.LivenessEngine.EngineException;
import io.mosip.registration.livenessmanager.challenge.FaceGeometry;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/**
 * Runs the real, pinned ONNX models on the test photo that ships with this repository (in memory only).
 */
public class DesktopFaceDetectorTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private DesktopFaceDetector detector;
	private BufferedImage photo;

	@Before
	public void setUp() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/applicantPhoto.jpg")) {
			assertNotNull("test photo", in);
			photo = ImageIO.read(in);
		}
		detector = new DesktopFaceDetector(DesktopModelStore.bundled(tmp.newFolder("data")));
		detector.initialize();
	}

	@After
	public void tearDown() {
		detector.close();
	}

	private static FaceFrame frameOf(BufferedImage img) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "jpg", out);
		FaceFrame f = SbiStreamFrameSource.decode(out.toByteArray(), 1);
		assertNotNull(f);
		return f;
	}

	private static BufferedImage canvas(int w, int h, Color c) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(c);
		g.fillRect(0, 0, w, h);
		g.dispose();
		return img;
	}

	@Test
	public void findsOneFaceWithAtLeast468Landmarks() throws Exception {
		List<FaceObservation> faces = detector.detect(frameOf(photo));
		assertEquals(1, faces.size());
		FaceObservation f = faces.get(0);
		assertTrue("landmarks " + f.getLandmarkCount(), f.getLandmarkCount() >= FaceGeometry.MIN_LANDMARKS);

		// Inside the image, and landmark topology as FaceGeometry expects it.
		assertTrue(f.getLeft() >= -5 && f.getRight() <= photo.getWidth() + 5);
		assertTrue(f.getTop() >= -5 && f.getBottom() <= photo.getHeight() + 5);
		double eyeLeftOuter = f.x(263);
		double eyeRightOuter = f.x(33);
		assertTrue("eyes: right-eye outer (33) is left of left-eye outer (263) in an upright photo",
				eyeRightOuter < eyeLeftOuter);
		assertTrue("nose tip (1) between the eyes", f.x(1) > eyeRightOuter && f.x(1) < eyeLeftOuter);
		assertTrue("nose below the eyes", f.y(1) > f.y(33));
		assertTrue("mouth below the nose", f.y(61) > f.y(1));
		assertTrue("mouth corners left/right", f.x(61) < f.x(291));
		assertTrue("cheeks left/right", f.x(234) < f.x(454));
	}

	@Test
	public void geometryIsPlausibleForAnOpenEyedNeutralFace() throws Exception {
		FaceObservation f = detector.detect(frameOf(photo)).get(0);
		double ear = FaceGeometry.eyeAspectRatio(f);
		assertTrue("eye aspect ratio " + ear, ear > 0.12 && ear < 0.45);
		double yaw = FaceGeometry.yawDegrees(f, false);
		assertTrue("frontal face, yaw " + yaw, Math.abs(yaw) < 25);
	}

	@Test
	public void aTiltedHeadIsFollowedInTheRightDirection() throws Exception {
		for (double degrees : new double[] { 25, -25 }) {
			BufferedImage big = canvas(420, 420, new Color(70, 70, 70));
			Graphics2D g = big.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			AffineTransform t = new AffineTransform();
			t.translate(210, 210);
			t.rotate(Math.toRadians(degrees));
			t.translate(-photo.getWidth() / 2.0, -photo.getHeight() / 2.0);
			g.drawImage(photo, t, null);
			g.dispose();

			List<FaceObservation> faces = detector.detect(frameOf(big));
			assertEquals("rotated " + degrees, 1, faces.size());
			FaceObservation f = faces.get(0);
			double measured = Math.toDegrees(Math.atan2(f.y(263) - f.y(33), f.x(263) - f.x(33)));
			assertEquals("eye line angle for a head rotated by " + degrees, degrees, measured, 12.0);
		}
	}

	@Test
	public void aBlankFrameHasNoFace() throws Exception {
		assertTrue(detector.detect(frameOf(canvas(320, 240, Color.GRAY))).isEmpty());
		assertTrue(detector.detect(frameOf(canvas(320, 240, Color.BLACK))).isEmpty());
	}

	@Test
	public void twoFacesAreBothCountedButOnlyTheFirstGetsLandmarks() throws Exception {
		BufferedImage two = canvas(photo.getWidth() * 2 + 40, photo.getHeight(), new Color(70, 70, 70));
		Graphics2D g = two.createGraphics();
		g.drawImage(photo, 0, 0, null);
		g.drawImage(photo, photo.getWidth() + 40, 0, null);
		g.dispose();

		List<FaceObservation> faces = detector.detect(frameOf(two));
		assertEquals(2, faces.size());
		assertTrue(faces.get(0).getLandmarkCount() >= FaceGeometry.MIN_LANDMARKS);
		assertEquals(0, faces.get(1).getLandmarkCount());
		assertTrue(faces.get(1).getWidth() > 20);
	}

	@Test
	public void anInvalidFrameYieldsNoFace() throws Exception {
		assertTrue(detector.detect(null).isEmpty());
		assertTrue(detector.detect(new FaceFrame(1, 1, 10, 10, new byte[3], false, null)).isEmpty());
	}

	@Test
	public void detectionKeepsUpWithTheStreamOnThisMachine() throws Exception {
		FaceFrame frame = frameOf(photo);
		for (int i = 0; i < 5; i++) {
			detector.detect(frame); // warm-up
		}
		int n = 30;
		long[] ms = new long[n];
		for (int i = 0; i < n; i++) {
			long t0 = System.nanoTime();
			assertEquals(1, detector.detect(frame).size());
			ms[i] = (System.nanoTime() - t0) / 1_000_000;
		}
		Arrays.sort(ms);
		long mean = Arrays.stream(ms).sum() / n;
		System.out.println("DesktopFaceDetector timing on " + System.getProperty("os.name") + " "
				+ System.getProperty("os.arch") + ": mean=" + mean + " ms, p95=" + ms[(int) (n * 0.95) - 1]
				+ " ms (detector+landmarks, " + frame.getWidth() + "x" + frame.getHeight() + ")");
		assertTrue("p95 " + ms[(int) (n * 0.95) - 1] + " ms", ms[(int) (n * 0.95) - 1] < 1000);
	}

	@Test
	public void theWholeEngineKeepsUpAt640x480() throws Exception {
		// A camera-sized frame: the test photo scaled onto a 640x480 canvas.
		BufferedImage canvas = canvas(640, 480, new Color(70, 70, 70));
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int h = 440;
		int w = photo.getWidth() * h / photo.getHeight();
		g.drawImage(photo, (640 - w) / 2, 20, w, h, null);
		g.dispose();
		FaceFrame frame = frameOf(canvas);
		assertEquals(640, frame.getWidth());

		io.mosip.registration.livenessmanager.engine.DefaultLivenessEngine engine = new io.mosip.registration.livenessmanager.engine.DefaultLivenessEngine(
				detector, DesktopLivenessService.padModel(DesktopModelStore.bundled(tmp.newFolder("engine"))),
				new io.mosip.registration.livenessmanager.quality.LumaQualityAnalyzer());
		engine.initialize();
		io.mosip.registration.livenessmanager.model.LivenessPolicy policy = new io.mosip.registration.livenessmanager.model.LivenessPolicy();
		for (int i = 0; i < 5; i++) {
			engine.analyze(frame, policy, true);
		}
		int n = 40;
		long[] withPad = new long[n];
		long[] noPad = new long[n];
		for (int i = 0; i < n; i++) {
			long t0 = System.nanoTime();
			engine.analyze(frame, policy, true);
			withPad[i] = (System.nanoTime() - t0) / 1_000_000;
			t0 = System.nanoTime();
			engine.analyze(frame, policy, false);
			noPad[i] = (System.nanoTime() - t0) / 1_000_000;
		}
		Arrays.sort(withPad);
		Arrays.sort(noPad);
		System.out.println("DesktopLivenessEngine timing on " + System.getProperty("os.name") + " "
				+ System.getProperty("os.arch") + " (" + Runtime.getRuntime().availableProcessors() + " cores, JDK "
				+ System.getProperty("java.version") + "), 640x480: with PAD mean=" + (Arrays.stream(withPad).sum() / n)
				+ " ms p95=" + withPad[(int) (n * 0.95) - 1] + " ms; without PAD mean="
				+ (Arrays.stream(noPad).sum() / n) + " ms p95=" + noPad[(int) (n * 0.95) - 1] + " ms");
		assertTrue("p95 " + withPad[(int) (n * 0.95) - 1] + " ms", withPad[(int) (n * 0.95) - 1] < 1500);
		engine.close();
	}

	// ---- integrity --------------------------------------------------------------------------------------------

	@Test
	public void aTamperedModelFailsClosedWithAnIntegrityError() throws Exception {
		DesktopModelStore tampered = DesktopModelStore.load(resource -> {
			InputStream in = DesktopModelStore.class.getResourceAsStream(resource);
			if (in != null && resource.endsWith("face_landmarks.onnx")) {
				byte[] bytes = in.readAllBytes();
				bytes[bytes.length / 2] ^= 0x01;
				return new java.io.ByteArrayInputStream(bytes);
			}
			return in;
		}, tmp.newFolder("tampered"));
		DesktopFaceDetector bad = new DesktopFaceDetector(tampered);
		try {
			bad.initialize();
			fail("a modified model must never load");
		} catch (EngineException e) {
			assertTrue(e.isIntegrityFailure());
		}
	}

	@Test
	public void aMissingModelFailsClosedWithAnIntegrityError() throws Exception {
		DesktopModelStore missing = DesktopModelStore.load(
				resource -> resource.endsWith("face_detector.onnx") ? null
						: DesktopModelStore.class.getResourceAsStream(resource),
				tmp.newFolder("missing"));
		try {
			new DesktopFaceDetector(missing).initialize();
			fail("a missing model must fail");
		} catch (EngineException e) {
			assertTrue(e.isIntegrityFailure());
		}
	}

	@Test
	public void everyManifestEntryMatchesItsBundledFile() throws Exception {
		DesktopModelStore store = DesktopModelStore.bundled(tmp.newFolder("bundled"));
		assertEquals(64, store.getFaceDetector().sha256.length());
		store.loadVerified(store.getFaceDetector());
		store.loadVerified(store.getFaceLandmarks());
		assertEquals(2, store.getPad().size());
		for (DesktopModelStore.Entry e : store.getPad()) {
			store.loadVerified(e);
		}
	}

	@Test
	public void padModelsAreMaterialisedVerifiedAndAlone() throws Exception {
		File data = tmp.newFolder("pad");
		DesktopModelStore store = DesktopModelStore.bundled(data);
		File dir = store.materializePad();
		File[] files = dir.listFiles();
		assertNotNull(files);
		assertEquals(2, files.length);

		// A planted extra model and a corrupted copy are both dealt with on the next start.
		File rogue = new File(dir, "evil.onnx");
		assertTrue(rogue.createNewFile());
		File victim = files[0];
		java.nio.file.Files.write(victim.toPath(), new byte[] { 1, 2, 3 });
		File again = DesktopModelStore.bundled(data).materializePad();
		assertEquals(dir, again);
		assertFalse(rogue.exists());
		assertTrue(victim.length() > 1000);
		assertEquals(2, again.listFiles().length);
	}
}

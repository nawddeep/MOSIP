package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Test;

import io.mosip.registration.livenessmanager.api.FaceFrameSource;
import io.mosip.registration.livenessmanager.model.FaceFrame;

public class SbiStreamFrameSourceTest {

	private SbiStreamFrameSource source;

	@After
	public void tearDown() {
		if (source != null) {
			source.stop();
		}
	}

	/** A solid-colour JPEG, built in memory (no real person's image is used in tests). */
	static byte[] jpeg(int width, int height, Color color) throws Exception {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "jpg", out);
		return out.toByteArray();
	}

	private static class Collector implements FaceFrameSource.Listener {
		final List<FaceFrame> frames = new CopyOnWriteArrayList<>();
		final List<FaceFrameSource.Failure> errors = new CopyOnWriteArrayList<>();

		@Override
		public void onFrame(FaceFrame frame) {
			frames.add(frame);
		}

		@Override
		public void onError(FaceFrameSource.Failure failure, String detail) {
			errors.add(failure);
		}
	}

	private static void await(java.util.function.BooleanSupplier condition, String what) throws Exception {
		long end = System.currentTimeMillis() + 5_000;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > end) {
				throw new AssertionError("timed out waiting for " + what);
			}
			Thread.sleep(5);
		}
	}

	@Test
	public void aLargeFrameIsScaledToAtMost640PxAndDeliveredAsRgba() throws Exception {
		source = new SbiStreamFrameSource();
		Collector c = new Collector();
		source.start(null, c);
		source.offerJpeg(jpeg(1280, 720, new Color(250, 10, 10)));
		await(() -> !c.frames.isEmpty(), "first frame");

		FaceFrame f = c.frames.get(0);
		assertEquals(640, f.getWidth());
		assertEquals(360, f.getHeight());
		assertTrue(f.isValid());
		assertFalse("SBI frames are not mirrored", f.isMirrored());
		int r = f.getRgba()[0] & 0xFF;
		int g = f.getRgba()[1] & 0xFF;
		assertTrue("red channel " + r, r > 200);
		assertTrue("green channel " + g, g < 60);
		assertEquals(0xFF, f.getRgba()[3] & 0xFF);
		assertEquals(FaceFrameSource.SourceType.VENDOR_DEVICE, source.getType());
	}

	@Test
	public void aPortraitFrameKeepsItsAspectRatio() throws Exception {
		source = new SbiStreamFrameSource();
		Collector c = new Collector();
		source.start(null, c);
		source.offerJpeg(jpeg(720, 1280, Color.GRAY));
		await(() -> !c.frames.isEmpty(), "frame");
		assertEquals(360, c.frames.get(0).getWidth());
		assertEquals(640, c.frames.get(0).getHeight());
	}

	@Test
	public void aSmallFrameIsNotResized() throws Exception {
		source = new SbiStreamFrameSource();
		Collector c = new Collector();
		source.start(null, c);
		source.offerJpeg(jpeg(320, 240, Color.GRAY));
		await(() -> !c.frames.isEmpty(), "frame");
		assertEquals(320, c.frames.get(0).getWidth());
		assertEquals(240, c.frames.get(0).getHeight());
	}

	@Test
	public void onlyTheLatestFrameIsKeptWhileTheEngineIsBusy() throws Exception {
		source = new SbiStreamFrameSource();
		CountDownLatch firstSeen = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		List<FaceFrame> frames = new CopyOnWriteArrayList<>();
		source.start(null, new FaceFrameSource.Listener() {
			@Override
			public void onFrame(FaceFrame frame) {
				frames.add(frame);
				if (frames.size() == 1) {
					firstSeen.countDown();
					try {
						release.await(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}

			@Override
			public void onError(FaceFrameSource.Failure failure, String detail) {
			}
		});

		source.offerJpeg(jpeg(64, 64, Color.BLUE));
		assertTrue(firstSeen.await(5, TimeUnit.SECONDS));

		// The "engine" is busy: 40 more frames arrive, the last one is pure green.
		for (int i = 0; i < 39; i++) {
			source.offerJpeg(jpeg(64, 64, new Color(200, 0, 0)));
		}
		source.offerJpeg(jpeg(64, 64, Color.GREEN));
		assertTrue("older frames are dropped", source.getDroppedFrames() >= 38);

		release.countDown();
		await(() -> frames.size() >= 2, "second frame");
		FaceFrame second = frames.get(1);
		assertTrue("second delivered frame is the latest one (green)",
				(second.getRgba()[1] & 0xFF) > 200 && (second.getRgba()[0] & 0xFF) < 60);
		assertEquals(2, source.getDeliveredFrames());
		assertEquals(40, source.getOfferedFrames() - 1);
	}

	@Test
	public void garbageIsSkippedWithoutKillingTheStream() throws Exception {
		source = new SbiStreamFrameSource();
		Collector c = new Collector();
		source.start(null, c);
		source.offerJpeg(new byte[] { 1, 2, 3, 4, 5 });
		await(() -> source.getUndecodableFrames() >= 1, "garbage skipped");
		source.offerJpeg(jpeg(80, 80, Color.GRAY));
		await(() -> !c.frames.isEmpty(), "good frame after garbage");
		assertTrue(c.errors.isEmpty());
	}

	@Test
	public void aSilentStreamIsReportedOnce() throws Exception {
		source = new SbiStreamFrameSource(150);
		Collector c = new Collector();
		source.start(null, c);
		await(() -> !c.errors.isEmpty(), "stall error");
		Thread.sleep(400);
		assertEquals(1, c.errors.size());
		assertEquals(FaceFrameSource.Failure.STREAM_ERROR, c.errors.get(0));
	}

	@Test
	public void nothingIsDeliveredAfterStop() throws Exception {
		source = new SbiStreamFrameSource();
		Collector c = new Collector();
		source.start(null, c);
		source.offerJpeg(jpeg(64, 64, Color.GRAY));
		await(() -> !c.frames.isEmpty(), "frame");
		source.stop();
		int before = c.frames.size();
		source.offerJpeg(jpeg(64, 64, Color.GRAY));
		Thread.sleep(150);
		assertEquals(before, c.frames.size());
	}

	@Test
	public void aListenerThatThrowsDoesNotKillTheSource() throws Exception {
		source = new SbiStreamFrameSource();
		AtomicReference<Integer> seen = new AtomicReference<>(0);
		source.start(null, new FaceFrameSource.Listener() {
			@Override
			public void onFrame(FaceFrame frame) {
				seen.set(seen.get() + 1);
				throw new IllegalStateException("boom");
			}

			@Override
			public void onError(FaceFrameSource.Failure failure, String detail) {
			}
		});
		source.offerJpeg(jpeg(64, 64, Color.GRAY));
		await(() -> seen.get() >= 1, "first frame");
		source.offerJpeg(jpeg(64, 64, Color.GRAY));
		await(() -> seen.get() >= 2, "second frame despite the exception");
		assertNotNull(source);
	}
}

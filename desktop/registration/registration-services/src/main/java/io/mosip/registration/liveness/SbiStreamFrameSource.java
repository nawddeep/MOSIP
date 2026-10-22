package io.mosip.registration.liveness;

import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import io.mosip.registration.livenessmanager.api.FaceFrameSource;
import io.mosip.registration.livenessmanager.api.PreviewSink;
import io.mosip.registration.livenessmanager.model.FaceFrame;

/**
 * Feeds the liveness engine from the SBI preview stream the client already shows: it never opens a second
 * stream. {@code Streamer} hands every decoded JPEG to {@link #offerJpeg(byte[])}.
 *
 * <p>
 * Only the <b>latest</b> frame is kept. While the engine is busy the stream thread simply replaces the pending
 * frame, so the preview is never slowed down and the engine always works on the freshest image. Frames are
 * decoded on this class's own thread, scaled so the longer side is at most {@value #MAX_SIDE} px, and handed
 * over as RGBA. Frames stay in memory only: nothing is written to disk and nothing about pixels is logged.
 *
 * <p>
 * The SBI image is not mirrored, so {@code mirrored=false} is reported (head-turn direction is then computed on
 * the frame as received). The UI mirrors the preview for the person looking at it.
 */
public final class SbiStreamFrameSource implements FaceFrameSource {

	/** Longest side handed to the engine. */
	public static final int MAX_SIDE = 640;
	/** No frame for this long after start (or after the last frame) is reported as a stream error. */
	public static final long DEFAULT_STALL_MILLIS = 5_000;

	private static final long POLL_MILLIS = 100;

	private final long stallMillis;
	private final AtomicReference<byte[]> latest = new AtomicReference<>();
	private final AtomicLong offered = new AtomicLong();
	private final AtomicLong dropped = new AtomicLong();
	private final AtomicLong delivered = new AtomicLong();
	private final AtomicLong undecodable = new AtomicLong();
	private final Object signal = new Object();

	private volatile boolean running;
	private volatile Thread worker;

	public SbiStreamFrameSource() {
		this(DEFAULT_STALL_MILLIS);
	}

	public SbiStreamFrameSource(long stallMillis) {
		this.stallMillis = stallMillis;
	}

	@Override
	public SourceType getType() {
		return SourceType.VENDOR_DEVICE;
	}

	@Override
	public synchronized void start(PreviewSink previewSink, Listener listener) {
		if (running) {
			return;
		}
		running = true;
		latest.set(null);
		Thread t = new Thread(() -> run(listener), "LIVENESS_FRAMES");
		t.setDaemon(true);
		worker = t;
		t.start();
	}

	@Override
	public synchronized void stop() {
		running = false;
		latest.set(null);
		Thread t = worker;
		worker = null;
		if (t != null) {
			t.interrupt();
			if (t != Thread.currentThread()) {
				try {
					t.join(1_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/** Called from the stream thread for every frame. Never blocks and never throws. */
	public void offerJpeg(byte[] jpeg) {
		if (!running || jpeg == null) {
			return;
		}
		offered.incrementAndGet();
		if (latest.getAndSet(jpeg) != null) {
			dropped.incrementAndGet();
		}
		synchronized (signal) {
			signal.notifyAll();
		}
	}

	/** Frames replaced before the engine took them (the engine was busy). */
	public long getDroppedFrames() {
		return dropped.get();
	}

	public long getDeliveredFrames() {
		return delivered.get();
	}

	public long getOfferedFrames() {
		return offered.get();
	}

	public long getUndecodableFrames() {
		return undecodable.get();
	}

	private void run(Listener listener) {
		long lastFrameAt = System.currentTimeMillis();
		boolean stallReported = false;
		long frameId = 0;
		while (running) {
			byte[] jpeg = latest.getAndSet(null);
			if (jpeg == null) {
				if (!stallReported && System.currentTimeMillis() - lastFrameAt > stallMillis) {
					stallReported = true;
					safe(() -> listener.onError(Failure.STREAM_ERROR, "no frames from the device"));
				}
				synchronized (signal) {
					if (latest.get() == null && running) {
						try {
							signal.wait(POLL_MILLIS);
						} catch (InterruptedException e) {
							return;
						}
					}
				}
				continue;
			}
			FaceFrame frame = decode(jpeg, ++frameId);
			if (frame == null) {
				undecodable.incrementAndGet();
				continue;
			}
			lastFrameAt = System.currentTimeMillis();
			stallReported = false;
			delivered.incrementAndGet();
			// Blocks while the engine works; frames arriving meanwhile replace each other in 'latest'.
			safe(() -> listener.onFrame(frame));
		}
	}

	private static void safe(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException ignored) {
			// A listener failure must never kill the frame thread.
		}
	}

	static FaceFrame decode(byte[] jpeg, long frameId) {
		BufferedImage src;
		try {
			src = ImageIO.read(new ByteArrayInputStream(jpeg));
		} catch (IOException | RuntimeException e) {
			return null;
		}
		if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
			return null;
		}
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage img = src;
		int longest = Math.max(w, h);
		if (longest > MAX_SIDE) {
			double scale = (double) MAX_SIDE / longest;
			w = Math.max(1, (int) Math.round(w * scale));
			h = Math.max(1, (int) Math.round(h * scale));
			BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = scaled.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(src, 0, 0, w, h, null);
			} finally {
				g.dispose();
			}
			img = scaled;
		}
		int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
		byte[] rgba = new byte[w * h * 4];
		for (int i = 0, p = 0; i < argb.length; i++, p += 4) {
			int v = argb[i];
			rgba[p] = (byte) (v >> 16);
			rgba[p + 1] = (byte) (v >> 8);
			rgba[p + 2] = (byte) v;
			rgba[p + 3] = (byte) 0xFF;
		}
		return new FaceFrame(frameId, System.nanoTime(), w, h, rgba, false, null);
	}
}

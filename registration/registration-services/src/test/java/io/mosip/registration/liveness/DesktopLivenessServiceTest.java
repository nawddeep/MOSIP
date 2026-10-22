package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.config.CanonicalJson;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditEvent;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditManagerService;
import io.mosip.registration.livenessmanager.desktop.DesktopGlobalParamService;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessConfigAdapter;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * The whole Desktop chain with the real engine and real models: fake SBI tap -> frame source -> detector, PAD,
 * quality -> session state machine -> audit. The "device" replays a still photo, an injected-still attack.
 */
public class DesktopLivenessServiceTest {

	/** Shortest sensible policy: one attempt, one 3 s challenge. */
	private static final String FAST = "{\"schemaVersion\":1,\"configVersion\":\"fast\",\"policies\":{"
			+ "\"resident\":{\"enabled\":true,\"maxRetries\":1,\"challengeCount\":1,\"challengeTimeoutSeconds\":3},"
			+ "\"operator\":{\"enabled\":false},\"supervisor\":{\"enabled\":false}}}";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final List<DesktopAuditEvent> auditEvents = Collections.synchronizedList(new ArrayList<>());
	private final List<String> auditDescriptions = Collections.synchronizedList(new ArrayList<>());
	private DesktopLivenessRuntime runtime;
	private DesktopLivenessService service;
	private byte[] photoJpeg;

	private static String sign(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		JsonObject integrity = new JsonObject();
		integrity.addProperty("algorithm", "SHA-256");
		integrity.addProperty("value", CanonicalJson.integrityDigest(o));
		o.add("integrity", integrity);
		return o.toString();
	}

	@Before
	public void setUp() throws Exception {
		DesktopAuditManagerService audit = (event, moduleId, moduleName, description) -> {
			auditEvents.add(event);
			auditDescriptions.add(description);
		};
		DesktopGlobalParamService params = code -> DesktopLivenessConfigAdapter.GLOBAL_PARAM_KEY.equals(code)
				? sign(FAST)
				: null;
		runtime = new DesktopLivenessRuntime(params, audit, tmp.newFolder("liveness"));
		assertTrue(runtime.refreshConfig(true));
		service = new DesktopLivenessService(runtime);

		try (InputStream in = getClass().getResourceAsStream("/applicantPhoto.jpg")) {
			assertNotNull(in);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(ImageIO.read(in), "jpg", out);
			photoJpeg = out.toByteArray();
		}
	}

	@After
	public void tearDown() {
		service.shutdown();
	}

	/** Pushes the photo at ~15 fps until detached. */
	private final class PhotoReplayTap implements SbiStreamTap {
		final AtomicBoolean attached = new AtomicBoolean();
		final AtomicInteger detaches = new AtomicInteger();
		volatile Thread pump;

		@Override
		public void attach(Consumer<byte[]> listener) {
			attached.set(true);
			pump = new Thread(() -> {
				while (attached.get()) {
					listener.accept(photoJpeg.clone());
					try {
						Thread.sleep(66);
					} catch (InterruptedException e) {
						return;
					}
				}
			}, "TEST_SBI_PUMP");
			pump.setDaemon(true);
			pump.start();
		}

		@Override
		public void detach() {
			attached.set(false);
			detaches.incrementAndGet();
		}
	}

	private static class Events implements io.mosip.registration.livenessmanager.api.LivenessEventListener {
		final List<LivenessEvent> all = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch terminal = new CountDownLatch(1);

		@Override
		public void onEvent(LivenessEvent event) {
			all.add(event);
			LivenessState s = event.getState();
			if (s.isTerminal() || s.getKind() == LivenessState.Kind.SYSTEM_ERROR
					|| s.getKind() == LivenessState.Kind.ATTEMPT_FAILURE) {
				terminal.countDown();
			}
		}

		LivenessState last() {
			synchronized (all) {
				return all.get(all.size() - 1).getState();
			}
		}
	}

	@Test
	public void aStillPhotoReplayedByTheDeviceNeverEarnsAPass() throws Exception {
		PhotoReplayTap tap = new PhotoReplayTap();
		Events events = new Events();

		service.start(Workflow.RESIDENT, tap, events);

		assertTrue("session ended: " + events.all, events.terminal.await(60, TimeUnit.SECONDS));
		LivenessState end = events.last();
		StringBuilder path = new StringBuilder();
		synchronized (events.all) {
			for (LivenessEvent e : events.all) {
				if (path.length() > 0) {
					path.append(" > ");
				}
				path.append(e.getState());
			}
		}
		System.out.println("Still-photo replay attack: " + path);
		assertFalse("a still photo must not pass, ended in " + end, end == LivenessState.SUCCESS);
		assertFalse(runtime.getGate().isValid(Workflow.RESIDENT));
		assertFalse(service.checkAndConsumePass(Workflow.RESIDENT, "individualBiometrics"));

		// The attempt was audited through the real adapter chain, without any image data.
		assertTrue(auditEvents.toString(), auditEvents.contains(DesktopAuditEvent.FACE_LIVENESS_SESSION));
		assertTrue(auditEvents.toString(), auditEvents.contains(DesktopAuditEvent.FACE_LIVENESS_GATE_BLOCKED));
		for (String d : auditDescriptions) {
			assertTrue(d, d.length() < 2048);
			assertFalse(d, d.matches(".*[A-Za-z0-9+/=]{200,}.*"));
		}
		service.cancel();
		assertTrue("the preview tap was released", tap.detaches.get() >= 1);
		assertFalse(tap.attached.get());
	}

	@Test
	public void noDeviceIsReportedAsUnavailableAndIssuesNoPass() throws Exception {
		SbiStreamTap none = new SbiStreamTap() {
			@Override
			public void attach(Consumer<byte[]> jpegListener) throws FaceCaptureDevice.DeviceException {
				throw new FaceCaptureDevice.DeviceException("no face device", true, null);
			}

			@Override
			public void detach() {
			}
		};
		Events events = new Events();
		service.start(Workflow.RESIDENT, none, events);

		assertTrue(events.terminal.await(30, TimeUnit.SECONDS));
		assertEquals(LivenessState.DEVICE_UNAVAILABLE, events.last());
		assertFalse(runtime.getGate().isValid(Workflow.RESIDENT));
	}

	@Test
	public void aModifiedModelStopsTheSessionWithAnEngineError() throws Exception {
		DesktopLivenessService broken = new DesktopLivenessService(runtime) {
			@Override
			DesktopModelStore openModelStore() throws LivenessEngine.EngineException {
				throw new LivenessEngine.EngineException("Model file failed its integrity check", null, true);
			}
		};
		PhotoReplayTap tap = new PhotoReplayTap();
		Events events = new Events();
		broken.start(Workflow.RESIDENT, tap, events);

		assertEquals(LivenessState.ENGINE_ERROR, events.last());
		assertFalse("no stream was opened", tap.attached.get());
		assertFalse(runtime.getGate().isValid(Workflow.RESIDENT));
		assertTrue(auditDescriptions.toString(), auditDescriptions.toString().contains("LIV-403"));
		assertTrue(auditEvents.contains(DesktopAuditEvent.FACE_LIVENESS_SESSION));
	}

	@Test
	public void aWorkflowWithLivenessDisabledPassesWithoutTouchingTheCamera() throws Exception {
		PhotoReplayTap tap = new PhotoReplayTap();
		Events events = new Events();
		assertFalse(service.isEnabled(Workflow.OPERATOR));

		service.start(Workflow.OPERATOR, tap, events);

		assertEquals(LivenessState.SUCCESS, events.last());
		assertFalse("camera never opened", tap.attached.get());
		assertTrue(service.checkAndConsumePass(Workflow.OPERATOR, "login"));
	}

	@Test
	public void maxAttemptsComesFromThePolicy() {
		assertEquals(1, service.getMaxAttempts(Workflow.RESIDENT));
		File dir = runtime.getDataDirectory();
		assertNotNull(dir);
	}
}

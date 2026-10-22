package io.mosip.registration.liveness;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.minifasnet.MiniFasNetPad;
import org.minifasnet.PadException;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.LivenessEventListener;
import io.mosip.registration.livenessmanager.api.PreviewSink;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.core.LivenessController;
import io.mosip.registration.livenessmanager.engine.DefaultLivenessEngine;
import io.mosip.registration.livenessmanager.engine.EnsemblePadModel;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.LivenessErrorCode;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;
import io.mosip.registration.livenessmanager.quality.LumaQualityAnalyzer;

/**
 * The Desktop face liveness flow behind one small API for the JavaFX screens: {@link #start}, {@link #retry},
 * {@link #cancel}. It owns the single long-lived {@link LivenessController} (so the ONNX models load once per
 * process), the engine {@code DefaultLivenessEngine(detector, pad, quality)}, and the daemon threads.
 *
 * <p>
 * Events reach the listener on engine threads; the UI must hop to the JavaFX Application Thread.
 *
 * <p>
 * Fails closed: if the engine cannot be assembled (missing or modified model, bad manifest) the listener gets
 * {@code ENGINE_ERROR}, an audit row is written, and no pass is ever issued.
 */
@Component
public class DesktopLivenessService {

	private static final Logger LOGGER = Logger.getLogger(DesktopLivenessService.class.getName());

	/** Nothing to render into: the JavaFX screen shows the SBI preview itself. */
	private static final PreviewSink NO_SINK = new PreviewSink() {
		@Override
		public Object acquireSurface(int width, int height) {
			return null;
		}

		@Override
		public void onPreviewTransform(int width, int height, int rotationDegrees, boolean mirror) {
		}
	};

	private final DesktopLivenessRuntime runtime;

	private LivenessController controller;
	private ExecutorService background;
	private ScheduledExecutorService tickerExecutor;
	private volatile FaceCaptureDevice device;

	@Autowired
	public DesktopLivenessService(DesktopLivenessRuntime runtime) {
		this.runtime = runtime;
	}

	/** Is a liveness pass required for this workflow? Fails closed when the policy cannot be read. */
	public boolean isEnabled(Workflow workflow) {
		return runtime.isEnabled(workflow);
	}

	/** The Java-side gate (see {@link DesktopLivenessRuntime#checkAndConsumePass}). */
	public boolean checkAndConsumePass(Workflow workflow, String fieldId) {
		return runtime.checkAndConsumePass(workflow, fieldId);
	}

	public int getMaxAttempts(Workflow workflow) {
		return Math.max(1, runtime.getConfig().getPolicy(workflow).maxRetries);
	}

	/**
	 * Starts a session. Frames come from {@code tap}; a failure to assemble the engine is reported to the
	 * listener as {@code ENGINE_ERROR}.
	 */
	public synchronized void start(Workflow workflow, SbiStreamTap tap, LivenessEventListener listener) {
		LivenessController c;
		try {
			c = controller();
		} catch (LivenessEngine.EngineException | RuntimeException | LinkageError e) {
			boolean integrity = e instanceof LivenessEngine.EngineException
					&& ((LivenessEngine.EngineException) e).isIntegrityFailure();
			LOGGER.log(Level.WARNING, "Liveness engine unavailable: " + e.getClass().getSimpleName());
			runtime.getAuditLogger()
					.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_ENDED, System.currentTimeMillis())
							.session(UUID.randomUUID().toString(), workflow, 1)
							.outcome(LivenessAuditRecord.Outcome.ERROR)
							.error(integrity ? LivenessErrorCode.MODEL_INTEGRITY_FAILED
									: LivenessErrorCode.ENGINE_INIT_FAILED)
							.build());
			listener.onEvent(new LivenessEvent(LivenessState.ENGINE_ERROR, workflow, 1, 1, null, null, null));
			return;
		}
		device = new SbiFaceCaptureDevice(tap);
		c.start(workflow, NO_SINK, listener);
	}

	/** "Try again" after a failed attempt, or "Retry connection" after a device error. */
	public synchronized void retry() {
		if (controller != null) {
			controller.retry();
		}
	}

	/** Cancels the session, revokes any pass, and releases the stream tap. */
	public synchronized void cancel() {
		if (controller != null) {
			controller.cancel();
		}
	}

	/** Releases the models and threads (application shutdown). */
	public synchronized void shutdown() {
		if (controller != null) {
			controller.release();
			controller = null;
		}
		if (background != null) {
			background.shutdownNow();
			background = null;
		}
		if (tickerExecutor != null) {
			tickerExecutor.shutdownNow();
			tickerExecutor = null;
		}
	}

	private LivenessController controller() throws LivenessEngine.EngineException {
		if (controller != null) {
			return controller;
		}
		DesktopModelStore store = openModelStore();
		background = Executors.newSingleThreadExecutor(r -> daemon(r, "LIVENESS_CONNECT"));
		tickerExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "LIVENESS_TICKER"));
		LivenessController.Ticker ticker = (tick, periodMillis) -> {
			ScheduledFuture<?> f = tickerExecutor.scheduleAtFixedRate(() -> {
				try {
					tick.run();
				} catch (RuntimeException ignored) {
					// never kill the ticker thread
				}
			}, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
			return () -> f.cancel(false);
		};
		controller = new LivenessController(() -> device, new LazyEngine(store), runtime.getConfig(),
				runtime.getAuditLogger(), runtime.getGate(), background, ticker, System::currentTimeMillis);
		return controller;
	}

	/** The pinned models. Overridable so tests can present a modified model. */
	DesktopModelStore openModelStore() throws LivenessEngine.EngineException {
		return DesktopModelStore.bundled(runtime.getDataDirectory());
	}

	private static Thread daemon(Runnable r, String name) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		return t;
	}

	/** Builds the real engine on first use (it is the slow part: ONNX sessions for four models). */
	static final class LazyEngine implements LivenessEngine {
		private final DesktopModelStore store;
		private DefaultLivenessEngine delegate;

		LazyEngine(DesktopModelStore store) {
			this.store = store;
		}

		@Override
		public void initialize() throws EngineException {
			if (delegate != null) {
				return;
			}
			DefaultLivenessEngine engine = new DefaultLivenessEngine(new DesktopFaceDetector(store), padModel(store),
					new LumaQualityAnalyzer());
			try {
				engine.initialize();
			} catch (EngineException e) {
				engine.close();
				throw e;
			}
			delegate = engine;
		}

		@Override
		public FrameAnalysis analyze(FaceFrame frame, LivenessPolicy policy, boolean runPad) throws EngineException {
			if (delegate == null) {
				throw new EngineException("Engine not initialized", null);
			}
			return delegate.analyze(frame, policy, runPad);
		}

		@Override
		public String describe() {
			return delegate == null ? "uninitialized" : delegate.describe();
		}

		@Override
		public void close() {
			if (delegate != null) {
				delegate.close();
				delegate = null;
			}
		}
	}

	static EnsemblePadModel padModel(DesktopModelStore store) {
		StringBuilder versions = new StringBuilder();
		for (DesktopModelStore.Entry m : store.getPad()) {
			if (versions.length() > 0) {
				versions.append('+');
			}
			versions.append(m.version);
		}
		return new EnsemblePadModel(() -> {
			java.io.File dir = store.materializePad();
			final MiniFasNetPad lib;
			try {
				lib = new MiniFasNetPad(dir.getPath(), store.getPadMinFaceSize(), store.getPadIntraOpThreads());
			} catch (PadException | IllegalArgumentException e) {
				throw new LivenessEngine.EngineException("PAD models failed to load", e, true);
			}
			return new EnsemblePadModel.Scorer() {
				@Override
				public double realScore(byte[] bgr, int width, int height, double[] box) {
					return lib.predict(bgr, width, height, box).realScore;
				}

				@Override
				public void close() {
					lib.close();
				}
			};
		}, "minifasnet-ensemble:" + versions);
	}
}

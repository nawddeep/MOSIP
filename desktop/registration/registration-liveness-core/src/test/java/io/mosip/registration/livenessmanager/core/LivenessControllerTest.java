package io.mosip.registration.livenessmanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.mosip.registration.livenessmanager.FakeClock;
import io.mosip.registration.livenessmanager.RecordingAudit;
import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.FaceFrameSource;
import io.mosip.registration.livenessmanager.api.LivenessConfigProvider;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.PreviewSink;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.QualityResult;
import io.mosip.registration.livenessmanager.model.Workflow;

public class LivenessControllerTest {

    static final class FakeSource implements FaceFrameSource {
        Listener listener;
        boolean stopped;

        @Override
        public SourceType getType() {
            return SourceType.MOCK_VIDEO;
        }

        @Override
        public void start(PreviewSink previewSink, Listener l) {
            listener = l;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    static final class FakeDevice implements FaceCaptureDevice {
        boolean present = true;
        boolean failing;
        int connects;
        int disconnects;
        FakeSource source;

        @Override
        public String getDeviceId() {
            return "fake";
        }

        @Override
        public DeviceLevel getLevel() {
            return DeviceLevel.L0;
        }

        @Override
        public ConnectionStatus getStatus() {
            return ConnectionStatus.CONNECTED;
        }

        @Override
        public FaceFrameSource connect() throws DeviceException {
            connects++;
            if (!present) throw new DeviceException("absent", true, null);
            if (failing) throw new DeviceException("broken", false, null);
            source = new FakeSource();
            return source;
        }

        @Override
        public void disconnect() {
            disconnects++;
        }
    }

    /** Engine that returns a face with a scripted live score. */
    static final class FakeEngine implements LivenessEngine {
        float score = 0.97f;
        boolean failInit;
        boolean failAnalyze;
        int closes;

        @Override
        public void initialize() throws EngineException {
            if (failInit) throw new EngineException("model hash mismatch", null, true);
        }

        @Override
        public FrameAnalysis analyze(FaceFrame frame, LivenessPolicy policy, boolean runPad) throws EngineException {
            if (failAnalyze) throw new EngineException("boom", null);
            return new FrameAnalysis(frame.getFrameId(), frame.getTimestampNanos(), false, 1,
                    SyntheticFace.live(), QualityResult.ok(120, 100, 0.4), runPad ? score : null);
        }

        @Override
        public String describe() {
            return "fake";
        }

        @Override
        public void close() {
            closes++;
        }
    }

    static final class StaticConfig implements LivenessConfigProvider {
        final LivenessConfig config = new LivenessConfig();

        StaticConfig() {
            for (Workflow w : Workflow.values()) {
                LivenessPolicy p = new LivenessPolicy();
                p.challengeTypes = new ArrayList<>(Collections.singletonList("BLINK"));
                config.policies.put(w.getConfigKey(), p);
            }
        }

        @Override
        public LivenessPolicy getPolicy(Workflow workflow) {
            return config.policyFor(workflow);
        }

        @Override
        public LivenessConfig getConfig() {
            return config;
        }

        @Override
        public boolean refresh(boolean online) {
            return false;
        }
    }

    private FakeDevice device;
    private FakeEngine engine;
    private StaticConfig config;
    private FakeClock clock;
    private LivenessGate gate;
    private LivenessController controller;
    private final List<LivenessEvent> events = new ArrayList<>();
    private Runnable tick;
    private long ts;

    @Before
    public void setUp() {
        device = new FakeDevice();
        engine = new FakeEngine();
        config = new StaticConfig();
        clock = new FakeClock();
        gate = new LivenessGate(clock);
        controller = new LivenessController(() -> device, engine, config, new RecordingAudit(), gate,
                Runnable::run, (t, period) -> {
                    tick = t;
                    return () -> tick = null;
                }, clock);
    }

    private FaceFrame frame() {
        ts += 33_000_000L;
        return new FaceFrame(ts, ts, 4, 4, new byte[64], false, null);
    }

    private void pump(int n) {
        for (int i = 0; i < n; i++) device.source.listener.onFrame(frame());
    }

    private LivenessState last() {
        return events.get(events.size() - 1).getState();
    }

    @Test
    public void successIssuesSingleUseGateAndReleasesCamera() {
        controller.start(Workflow.RESIDENT, null, events::add);
        assertEquals(LivenessState.POSITIONING, last());
        pump(10);
        assertEquals(LivenessState.SUCCESS, last());
        assertTrue(device.source.stopped);
        assertEquals(1, device.disconnects);
        assertTrue(gate.consume(Workflow.RESIDENT));
        assertFalse("single use", gate.consume(Workflow.RESIDENT));
    }

    @Test
    public void gateExpires() {
        controller.start(Workflow.OPERATOR, null, events::add);
        pump(10);
        clock.advance(61_000);
        assertFalse(gate.consume(Workflow.OPERATOR));
    }

    @Test
    public void missingDeviceShowsUnavailableThenRetryReconnects() {
        device.present = false;
        controller.start(Workflow.RESIDENT, null, events::add);
        assertEquals(LivenessState.DEVICE_UNAVAILABLE, last());
        device.present = true;
        controller.retry();
        assertEquals(LivenessState.POSITIONING, last());
        assertEquals(2, device.connects);
    }

    @Test
    public void failingDeviceIsCameraError() {
        device.failing = true;
        controller.start(Workflow.RESIDENT, null, events::add);
        assertEquals(LivenessState.CAMERA_ERROR, last());
    }

    @Test
    public void streamErrorIsCameraError() {
        controller.start(Workflow.RESIDENT, null, events::add);
        device.source.listener.onError(FaceFrameSource.Failure.STREAM_ERROR, "disconnected");
        assertEquals(LivenessState.CAMERA_ERROR, last());
        assertTrue(device.source.stopped);
    }

    @Test
    public void engineInitFailureFailsClosed() {
        engine.failInit = true;
        controller.start(Workflow.RESIDENT, null, events::add);
        assertEquals(LivenessState.ENGINE_ERROR, last());
        assertFalse(gate.isValid(Workflow.RESIDENT));
    }

    @Test
    public void inferenceFailureIsEngineError() {
        controller.start(Workflow.RESIDENT, null, events::add);
        engine.failAnalyze = true;
        pump(1);
        assertEquals(LivenessState.ENGINE_ERROR, last());
    }

    @Test
    public void invalidFramesAreDetected() {
        controller.start(Workflow.RESIDENT, null, events::add);
        for (int i = 0; i < LivenessSession.MAX_INVALID_FRAME_STREAK; i++) {
            device.source.listener.onFrame(new FaceFrame(i, 1, 4, 4, new byte[3], false, null));
        }
        assertEquals(LivenessState.INVALID_FRAMES, last());
    }

    @Test
    public void outOfOrderTimestampsAreInvalid() {
        controller.start(Workflow.RESIDENT, null, events::add);
        for (int i = 0; i < LivenessSession.MAX_INVALID_FRAME_STREAK + 1; i++) {
            device.source.listener.onFrame(new FaceFrame(i, 5, 4, 4, new byte[64], false, null));
        }
        assertEquals(LivenessState.INVALID_FRAMES, last());
    }

    @Test
    public void attemptFailureRetryKeepsCameraAndCountsAttempt() {
        engine.score = 0.05f;
        controller.start(Workflow.RESIDENT, null, events::add);
        pump(10);
        assertEquals(LivenessState.PAD_FAILED, last());
        assertFalse(device.source.stopped);
        controller.retry();
        assertEquals(LivenessState.POSITIONING, last());
        assertEquals(2, events.get(events.size() - 1).getAttempt());
        assertEquals(1, device.connects);
    }

    @Test
    public void challengeTimeoutDrivenByTicker() {
        engine.score = 0.6f;
        controller.start(Workflow.RESIDENT, null, events::add);
        pump(10);
        assertEquals(LivenessState.CHALLENGE, last());
        clock.advance(9_000);
        tick.run();
        assertEquals(LivenessState.CHALLENGE_TIMEOUT, last());
    }

    @Test
    public void disabledPolicySucceedsImmediatelyWithoutCamera() {
        config.config.policies.get("supervisor").enabled = false;
        controller.start(Workflow.SUPERVISOR, null, events::add);
        assertEquals(LivenessState.SUCCESS, last());
        assertEquals(0, device.connects);
        assertTrue(gate.consume(Workflow.SUPERVISOR));
    }

    @Test
    public void cancelReleasesCameraAndRevokesGate() {
        controller.start(Workflow.RESIDENT, null, events::add);
        controller.cancel();
        assertEquals(LivenessState.IDLE, last());
        assertTrue(device.source.stopped);
        assertFalse(gate.isValid(Workflow.RESIDENT));
    }

    @Test
    public void frameSkipAnalysesEveryNthFrame() {
        config.config.performance.frameSkip = 2;
        controller.start(Workflow.RESIDENT, null, events::add);
        pump(19);
        assertEquals(LivenessState.SUCCESS, last());
    }

    @Test
    public void releaseClosesEngine() {
        controller.start(Workflow.RESIDENT, null, events::add);
        controller.release();
        assertEquals(1, engine.closes);
    }

    @Test
    public void throwingAuditSinkDoesNotBreakSession() {
        controller = new LivenessController(() -> device, engine, config, record -> {
            throw new IllegalStateException("db full");
        }, gate, Runnable::run, (t, period) -> {
            tick = t;
            return () -> tick = null;
        }, clock);
        controller.start(Workflow.RESIDENT, null, events::add);
        pump(10);
        assertEquals(LivenessState.SUCCESS, last());
    }

    @Test
    public void sessionLogicErrorFailsClosedToEngineError() {
        controller.start(Workflow.RESIDENT, null, e -> {
            if (e.getState() == LivenessState.PASSIVE_CHECK) throw new IllegalStateException("listener bug");
            events.add(e);
        });
        pump(10);
        assertEquals(LivenessState.ENGINE_ERROR, last());
        assertFalse(gate.isValid(Workflow.RESIDENT));
    }
}

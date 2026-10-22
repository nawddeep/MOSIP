/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.core;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import io.mosip.registration.livenessmanager.api.ChallengeManager;
import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.FaceFrameSource;
import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.LivenessConfigProvider;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.LivenessEventListener;
import io.mosip.registration.livenessmanager.api.PreviewSink;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.challenge.ChallengeDetectorFactory;
import io.mosip.registration.livenessmanager.challenge.RandomChallengeManager;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessConfig;
import io.mosip.registration.livenessmanager.model.LivenessErrorCode;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * Wires device, engine and session together. All session mutations are serialised on this object's
 * monitor. Listener callbacks happen while holding it, so the listener must hand events off
 * asynchronously (the Android bridge posts them to the main thread).
 * <p>
 * Threads: {@code background} connects devices and loads models; frames arrive on the source's own
 * thread and inference runs there (one frame in flight, extra frames are dropped by the source).
 */
public final class LivenessController {

    public interface DeviceProvider {
        FaceCaptureDevice get();
    }

    public interface Ticker {
        /** Starts calling {@code tick} every {@code periodMillis}. @return handle that stops it */
        Runnable start(Runnable tick, long periodMillis);
    }

    public interface ChallengeManagerFactory {
        ChallengeManager create();
    }

    static final long TICK_MILLIS = 200;

    private final DeviceProvider devices;
    private final LivenessEngine engine;
    private final LivenessConfigProvider config;
    private final LivenessAuditLogger audit;
    private final LivenessGate gate;
    private final Executor background;
    private final Ticker ticker;
    private final LivenessSession.Clock clock;
    private final ChallengeManagerFactory challengeManagers;
    private final ChallengeDetectorFactory detectors;
    private final ReentrantLock engineLock = new ReentrantLock();

    private LivenessSession session;
    private LivenessPolicy policy;
    private LivenessConfig.Performance performance;
    private LivenessEventListener listener;
    private PreviewSink previewSink;
    private FaceCaptureDevice device;
    private FaceFrameSource source;
    private Runnable stopTicker;
    private boolean engineReady;
    private long generation;
    private long frameCounter;
    private long analysedCounter;
    private long lastTimestamp;
    private final PerfStats perf = new PerfStats();

    public LivenessController(DeviceProvider devices, LivenessEngine engine, LivenessConfigProvider config,
                              LivenessAuditLogger audit, LivenessGate gate, Executor background, Ticker ticker,
                              LivenessSession.Clock clock) {
        this(devices, engine, config, audit, gate, background, ticker, clock,
                RandomChallengeManager::new, ChallengeDetectorFactory.DEFAULT);
    }

    public LivenessController(DeviceProvider devices, LivenessEngine engine, LivenessConfigProvider config,
                              LivenessAuditLogger audit, LivenessGate gate, Executor background, Ticker ticker,
                              LivenessSession.Clock clock, ChallengeManagerFactory challengeManagers,
                              ChallengeDetectorFactory detectors) {
        this.devices = devices;
        this.engine = engine;
        this.config = config;
        this.audit = safe(audit);
        this.gate = gate;
        this.background = background;
        this.ticker = ticker;
        this.clock = clock;
        this.challengeManagers = challengeManagers;
        this.detectors = detectors;
    }

    /** Auditing must never break or crash a session. */
    private static LivenessAuditLogger safe(LivenessAuditLogger delegate) {
        return record -> {
            try {
                delegate.log(record);
            } catch (RuntimeException ignored) {
                // audit sink failure is not a liveness failure
            }
        };
    }

    public boolean isEnabled(Workflow workflow) {
        return config.getPolicy(workflow).enabled;
    }

    /** Max attempts for the workflow (the only policy value the UI may know, for "Attempt n of N"). */
    public int getMaxAttempts(Workflow workflow) {
        return Math.max(1, config.getPolicy(workflow).maxRetries);
    }

    public synchronized LivenessState getState() {
        return session == null ? LivenessState.IDLE : session.getState();
    }

    public synchronized void start(Workflow workflow, PreviewSink sink, LivenessEventListener eventListener) {
        if (session != null && session.getState() != LivenessState.IDLE) {
            session.cancel();
        }
        stopStreaming();
        gate.revoke(workflow);
        generation++;
        listener = eventListener;
        previewSink = sink;
        policy = config.getPolicy(workflow);
        performance = config.getConfig().performance != null ? config.getConfig().performance : new LivenessConfig.Performance();
        session = new LivenessSession(UUID.randomUUID().toString(), workflow, policy,
                challengeManagers.create(), detectors, this::onSessionEvent, audit, clock);
        if (!policy.enabled) {
            audit.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_ENDED, clock.nowMillis())
                    .session(session.getSessionId(), workflow, 1)
                    .outcome(LivenessAuditRecord.Outcome.PASS).detail("reason", "disabled-by-policy").build());
            gate.issue(workflow, ttlMillis());
            eventListener.onEvent(new LivenessEvent(LivenessState.SUCCESS, workflow, 1, 1, null, null, null));
            return;
        }
        connectAsync(generation, false);
    }

    /** "Try again" after a failure, or "Retry connection" after a device/camera/engine error. */
    public synchronized void retry() {
        if (session == null || !session.getState().isRetryable()) {
            return;
        }
        if (session.getState().getKind() == LivenessState.Kind.SYSTEM_ERROR || source == null) {
            generation++;
            stopStreaming();
            connectAsync(generation, true);
        } else {
            resetFrameCounters();
            session.retry();
        }
    }

    public synchronized void cancel() {
        if (session != null) {
            session.cancel();
            gate.revoke(session.getWorkflow());
        }
        generation++;
        stopStreaming();
    }

    /** Releases camera and models. Call when the hosting activity is destroyed. */
    public void release() {
        synchronized (this) {
            cancel();
            session = null;
        }
        engineLock.lock();
        try {
            engine.close();
            engineReady = false;
        } finally {
            engineLock.unlock();
        }
    }

    public LivenessGate getGate() {
        return gate;
    }

    // ------------------------------------------------------------------ internals

    private void connectAsync(long gen, boolean isRetry) {
        final LivenessSession s = session;
        background.execute(() -> {
            engineLock.lock();
            try {
                if (!engineReady) {
                    engine.initialize();
                    engineReady = true;
                }
            } catch (LivenessEngine.EngineException e) {
                failSystem(gen, LivenessState.ENGINE_ERROR,
                        e.isIntegrityFailure() ? LivenessErrorCode.MODEL_INTEGRITY_FAILED : LivenessErrorCode.ENGINE_INIT_FAILED,
                        e.getMessage());
                return;
            } catch (RuntimeException | LinkageError e) {
                failSystem(gen, LivenessState.ENGINE_ERROR, LivenessErrorCode.ENGINE_INIT_FAILED,
                        e.getClass().getSimpleName());
                return;
            } finally {
                engineLock.unlock();
            }

            FaceCaptureDevice d;
            FaceFrameSource src;
            try {
                d = devices.get();
                if (d == null) {
                    throw new FaceCaptureDevice.DeviceException("No device configured", true, null);
                }
                src = d.connect();
            } catch (FaceCaptureDevice.DeviceException e) {
                failSystem(gen, e.isNotPresent() ? LivenessState.DEVICE_UNAVAILABLE : LivenessState.CAMERA_ERROR,
                        e.isNotPresent() ? LivenessErrorCode.DEVICE_NOT_CONNECTED : LivenessErrorCode.DEVICE_CONNECT_FAILED,
                        e.getMessage());
                return;
            } catch (RuntimeException e) {
                failSystem(gen, LivenessState.CAMERA_ERROR, LivenessErrorCode.CAMERA_FAILURE, e.getClass().getSimpleName());
                return;
            }

            synchronized (LivenessController.this) {
                if (gen != generation || session != s) {
                    d.disconnect();
                    return;
                }
                device = d;
                source = src;
                resetFrameCounters();
                src.start(previewSink, new FrameListener(gen));
                stopTicker = ticker.start(this::onTick, TICK_MILLIS);
                if (isRetry) {
                    s.retry();
                } else {
                    s.start();
                }
            }
        });
    }

    private synchronized void failSystem(long gen, LivenessState state, LivenessErrorCode code, String detail) {
        if (gen != generation || session == null) {
            return;
        }
        stopStreaming();
        session.onSystemError(state, code, detail);
    }

    private synchronized void onTick() {
        if (session != null) {
            try {
                session.onTick();
            } catch (RuntimeException e) {
                failSystem(generation, LivenessState.ENGINE_ERROR, LivenessErrorCode.ENGINE_INFERENCE_FAILED, "tick error");
            }
        }
    }

    private void onSessionEvent(LivenessEvent event) {
        LivenessState s = event.getState();
        if (s == LivenessState.SUCCESS) {
            // Release the camera before the UI moves on: the SBI/MDS app needs it next.
            stopStreaming();
            gate.issue(event.getWorkflow(), ttlMillis());
        } else if (s.isTerminal() || s == LivenessState.IDLE || s.getKind() == LivenessState.Kind.SYSTEM_ERROR) {
            stopStreaming();
        }
        if (listener != null) {
            listener.onEvent(event);
        }
    }

    private long ttlMillis() {
        return config.getConfig().livenessTokenTtlSeconds * 1000L;
    }

    private void resetFrameCounters() {
        frameCounter = 0;
        analysedCounter = 0;
        lastTimestamp = Long.MIN_VALUE;
    }

    private void stopStreaming() {
        flushPerf();
        if (stopTicker != null) {
            stopTicker.run();
            stopTicker = null;
        }
        if (source != null) {
            source.stop();
            source = null;
        }
        if (device != null) {
            device.disconnect();
            device = null;
        }
    }

    private void flushPerf() {
        PerfStats.Snapshot snap = perf.snapshotAndReset();
        if (snap == null || session == null) return;
        audit.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.PERFORMANCE, clock.nowMillis())
                .session(session.getSessionId(), session.getWorkflow(), session.getAttempt())
                .detail("framesAnalysed", String.valueOf(snap.count))
                .detail("framesWithPad", String.valueOf(snap.padCount))
                .detail("meanMs", snap.meanMs).detail("p95Ms", snap.p95Ms).detail("maxMs", snap.maxMs)
                .detail("engine", engine.describe()).build());
    }

    private final class FrameListener implements FaceFrameSource.Listener {
        private final long gen;

        FrameListener(long gen) {
            this.gen = gen;
        }

        @Override
        public void onFrame(FaceFrame frame) {
            final LivenessSession s;
            final boolean runPad;
            final LivenessPolicy p;
            synchronized (LivenessController.this) {
                if (gen != generation || session == null || !session.getState().isRunning()) {
                    return;
                }
                frameCounter++;
                int skip = Math.max(1, performance.frameSkip);
                if ((frameCounter - 1) % skip != 0) {
                    return;
                }
                if (frame == null || !frame.isValid() || frame.getTimestampNanos() <= lastTimestamp) {
                    session.onInvalidFrame();
                    return;
                }
                lastTimestamp = frame.getTimestampNanos();
                runPad = analysedCounter++ % Math.max(1, performance.padEveryNthFrame) == 0;
                s = session;
                p = policy;
            }
            FrameAnalysis analysis;
            engineLock.lock();
            try {
                if (!engineReady) return;
                long t0 = System.nanoTime();
                analysis = engine.analyze(frame, p, runPad);
                perf.add(System.nanoTime() - t0, runPad);
            } catch (LivenessEngine.EngineException e) {
                failSystem(gen, LivenessState.ENGINE_ERROR,
                        e.isIntegrityFailure() ? LivenessErrorCode.MODEL_INTEGRITY_FAILED : LivenessErrorCode.ENGINE_INFERENCE_FAILED,
                        e.getMessage());
                return;
            } catch (RuntimeException e) {
                failSystem(gen, LivenessState.ENGINE_ERROR, LivenessErrorCode.ENGINE_INFERENCE_FAILED,
                        e.getClass().getSimpleName());
                return;
            } finally {
                engineLock.unlock();
            }
            boolean failed = false;
            synchronized (LivenessController.this) {
                if (gen == generation && session == s) {
                    try {
                        s.onAnalysis(analysis);
                    } catch (RuntimeException e) {
                        failed = true;
                    }
                }
            }
            if (failed) {
                // Never let a logic error escape onto the camera thread; fail closed instead.
                failSystem(gen, LivenessState.ENGINE_ERROR, LivenessErrorCode.ENGINE_INFERENCE_FAILED, "session error");
            }
        }

        @Override
        public void onError(FaceFrameSource.Failure failure, String detail) {
            failSystem(gen, failure == FaceFrameSource.Failure.UNAVAILABLE
                            ? LivenessState.DEVICE_UNAVAILABLE : LivenessState.CAMERA_ERROR,
                    failure == FaceFrameSource.Failure.UNAVAILABLE
                            ? LivenessErrorCode.DEVICE_NOT_CONNECTED : LivenessErrorCode.CAMERA_FAILURE,
                    detail);
        }
    }
}

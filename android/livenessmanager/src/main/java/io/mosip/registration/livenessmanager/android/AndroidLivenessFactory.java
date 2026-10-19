/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.content.Context;

import org.opencv.android.OpenCVLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.LivenessConfigProvider;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.core.LivenessController;
import io.mosip.registration.livenessmanager.core.LivenessGate;
import io.mosip.registration.livenessmanager.core.LivenessSession;
import io.mosip.registration.livenessmanager.engine.DefaultLivenessEngine;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;

/** Builds the Android engine and controller with production threading. */
public final class AndroidLivenessFactory {
    public static final String BUNDLED_CONFIG_ASSET = "liveness/default_liveness_config.json";

    private AndroidLivenessFactory() {
    }

    public static String readBundledConfig(Context context) {
        try (InputStream in = context.getAssets().open(BUNDLED_CONFIG_ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Bundled liveness config missing", e);
        }
    }

    /** MediaPipe + MiniFASNet + OpenCV engine. Native libraries and models load in initialize(). */
    public static LivenessEngine createEngine(Context context) {
        return new LazyEngine(context.getApplicationContext());
    }

    public static LivenessController createController(Context context, LivenessController.DeviceProvider devices,
                                                      LivenessConfigProvider config, LivenessAuditLogger audit,
                                                      LivenessGate gate) {
        ExecutorService background = Executors.newSingleThreadExecutor(r -> new Thread(r, "liveness-connect"));
        ScheduledExecutorService tickerExec = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "liveness-ticker"));
        LivenessController.Ticker ticker = (tick, period) -> {
            ScheduledFuture<?> f = tickerExec.scheduleAtFixedRate(() -> {
                try {
                    tick.run();
                } catch (RuntimeException ignored) {
                    // never kill the ticker thread
                }
            }, period, period, TimeUnit.MILLISECONDS);
            return () -> f.cancel(false);
        };
        return new LivenessController(devices, createEngine(context), config, audit, gate,
                background, ticker, systemClock());
    }

    public static LivenessSession.Clock systemClock() {
        return System::currentTimeMillis;
    }

    static final class LazyEngine implements LivenessEngine {
        private final Context context;
        private DefaultLivenessEngine delegate;

        LazyEngine(Context context) {
            this.context = context;
        }

        @Override
        public void initialize() throws EngineException {
            if (delegate != null) return;
            boolean loaded;
            try {
                loaded = OpenCVLoader.initLocal();
            } catch (Throwable t) {
                loaded = false;
            }
            if (!loaded) throw new EngineException("OpenCV native library failed to load", null);
            ModelManifest manifest = ModelManifest.load(context.getAssets());
            OpenCvFrameCache cache = new OpenCvFrameCache();
            DefaultLivenessEngine engine = new DefaultLivenessEngine(
                    new MediaPipeFaceDetector(context, manifest.faceLandmarker),
                    MiniFasNetModels.createPadModel(context, manifest.pad),
                    new OpenCvQualityAnalyzer(cache));
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
            if (delegate == null) throw new EngineException("Engine not initialized", null);
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
}

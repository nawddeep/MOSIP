/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration_client.api_services;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.TextureRegistry;
import io.mosip.registration.clientmanager.repository.GlobalParamRepository;
import io.mosip.registration.clientmanager.spi.AuditManagerService;
import io.mosip.registration.livenessmanager.android.AndroidLivenessFactory;
import io.mosip.registration.livenessmanager.android.CameraDeviceAdapter;
import io.mosip.registration.livenessmanager.android.FileConfigCache;
import io.mosip.registration.livenessmanager.android.MockDeviceAdapter;
import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.PreviewSink;
import io.mosip.registration.livenessmanager.config.CachedLivenessConfigProvider;
import io.mosip.registration.livenessmanager.core.LivenessController;
import io.mosip.registration.livenessmanager.core.LivenessGate;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.Workflow;
import io.mosip.registration_client.BuildConfig;
import io.mosip.registration_client.liveness.AuditManagerLivenessLogger;
import io.mosip.registration_client.liveness.LivenessGuard;
import io.mosip.registration_client.utils.NetworkUtils;

/**
 * Flutter bridge for face liveness: a MethodChannel for commands, an EventChannel for state events,
 * and a Flutter texture for the camera preview. Only {@link LivenessEvent} maps cross to Dart, so no
 * score, threshold or model detail can reach the UI.
 */
@Singleton
public class FaceLivenessApi implements MethodChannel.MethodCallHandler, EventChannel.StreamHandler, LivenessGuard {
    private static final String TAG = "FaceLivenessApi";
    public static final String METHOD_CHANNEL = "io.mosip.registration_client/face_liveness";
    public static final String EVENT_CHANNEL = "io.mosip.registration_client/face_liveness/events";
    static final String MOCK_DIR = "liveness_mock";

    private final Context context;
    private final GlobalParamRepository globalParamRepository;
    private final LivenessAuditLogger auditLogger;
    private final LivenessGate gate = new LivenessGate(AndroidLivenessFactory.systemClock());
    private final Handler main = new Handler(Looper.getMainLooper());

    private CachedLivenessConfigProvider configProvider;
    private LivenessController controller;
    private LifecycleOwner lifecycleOwner;
    private TextureRegistry textures;
    private MethodChannel methodChannel;
    private EventChannel.EventSink eventSink;

    private TextureRegistry.SurfaceTextureEntry textureEntry;
    private Surface surface;

    private boolean useMock;
    private File mockMedia;
    private boolean mockMirrored;

    public FaceLivenessApi(Context context, GlobalParamRepository globalParamRepository,
                           AuditManagerService auditManagerService) {
        this.context = context.getApplicationContext();
        this.globalParamRepository = globalParamRepository;
        this.auditLogger = new AuditManagerLivenessLogger(auditManagerService);
    }

    /** Called from MainActivity.configureFlutterEngine. */
    public void setup(@NonNull FlutterEngine engine, @NonNull LifecycleOwner owner) {
        this.lifecycleOwner = owner;
        this.textures = engine.getRenderer();
        methodChannel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), METHOD_CHANNEL);
        methodChannel.setMethodCallHandler(this);
        new EventChannel(engine.getDartExecutor().getBinaryMessenger(), EVENT_CHANNEL).setStreamHandler(this);
        mockMedia = new File(context.getExternalFilesDir(MOCK_DIR), "sample.mp4");
    }

    private synchronized CachedLivenessConfigProvider config() {
        if (configProvider == null) {
            configProvider = new CachedLivenessConfigProvider(
                    () -> globalParamRepository.getGlobalParamValue(CachedLivenessConfigProvider.GLOBAL_PARAM_KEY),
                    new FileConfigCache(new File(context.getFilesDir(), "liveness")),
                    AndroidLivenessFactory.readBundledConfig(context),
                    auditLogger, System::currentTimeMillis);
        }
        return configProvider;
    }

    private synchronized LivenessController controller() {
        if (controller == null) {
            controller = AndroidLivenessFactory.createController(context, this::currentDevice, config(), auditLogger, gate);
        }
        return controller;
    }

    private FaceCaptureDevice currentDevice() {
        if (useMock && BuildConfig.DEBUG) {
            return new MockDeviceAdapter(mockMedia, mockMirrored, 15);
        }
        return new CameraDeviceAdapter(context, lifecycleOwner);
    }

    // ------------------------------------------------------------------ MethodChannel

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        try {
            switch (call.method) {
                case "isEnabled":
                    result.success(controller().isEnabled(workflowArg(call)));
                    break;
                case "getMaxAttempts":
                    result.success(controller().getMaxAttempts(workflowArg(call)));
                    break;
                case "start":
                    start(workflowArg(call), result);
                    break;
                case "retry":
                    controller().retry();
                    result.success(null);
                    break;
                case "cancel":
                    cancel();
                    result.success(null);
                    break;
                case "setSource":
                    result.success(setSource(call));
                    break;
                default:
                    result.notImplemented();
            }
        } catch (IllegalArgumentException e) {
            result.error("LIVENESS_BAD_ARGUMENT", "Invalid argument", null);
        } catch (RuntimeException e) {
            Log.e(TAG, "liveness call failed: " + e.getClass().getSimpleName());
            result.error("LIVENESS_ERROR", "Liveness unavailable", null);
        }
    }

    private Workflow workflowArg(MethodCall call) {
        String w = call.argument("workflow");
        if (w == null) throw new IllegalArgumentException("workflow");
        return Workflow.fromValue(w);
    }

    private void start(Workflow workflow, MethodChannel.Result result) {
        // Offline-first: re-read synced config only when the network is up; otherwise the cached copy is used.
        config().refresh(NetworkUtils.isNetworkConnected(context));
        if (textureEntry == null) {
            textureEntry = textures.createSurfaceTexture();
        }
        final TextureRegistry.SurfaceTextureEntry entry = textureEntry;
        PreviewSink sink = new PreviewSink() {
            @Override
            public Object acquireSurface(int width, int height) {
                synchronized (FaceLivenessApi.this) {
                    if (textureEntry != entry) return null;
                    SurfaceTexture st = entry.surfaceTexture();
                    st.setDefaultBufferSize(width, height);
                    if (surface == null) surface = new Surface(st);
                    return surface;
                }
            }

            @Override
            public void onPreviewTransform(int width, int height, int rotationDegrees, boolean mirror) {
                Map<String, Object> m = new HashMap<>();
                m.put("width", width);
                m.put("height", height);
                m.put("rotationDegrees", rotationDegrees);
                m.put("mirror", mirror);
                main.post(() -> {
                    if (methodChannel != null) methodChannel.invokeMethod("onPreviewTransform", m);
                });
            }
        };
        controller().start(workflow, sink, this::postEvent);
        Map<String, Object> reply = new HashMap<>();
        reply.put("textureId", entry.id());
        result.success(reply);
    }

    private void cancel() {
        controller().cancel();
        // CameraX unbinds on the main looper; release the texture after that has run.
        main.post(this::releaseTexture);
    }

    private synchronized void releaseTexture() {
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (textureEntry != null) {
            textureEntry.release();
            textureEntry = null;
        }
    }

    private boolean setSource(MethodCall call) {
        if (!BuildConfig.DEBUG) {
            return false; // mock input is never available in release builds
        }
        String source = call.argument("source");
        useMock = "mock".equals(source);
        String path = call.argument("path");
        if (path != null) {
            File f = new File(path);
            // Relative names resolve inside <external files>/liveness_mock/ (adb push target).
            mockMedia = f.isAbsolute() ? f : new File(context.getExternalFilesDir(MOCK_DIR), path);
        }
        Boolean mirrored = call.argument("mirrored");
        mockMirrored = mirrored != null && mirrored;
        return true;
    }

    // ------------------------------------------------------------------ EventChannel

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        eventSink = events;
    }

    @Override
    public void onCancel(Object arguments) {
        eventSink = null;
    }

    private void postEvent(LivenessEvent event) {
        final Map<String, Object> map = event.toMap();
        main.post(() -> {
            if (eventSink != null) eventSink.success(map);
        });
    }

    // ------------------------------------------------------------------ guard / lifecycle

    @Override
    public boolean consumePass(Workflow workflow) {
        if (!controller().isEnabled(workflow)) {
            return true;
        }
        return gate.consume(workflow);
    }

    public void release() {
        if (controller != null) controller.release();
        releaseTexture();
    }
}

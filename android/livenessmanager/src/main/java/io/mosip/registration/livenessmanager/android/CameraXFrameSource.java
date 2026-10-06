/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mosip.registration.livenessmanager.api.FaceFrameSource;
import io.mosip.registration.livenessmanager.api.PreviewSink;
import io.mosip.registration.livenessmanager.model.FaceFrame;

/**
 * Front camera through CameraX: a Preview use case rendered to the Flutter texture, plus an
 * ImageAnalysis use case (RGBA_8888, keep-only-latest) feeding the engine on a dedicated thread.
 * Keep-only-latest means frames that arrive while inference is busy are dropped, never queued.
 */
public final class CameraXFrameSource implements FaceFrameSource {
    private static final String TAG = "CameraXFrameSource";
    static final Size ANALYSIS_SIZE = new Size(640, 480);

    private final android.content.Context context;
    private final LifecycleOwner owner;
    private final ProcessCameraProvider provider;
    private final CameraSelector selector;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final byte[][] buffer = new byte[1][];

    private ExecutorService analysisExecutor;
    private Preview preview;
    private ImageAnalysis analysis;
    private Camera camera;
    private Observer<CameraState> stateObserver;
    private long frameId;

    CameraXFrameSource(android.content.Context context, LifecycleOwner owner, ProcessCameraProvider provider,
                       CameraSelector selector) {
        this.context = context.getApplicationContext();
        this.owner = owner;
        this.provider = provider;
        this.selector = selector;
    }

    @Override
    public SourceType getType() {
        return SourceType.CAMERA;
    }

    @Override
    public void start(PreviewSink sink, Listener listener) {
        if (!running.compareAndSet(false, true)) return;
        analysisExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "liveness-analysis"));
        main.post(() -> {
            if (!running.get()) return;
            try {
                preview = new Preview.Builder().setTargetResolution(new Size(480, 640)).build();
                preview.setSurfaceProvider(ContextCompat.getMainExecutor(context), request -> {
                    Size res = request.getResolution();
                    Surface surface = sink == null ? null : (Surface) sink.acquireSurface(res.getWidth(), res.getHeight());
                    if (surface == null) {
                        request.willNotProvideSurface();
                        return;
                    }
                    request.setTransformationInfoListener(ContextCompat.getMainExecutor(context), info ->
                            sink.onPreviewTransform(res.getWidth(), res.getHeight(), info.getRotationDegrees(), true));
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context), result -> { });
                });

                analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(ANALYSIS_SIZE)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, image -> analyze(image, listener));

                provider.unbindAll();
                camera = provider.bindToLifecycle(owner, selector, preview, analysis);
                stateObserver = state -> {
                    CameraState.StateError error = state.getError();
                    if (error != null && running.get()) {
                        boolean unavailable = error.getCode() == CameraState.ERROR_CAMERA_DISABLED
                                || error.getCode() == CameraState.ERROR_MAX_CAMERAS_IN_USE
                                || error.getCode() == CameraState.ERROR_CAMERA_IN_USE;
                        if (error.getType() == CameraState.ErrorType.CRITICAL || unavailable) {
                            listener.onError(unavailable ? Failure.UNAVAILABLE : Failure.STREAM_ERROR,
                                    "camera state error " + error.getCode());
                        }
                    }
                };
                camera.getCameraInfo().getCameraState().observe(owner, stateObserver);
            } catch (RuntimeException e) {
                Log.e(TAG, "bind failed: " + e.getClass().getSimpleName());
                listener.onError(Failure.STREAM_ERROR, "bind failed");
            }
        });
    }

    private void analyze(ImageProxy image, Listener listener) {
        try {
            if (!running.get()) return;
            Bitmap raw = image.toBitmap();
            Bitmap upright = AndroidFrames.upright(raw, image.getImageInfo().getRotationDegrees());
            FaceFrame frame = AndroidFrames.toFrame(++frameId, image.getImageInfo().getTimestamp(), upright, false, buffer);
            listener.onFrame(frame);
            if (upright != raw) upright.recycle();
            raw.recycle();
        } catch (RuntimeException e) {
            // Treated as an invalid frame by the controller (null frame).
            listener.onFrame(null);
        } finally {
            image.close();
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        main.post(() -> {
            if (camera != null && stateObserver != null) {
                camera.getCameraInfo().getCameraState().removeObserver(stateObserver);
            }
            if (analysis != null) analysis.clearAnalyzer();
            try {
                if (preview != null && analysis != null) provider.unbind(preview, analysis);
            } catch (RuntimeException ignored) {
                // provider already shut down
            }
            camera = null;
        });
        ExecutorService ex = analysisExecutor;
        if (ex != null) ex.shutdown();
    }
}

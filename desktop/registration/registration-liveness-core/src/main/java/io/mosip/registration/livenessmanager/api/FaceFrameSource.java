/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.model.FaceFrame;

/**
 * Produces upright face frames: the phone camera, a vendor device or a recorded video (mock).
 * <p>
 * Implementations deliver frames on their own background thread and must tolerate
 * {@link #stop()} being called from any thread. A source never logs or stores frames.
 */
public interface FaceFrameSource {

    enum SourceType { CAMERA, VENDOR_DEVICE, MOCK_VIDEO }

    /** Why a source stopped producing frames. */
    enum Failure {
        /** Device not present / not connected / permission missing. */
        UNAVAILABLE,
        /** Device present but failed while streaming. */
        STREAM_ERROR
    }

    interface Listener {
        /** Called per frame; the frame is only valid during the call unless the listener copies it. */
        void onFrame(FaceFrame frame);

        void onError(Failure failure, String detail);
    }

    SourceType getType();

    /**
     * Starts streaming. Frames are rendered to {@code previewSink} (may be null for headless tests)
     * and delivered to {@code listener}.
     */
    void start(PreviewSink previewSink, Listener listener);

    /**
     * Stops streaming and releases the camera/decoder. Idempotent, callable from any thread
     * (including from inside {@link Listener#onFrame}), and <b>must not block</b> waiting for an
     * in-flight frame callback to finish.
     */
    void stop();
}

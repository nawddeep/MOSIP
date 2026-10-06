/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

/**
 * Platform-neutral handle to the preview surface (on Android an android.view.Surface backed by a
 * Flutter texture). Kept as Object so the core library has no Android dependency.
 */
public interface PreviewSink {
    /** @return the platform surface sized to (width, height), or null if the UI is gone. */
    Object acquireSurface(int width, int height);

    /**
     * How the UI must transform the raw surface content to show an upright selfie view.
     * Camera surfaces arrive in sensor orientation; the mock source draws upright frames (rotation 0).
     */
    void onPreviewTransform(int width, int height, int rotationDegrees, boolean mirror);
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import java.io.File;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.FaceFrameSource;

/**
 * Mock device backed by a recorded video or image folder (see README "Mock device guide").
 * Reports "not present" when the media file is missing, which exercises DEVICE_UNAVAILABLE.
 */
public final class MockDeviceAdapter implements FaceCaptureDevice {
    private final File media;
    private final boolean mirrored;
    private final int fps;
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    public MockDeviceAdapter(File media, boolean mirrored, int fps) {
        this.media = media;
        this.mirrored = mirrored;
        this.fps = fps;
    }

    @Override
    public String getDeviceId() {
        return "mock:" + media.getName();
    }

    @Override
    public DeviceLevel getLevel() {
        return DeviceLevel.L0;
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    @Override
    public FaceFrameSource connect() throws DeviceException {
        if (!media.exists()) {
            status = ConnectionStatus.FAILED;
            throw new DeviceException("Mock media not found", true, null);
        }
        status = ConnectionStatus.CONNECTED;
        return new MockVideoFrameSource(media, mirrored, fps, true);
    }

    @Override
    public void disconnect() {
        status = ConnectionStatus.DISCONNECTED;
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

/**
 * Device adapter (L0 phone camera, L1 vendor device, mock). Separates connection lifecycle from
 * frame streaming so a vendor SDK can be swapped in without touching the engine or UI.
 */
public interface FaceCaptureDevice {

    enum DeviceLevel { L0, L1 }

    enum ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    String getDeviceId();

    DeviceLevel getLevel();

    ConnectionStatus getStatus();

    /**
     * Connects (opens/claims the device). Blocking; call off the main thread.
     *
     * @return a frame source bound to this device
     * @throws DeviceException when the device is missing or cannot be opened
     */
    FaceFrameSource connect() throws DeviceException;

    /** Releases the device. Idempotent and safe after a failed connect. */
    void disconnect();

    class DeviceException extends Exception {
        private final boolean notPresent;

        public DeviceException(String message, boolean notPresent, Throwable cause) {
            super(message, cause);
            this.notPresent = notPresent;
        }

        /** True when no device is attached (DEVICE_UNAVAILABLE) rather than a failing one (CAMERA_ERROR). */
        public boolean isNotPresent() {
            return notPresent;
        }
    }
}

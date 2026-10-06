/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.FaceFrameSource;

/**
 * L1 adapter skeleton for an external/secure face capture device. A vendor integrates by implementing
 * {@link VendorFaceSdk}; nothing else (engine, session, UI) changes. Without an SDK the adapter
 * reports "not present", so the UI shows the device-unavailable screen.
 * <p>
 * Note: MOSIP has no SBI call that streams frames for in-app liveness. A vendor SDK must provide
 * frames directly; see docs/liveness/00-discovery.md risk R1.
 */
public final class VendorDeviceAdapter implements FaceCaptureDevice {

    /** Contract a vendor SDK must fulfil. */
    public interface VendorFaceSdk {
        boolean isAttached();

        /** Opens the device and returns a frame source; throws on failure. */
        FaceFrameSource open() throws Exception;

        void close();

        String deviceId();
    }

    private final VendorFaceSdk sdk;
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    public VendorDeviceAdapter(VendorFaceSdk sdk) {
        this.sdk = sdk;
    }

    @Override
    public String getDeviceId() {
        return sdk == null ? "vendor:none" : "vendor:" + sdk.deviceId();
    }

    @Override
    public DeviceLevel getLevel() {
        return DeviceLevel.L1;
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    @Override
    public FaceFrameSource connect() throws DeviceException {
        if (sdk == null || !sdk.isAttached()) {
            status = ConnectionStatus.FAILED;
            throw new DeviceException("Vendor device not attached", true, null);
        }
        status = ConnectionStatus.CONNECTING;
        try {
            FaceFrameSource source = sdk.open();
            status = ConnectionStatus.CONNECTED;
            return source;
        } catch (Exception e) {
            status = ConnectionStatus.FAILED;
            safeClose();
            throw new DeviceException("Vendor device failed to open", false, e);
        }
    }

    @Override
    public void disconnect() {
        safeClose();
        status = ConnectionStatus.DISCONNECTED;
    }

    private void safeClose() {
        if (sdk == null) return;
        try {
            sdk.close();
        } catch (RuntimeException ignored) {
            // vendor close must not crash the app
        }
    }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.TimeUnit;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.FaceFrameSource;

/** L0 device: the phone/tablet front camera (falls back to the back camera on camera-less-front tablets). */
public final class CameraDeviceAdapter implements FaceCaptureDevice {
    private final Context context;
    private final LifecycleOwner owner;
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    public CameraDeviceAdapter(Context context, LifecycleOwner owner) {
        this.context = context.getApplicationContext();
        this.owner = owner;
    }

    @Override
    public String getDeviceId() {
        return "builtin-camera";
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
        status = ConnectionStatus.CONNECTING;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status = ConnectionStatus.FAILED;
            throw new DeviceException("Camera permission not granted", true, null);
        }
        try {
            ProcessCameraProvider provider = ProcessCameraProvider.getInstance(context).get(5, TimeUnit.SECONDS);
            CameraSelector selector;
            if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                selector = CameraSelector.DEFAULT_FRONT_CAMERA;
            } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                selector = CameraSelector.DEFAULT_BACK_CAMERA;
            } else {
                status = ConnectionStatus.FAILED;
                throw new DeviceException("No camera on this device", true, null);
            }
            status = ConnectionStatus.CONNECTED;
            return new CameraXFrameSource(context, owner, provider, selector);
        } catch (DeviceException e) {
            throw e;
        } catch (Exception e) {
            status = ConnectionStatus.FAILED;
            throw new DeviceException("Camera provider failed: " + e.getClass().getSimpleName(), false, e);
        }
    }

    @Override
    public void disconnect() {
        status = ConnectionStatus.DISCONNECTED;
    }
}

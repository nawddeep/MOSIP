package io.mosip.registration.liveness;

import java.util.function.Consumer;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;

/**
 * Where the liveness engine gets its frames: the SBI face preview that the client shows anyway.
 *
 * <p>
 * The implementation (in the JavaFX client, next to {@code Streamer}) joins the stream that is already running
 * for the capture screen, or starts the face preview itself for operator and supervisor logins. It must never
 * open a second stream while one is running.
 */
public interface SbiStreamTap {

	/**
	 * Starts delivering every JPEG of the SBI face preview to {@code jpegListener}.
	 *
	 * @throws FaceCaptureDevice.DeviceException
	 *             {@code notPresent=true} when no face device is available; otherwise the stream could not start
	 */
	void attach(Consumer<byte[]> jpegListener) throws FaceCaptureDevice.DeviceException;

	/** Stops delivering frames. A stream that this tap started itself is stopped; a joined one keeps running. */
	void detach();

	/** Certification level of the device, as far as it is known. */
	default FaceCaptureDevice.DeviceLevel level() {
		return FaceCaptureDevice.DeviceLevel.L0;
	}

	/** Identifier shown in logs, e.g. the SBI device id. */
	default String deviceId() {
		return "sbi-face";
	}
}

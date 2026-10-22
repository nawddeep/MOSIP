package io.mosip.registration.liveness;

import java.util.Objects;

import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.livenessmanager.api.FaceFrameSource;

/**
 * The SBI face device as the liveness controller sees it. {@link #connect()} joins the preview stream through an
 * {@link SbiStreamTap} and returns the frame source; {@link #disconnect()} only detaches from it, because the
 * capture screen (or the tap itself) owns the stream.
 */
public final class SbiFaceCaptureDevice implements FaceCaptureDevice {

	private final SbiStreamTap tap;
	private SbiStreamFrameSource source;
	private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;

	public SbiFaceCaptureDevice(SbiStreamTap tap) {
		this.tap = Objects.requireNonNull(tap, "tap");
	}

	@Override
	public String getDeviceId() {
		return tap.deviceId();
	}

	@Override
	public DeviceLevel getLevel() {
		return tap.level();
	}

	@Override
	public ConnectionStatus getStatus() {
		return status;
	}

	@Override
	public synchronized FaceFrameSource connect() throws DeviceException {
		status = ConnectionStatus.CONNECTING;
		SbiStreamFrameSource s = new SbiStreamFrameSource();
		try {
			tap.attach(s::offerJpeg);
		} catch (DeviceException | RuntimeException e) {
			status = ConnectionStatus.FAILED;
			if (e instanceof DeviceException) {
				throw (DeviceException) e;
			}
			throw new DeviceException("SBI face preview could not be started", false, e);
		}
		source = s;
		status = ConnectionStatus.CONNECTED;
		return s;
	}

	@Override
	public synchronized void disconnect() {
		try {
			tap.detach();
		} catch (RuntimeException ignored) {
			// The stream is being torn down anyway.
		}
		if (source != null) {
			source.stop();
			source = null;
		}
		status = ConnectionStatus.DISCONNECTED;
	}
}

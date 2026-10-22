package io.mosip.registration.controller.liveness;

import java.io.InputStream;
import java.util.function.Consumer;

import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.device.Streamer;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.liveness.SbiStreamTap;
import io.mosip.registration.livenessmanager.api.FaceCaptureDevice;
import io.mosip.registration.service.bio.BioService;
import javafx.scene.image.ImageView;

/**
 * Connects the liveness engine to the SBI face preview through the client's existing {@link Streamer}: the
 * preview is drawn into the liveness screen's {@link ImageView} by {@code Streamer} as always, and every JPEG
 * is also handed to the engine through the frame-listener hook. There is never a second stream: starting this
 * one closes whichever stream {@code Streamer} had before.
 *
 * <p>
 * While the preview runs, the idle auto-logout is suspended (as the capture screens do) and restored on detach.
 */
final class StreamerSbiTap implements SbiStreamTap {

	private final Streamer streamer;
	private final BioService bioService;
	private final ImageView preview;
	private boolean autoLogoutSuspended;

	StreamerSbiTap(Streamer streamer, BioService bioService, ImageView preview) {
		this.streamer = streamer;
		this.bioService = bioService;
		this.preview = preview;
	}

	@Override
	public synchronized void attach(Consumer<byte[]> jpegListener) throws FaceCaptureDevice.DeviceException {
		InputStream stream;
		try {
			stream = bioService.getStream(RegistrationConstants.FACE_FULLFACE);
		} catch (RegBaseCheckedException e) {
			boolean missing = RegistrationExceptionConstants.MDS_BIODEVICE_NOT_FOUND.getErrorCode()
					.equals(e.getErrorCode());
			throw new FaceCaptureDevice.DeviceException(
					missing ? "No face device found" : "The face preview could not be started", missing, e);
		} catch (RuntimeException e) {
			throw new FaceCaptureDevice.DeviceException("The face preview could not be started", false, e);
		}
		if (stream == null) {
			throw new FaceCaptureDevice.DeviceException("The face device returned no stream", true, null);
		}
		suspendAutoLogout(true);
		streamer.setFrameListener(jpegListener);
		streamer.startStream(stream, preview, null);
	}

	@Override
	public synchronized void detach() {
		streamer.setFrameListener(null);
		streamer.setUrlStream(null);
		suspendAutoLogout(false);
	}

	private void suspendAutoLogout(boolean suspend) {
		try {
			if (suspend) {
				SessionContext.setAutoLogout(false);
				autoLogoutSuspended = true;
			} else if (autoLogoutSuspended) {
				SessionContext.setAutoLogout(true);
				autoLogoutSuspended = false;
			}
		} catch (RuntimeException ignored) {
			// No session yet (e.g. first login): nothing to suspend.
		}
	}
}

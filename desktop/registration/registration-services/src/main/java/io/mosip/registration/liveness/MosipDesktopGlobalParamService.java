package io.mosip.registration.liveness;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.registration.entity.GlobalParam;
import io.mosip.registration.livenessmanager.desktop.DesktopGlobalParamService;
import io.mosip.registration.service.config.GlobalParamService;

/**
 * Reads the synced liveness configuration ({@code mosip.registration.face.liveness.config}) from
 * {@code reg.global_param} through MOSIP's {@link GlobalParamService}. No SQL, no new table.
 *
 * <p>
 * The row is addressed by {@code code} + English {@code lang_code} (what {@code GlobalParamService} uses for
 * every other client parameter). A row marked {@code is_deleted} counts as absent, so the config adapter falls
 * back to the last valid cached config and then to the bundled default.
 *
 * <p>
 * {@code global_param.val} is {@code VARCHAR(512)}, so a synced config must be compact: list only the fields
 * that differ from the defaults and keep the integrity block (see docs/liveness/10-desktop-derby-integration.md).
 */
@Component
public class MosipDesktopGlobalParamService implements DesktopGlobalParamService {

	private static final Logger LOGGER = Logger.getLogger(MosipDesktopGlobalParamService.class.getName());

	private final GlobalParamService globalParamService;

	@Autowired
	public MosipDesktopGlobalParamService(GlobalParamService globalParamService) {
		this.globalParamService = Objects.requireNonNull(globalParamService, "globalParamService");
	}

	@Override
	public String getGlobalParam(String code) {
		try {
			GlobalParam param = globalParamService.getGlobalParam(code);
			if (param == null || Boolean.TRUE.equals(param.getIsDeleted())) {
				return null;
			}
			String value = param.getVal();
			return value == null || value.trim().isEmpty() ? null : value;
		} catch (RuntimeException e) {
			// Treated as "not synced": the caller falls back to the cached / bundled config.
			LOGGER.log(Level.WARNING, "Liveness config parameter could not be read: " + e.getClass().getSimpleName());
			return null;
		}
	}
}

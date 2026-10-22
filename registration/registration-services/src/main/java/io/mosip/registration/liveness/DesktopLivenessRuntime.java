package io.mosip.registration.liveness;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.registration.livenessmanager.api.LivenessAuditLogger;
import io.mosip.registration.livenessmanager.api.LivenessConfigProvider;
import io.mosip.registration.livenessmanager.core.LivenessGate;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditManagerService;
import io.mosip.registration.livenessmanager.desktop.DesktopGlobalParamService;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessAuditLogger;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessConfigAdapter;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessGate;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * One per client process: the liveness configuration, audit logger and single-use gate shared by the resident,
 * operator and supervisor flows.
 *
 * <p>
 * Everything is built lazily on first use because the database is not ready when Spring creates beans.
 * The cache of the last valid synced config lives in {@code <user.dir>/.liveness/config} (next to the client's
 * existing {@code .metrics} folder). Only configuration is stored there, never frames.
 *
 * <p>
 * Fails closed: if a policy cannot be read at all, the workflow is treated as enabled, so capture / login /
 * approval require a pass instead of silently skipping liveness.
 */
@Component
public class DesktopLivenessRuntime {

	/** Folder (under {@code user.dir}) for the liveness config cache and extracted model files. */
	public static final String DATA_DIRECTORY = ".liveness";

	private static final Logger LOGGER = Logger.getLogger(DesktopLivenessRuntime.class.getName());

	private final DesktopGlobalParamService globalParams;
	private final DesktopAuditManagerService audit;
	private final File dataDirectory;

	private DesktopLivenessAuditLogger auditLogger;
	private DesktopLivenessConfigAdapter config;
	private DesktopLivenessGate gate;

	@Autowired
	public DesktopLivenessRuntime(MosipDesktopGlobalParamService globalParams, MosipDesktopAuditManagerService audit) {
		this(globalParams, audit, new File(System.getProperty("user.dir"), DATA_DIRECTORY));
	}

	/** Explicit collaborators and data folder (used by tests and by callers that manage their own paths). */
	public DesktopLivenessRuntime(DesktopGlobalParamService globalParams, DesktopAuditManagerService audit,
			File dataDirectory) {
		this.globalParams = Objects.requireNonNull(globalParams, "globalParams");
		this.audit = Objects.requireNonNull(audit, "audit");
		this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
	}

	/** Folder for the config cache and the verified model files. */
	public File getDataDirectory() {
		return dataDirectory;
	}

	public synchronized LivenessAuditLogger getAuditLogger() {
		init();
		return auditLogger;
	}

	public synchronized LivenessConfigProvider getConfig() {
		init();
		return config;
	}

	/** The core gate (the one {@code LivenessController} issues passes on). */
	public synchronized LivenessGate getGate() {
		init();
		return gate.getGate();
	}

	/** Is liveness required for this workflow? Fails closed (true) when the policy cannot be read. */
	public boolean isEnabled(Workflow workflow) {
		try {
			LivenessPolicy policy = getConfig().getPolicy(workflow);
			return policy == null || policy.enabled;
		} catch (RuntimeException e) {
			LOGGER.log(Level.WARNING, "Liveness policy unreadable, treating as enabled: " + e.getClass().getSimpleName());
			return true;
		}
	}

	/**
	 * The Java-side gate. Returns {@code true} when liveness is disabled for the workflow (existing flow runs
	 * exactly as before) or when a fresh pass existed and was consumed. Otherwise audits REG-LIV-006 and returns
	 * {@code false}.
	 *
	 * @param fieldId
	 *            what was being attempted (for the audit row), e.g. the UI field id
	 */
	public boolean checkAndConsumePass(Workflow workflow, String fieldId) {
		boolean enabled = isEnabled(workflow);
		synchronized (this) {
			init();
			return gate.checkAndConsumePass(workflow, enabled, fieldId);
		}
	}

	/** Re-reads the synced configuration (call after a config sync). */
	public boolean refreshConfig(boolean online) {
		return getConfig().refresh(online);
	}

	private void init() {
		if (gate != null) {
			return;
		}
		auditLogger = new DesktopLivenessAuditLogger(audit);
		config = new DesktopLivenessConfigAdapter(globalParams, new File(dataDirectory, "config"),
				DesktopLivenessConfigAdapter.readBundledConfigFromClasspath(), auditLogger);
		gate = new DesktopLivenessGate(audit);
	}
}

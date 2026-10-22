package io.mosip.registration.livenessmanager.desktop;

/**
 * Access interface matching the Desktop Registration Client's global parameter service.
 * Global params are synced into Apache Derby's {@code GLOBAL_PARAM} table.
 */
public interface DesktopGlobalParamService {

    /**
     * Reads a global parameter value by its code/key.
     *
     * @param code the configuration code, e.g. {@code "mosip.registration.face.liveness.config"}
     * @return the JSON configuration string, or {@code null} if not found or not synced
     */
    String getGlobalParam(String code);
}

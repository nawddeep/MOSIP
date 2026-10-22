-- =============================================================================
-- MOSIP DESKTOP REGISTRATION CLIENT: APACHE DERBY SCHEMA REFERENCE
-- =============================================================================
-- Note: As mandated by MOSIP Hackathon guidelines, face liveness creates NO NEW
-- tables or schema changes. It exclusively integrates with these two pre-existing
-- tables via AuditManagerService and GlobalParamService.
-- =============================================================================

-- 1. EXISTING AUDIT TABLE IN APACHE DERBY
-- Entity mapping: io.mosip.registration.entity.Audit
-- Service: io.mosip.registration.service.AuditManagerService
CREATE TABLE APP_AUDIT_LOG (
    LOG_ID            INT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    LOG_DTIMES        BIGINT,                     -- Timestamp in milliseconds
    EVENT_ID          VARCHAR(64) NOT NULL,       -- e.g. 'REG-LIV-001' to 'REG-LIV-006'
    EVENT_NAME        VARCHAR(64) NOT NULL,       -- e.g. 'FACE_LIVENESS_SESSION', 'FACE_LIVENESS_CONFIG'
    EVENT_TYPE        VARCHAR(64) NOT NULL,       -- 'USER_EVENT' or 'SYSTEM_EVENT'
    ACTION_DTIMES     BIGINT,                     -- Execution timestamp
    HOST_NAME         VARCHAR(128),               -- Station hostname
    HOST_IP           VARCHAR(64),                -- Station IP address
    APP_ID            VARCHAR(64),                -- 'REGISTRATION_CLIENT'
    APP_NAME          VARCHAR(128),               -- 'Registration Client'
    SESSION_USER_ID   VARCHAR(64),                -- Logged-in Operator User ID
    SESSION_USER_NAME VARCHAR(128),               -- Operator Name
    REF_ID            VARCHAR(64),                -- Packet RID or Session ID
    REF_ID_TYPE       VARCHAR(64),                -- 'REGISTRATION_ID'
    CR_BY             VARCHAR(64),                -- Created By
    MODULE_NAME       VARCHAR(64),                -- 'REGISTRATION'
    MODULE_ID         VARCHAR(64),                -- 'REG-MOD'
    LOG_DESC          VARCHAR(2048)               -- Stores record.toCompactString()
);

-- Index used by MOSIP Audit Export / Sync Job
CREATE INDEX IDX_AUDIT_DTIMES ON APP_AUDIT_LOG(LOG_DTIMES);

-- Sample query executed by client audit view / sync export:
-- SELECT * FROM APP_AUDIT_LOG WHERE EVENT_ID LIKE 'REG-LIV%' ORDER BY LOG_DTIMES DESC;


-- 2. EXISTING GLOBAL PARAMETER TABLE IN APACHE DERBY
-- Entity mapping: io.mosip.registration.entity.GlobalParam
-- Service: io.mosip.registration.service.GlobalParamService
CREATE TABLE GLOBAL_PARAM (
    ID          VARCHAR(128) NOT NULL PRIMARY KEY, -- 'mosip.registration.face.liveness.config'
    NAME        VARCHAR(128),                      -- Display name
    VALUE       CLOB,                              -- Canonical JSON string containing SHA-256 digest
    STATUS      BOOLEAN DEFAULT TRUE               -- Active / Inactive
);

-- Sample query executed by DesktopGlobalParamService:
-- SELECT VALUE FROM GLOBAL_PARAM WHERE ID = 'mosip.registration.face.liveness.config' AND STATUS = true;

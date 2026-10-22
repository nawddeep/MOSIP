/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * Error codes written to the audit log only. Never sent to Flutter.
 */
public enum LivenessErrorCode {
    NONE("LIV-000"),
    PASSIVE_SCORE_LOW("LIV-101"),
    PAD_ATTACK_SUSPECTED("LIV-102"),
    CHALLENGE_NOT_PERFORMED("LIV-103"),
    CHALLENGE_WRONG_ACTION("LIV-104"),
    CHALLENGE_TIMED_OUT("LIV-105"),
    FACE_LOST_DURING_CHALLENGE("LIV-106"),
    FACE_DISCONTINUITY("LIV-107"),
    DEVICE_NOT_CONNECTED("LIV-201"),
    DEVICE_CONNECT_FAILED("LIV-202"),
    CAMERA_FAILURE("LIV-203"),
    CAMERA_PERMISSION_DENIED("LIV-204"),
    INVALID_FRAME_STREAM("LIV-301"),
    ENGINE_INIT_FAILED("LIV-401"),
    ENGINE_INFERENCE_FAILED("LIV-402"),
    MODEL_INTEGRITY_FAILED("LIV-403"),
    MAX_ATTEMPTS_REACHED("LIV-501"),
    CANCELLED_BY_USER("LIV-601"),
    CONFIG_REJECTED("LIV-701");

    private final String code;

    LivenessErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

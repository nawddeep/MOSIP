/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * Liveness session states sent to Flutter. See docs/liveness/01-architecture.md for the diagram.
 */
public enum LivenessState {
    IDLE(Kind.IDLE),
    POSITIONING(Kind.ACTIVE),
    PASSIVE_CHECK(Kind.ACTIVE),
    CHALLENGE(Kind.ACTIVE),
    SUCCESS(Kind.TERMINAL),

    /** User-attributable failures: consume one attempt. */
    LIVENESS_FAILED(Kind.ATTEMPT_FAILURE),
    PAD_FAILED(Kind.ATTEMPT_FAILURE),
    CHALLENGE_TIMEOUT(Kind.ATTEMPT_FAILURE),

    /** Guidance: shown while the session keeps running; resolved automatically. */
    NO_FACE(Kind.GUIDANCE),
    MULTIPLE_FACES(Kind.GUIDANCE),
    POOR_QUALITY(Kind.GUIDANCE),

    /** System errors: session stops, retry allowed, attempt not consumed. */
    DEVICE_UNAVAILABLE(Kind.SYSTEM_ERROR),
    CAMERA_ERROR(Kind.SYSTEM_ERROR),
    INVALID_FRAMES(Kind.SYSTEM_ERROR),
    ENGINE_ERROR(Kind.SYSTEM_ERROR),

    MAX_RETRIES(Kind.TERMINAL);

    public enum Kind { IDLE, ACTIVE, GUIDANCE, ATTEMPT_FAILURE, SYSTEM_ERROR, TERMINAL }

    private final Kind kind;

    LivenessState(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** True while frames are being consumed (active or guidance). */
    public boolean isRunning() {
        return kind == Kind.ACTIVE || kind == Kind.GUIDANCE;
    }

    /** True when the user may press "Try again" / "Retry connection". */
    public boolean isRetryable() {
        return kind == Kind.ATTEMPT_FAILURE || kind == Kind.SYSTEM_ERROR;
    }

    public boolean isTerminal() {
        return kind == Kind.TERMINAL;
    }
}

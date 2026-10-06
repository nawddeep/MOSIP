/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.challenge;

import io.mosip.registration.livenessmanager.api.ChallengeDetector;
import io.mosip.registration.livenessmanager.model.ChallengeType;

/** Creates a fresh detector per challenge. Replaceable for vendor detectors or tests. */
public interface ChallengeDetectorFactory {

    ChallengeDetector create(ChallengeType type);

    ChallengeDetectorFactory DEFAULT = type -> {
        switch (type) {
            case BLINK:
                return new BlinkDetector();
            case SMILE:
                return new SmileDetector();
            case TURN_LEFT:
            case TURN_RIGHT:
                return new HeadTurnDetector(type);
            default:
                throw new IllegalArgumentException("Unsupported challenge " + type);
        }
    };
}

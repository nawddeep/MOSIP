/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * Active challenges. LEFT/RIGHT are always from the user's own point of view.
 */
public enum ChallengeType {
    BLINK,
    SMILE,
    TURN_LEFT,
    TURN_RIGHT
}

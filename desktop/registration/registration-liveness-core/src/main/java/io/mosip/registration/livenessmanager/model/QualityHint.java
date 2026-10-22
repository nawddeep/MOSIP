/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * Sub-reason for {@link LivenessState#POOR_QUALITY}. Only tells the UI which instruction to show;
 * it never carries the measured value.
 */
public enum QualityHint {
    /** Too dark or over-exposed: "Improve the lighting". */
    LIGHTING,
    /** Face too small in frame: "Move closer". */
    FAR,
    /** Blurry or moving: "Hold still." */
    BLURRY
}

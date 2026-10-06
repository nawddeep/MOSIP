/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.QualityResult;

/** Brightness / blur / face-size / motion checks on the face region. */
public interface QualityAnalyzer {
    /**
     * @param previous the face in the previous analysed frame (for motion), or null
     */
    QualityResult analyze(FaceFrame frame, FaceObservation face, FaceObservation previous, LivenessPolicy policy);
}

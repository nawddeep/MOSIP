/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import java.util.List;

import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

/** Face detection + landmarks (MediaPipe Face Landmarker by default). */
public interface FaceDetector extends AutoCloseable {
    void initialize() throws LivenessEngine.EngineException;

    /** @return all faces found (up to the detector's max), landmarks in pixel coordinates */
    List<FaceObservation> detect(FaceFrame frame) throws LivenessEngine.EngineException;

    String describe();

    @Override
    void close();
}

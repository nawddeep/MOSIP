/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import io.mosip.registration.livenessmanager.model.FaceFrame;

/**
 * One reusable RGBA Mat per analysis thread, filled once per frame and shared by the PAD
 * preprocessing and the quality analyzer.
 */
final class OpenCvFrameCache {
    private Mat rgba;
    private long frameId = -1;

    Mat get(FaceFrame frame) {
        if (rgba == null || rgba.cols() != frame.getWidth() || rgba.rows() != frame.getHeight()) {
            release();
            rgba = new Mat(frame.getHeight(), frame.getWidth(), CvType.CV_8UC4);
            frameId = -1;
        }
        if (frameId != frame.getFrameId()) {
            rgba.put(0, 0, frame.getRgba());
            frameId = frame.getFrameId();
        }
        return rgba;
    }

    void release() {
        if (rgba != null) {
            rgba.release();
            rgba = null;
        }
    }
}

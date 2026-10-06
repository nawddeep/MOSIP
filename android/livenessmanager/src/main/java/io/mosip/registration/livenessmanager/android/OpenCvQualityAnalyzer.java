/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import io.mosip.registration.livenessmanager.api.QualityAnalyzer;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.QualityResult;
import io.mosip.registration.livenessmanager.quality.LumaQualityAnalyzer;
import io.mosip.registration.livenessmanager.quality.QualityRules;

/**
 * Quality checks with plain OpenCV on the face region: mean gray level (brightness) and variance of
 * the Laplacian (sharpness) on the region resized to 128 px wide. Same definitions and thresholds as
 * {@link LumaQualityAnalyzer}, which covers JVM tests.
 */
public final class OpenCvQualityAnalyzer implements QualityAnalyzer {
    private final OpenCvFrameCache frames;
    private final Mat gray = new Mat();
    private final Mat small = new Mat();
    private final Mat lap = new Mat();
    private final MatOfDouble mean = new MatOfDouble();
    private final MatOfDouble std = new MatOfDouble();

    OpenCvQualityAnalyzer(OpenCvFrameCache frames) {
        this.frames = frames;
    }

    @Override
    public QualityResult analyze(FaceFrame frame, FaceObservation face, FaceObservation previous, LivenessPolicy policy) {
        Mat rgba = frames.get(frame);
        int w = frame.getWidth(), h = frame.getHeight();
        int x0 = clamp((int) face.getLeft(), 0, w - 2), y0 = clamp((int) face.getTop(), 0, h - 2);
        int x1 = clamp((int) face.getRight(), x0 + 2, w), y1 = clamp((int) face.getBottom(), y0 + 2, h);
        Mat roi = rgba.submat(new Rect(x0, y0, x1 - x0, y1 - y0));
        Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY);
        roi.release();

        Core.meanStdDev(gray, mean, std);
        double brightness = mean.toArray()[0];

        int sw = Math.min(LumaQualityAnalyzer.SHARPNESS_WIDTH, gray.cols());
        int sh = Math.max(3, (int) Math.round((double) gray.rows() * sw / gray.cols()));
        Imgproc.resize(gray, small, new Size(Math.max(3, sw), sh), 0, 0, Imgproc.INTER_AREA);
        Imgproc.Laplacian(small, lap, CvType.CV_64F);
        Core.meanStdDev(lap, mean, std);
        double sd = std.toArray()[0];
        double sharpness = sd * sd;

        double sizeRatio = face.getWidth() / (double) Math.min(w, h);
        double motion = previous == null ? 0 : QualityRules.motion(face.getCenterX(), face.getCenterY(),
                previous.getCenterX(), previous.getCenterY(), face.getWidth());
        return QualityRules.evaluate(brightness, sharpness, sizeRatio, motion, policy);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

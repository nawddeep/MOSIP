/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * Result of running the engine on one frame. Contains no pixels.
 */
public final class FrameAnalysis {
    private final long frameId;
    private final long timestampNanos;
    private final boolean mirrored;
    private final int faceCount;
    private final FaceObservation face;
    private final QualityResult quality;
    private final Float padLiveScore;

    /**
     * @param face         the single face when {@code faceCount == 1}, otherwise null
     * @param quality      null when no single face was found
     * @param padLiveScore probability [0,1] that the face is live; null when PAD was not run for this frame
     */
    public FrameAnalysis(long frameId, long timestampNanos, boolean mirrored, int faceCount,
                         FaceObservation face, QualityResult quality, Float padLiveScore) {
        this.frameId = frameId;
        this.timestampNanos = timestampNanos;
        this.mirrored = mirrored;
        this.faceCount = faceCount;
        this.face = face;
        this.quality = quality;
        this.padLiveScore = padLiveScore;
    }

    public long getFrameId() { return frameId; }
    public long getTimestampNanos() { return timestampNanos; }
    public boolean isMirrored() { return mirrored; }
    public int getFaceCount() { return faceCount; }
    public FaceObservation getFace() { return face; }
    public QualityResult getQuality() { return quality; }
    public Float getPadLiveScore() { return padLiveScore; }
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

/**
 * One upright camera frame. Pixels are tightly packed RGBA_8888, rotation already applied.
 * <p>
 * Frames hold raw face pixels and must never be logged, persisted or sent to Flutter.
 * {@code toString()} deliberately omits pixel data.
 */
public final class FaceFrame {
    private final long frameId;
    private final long timestampNanos;
    private final int width;
    private final int height;
    private final byte[] rgba;
    private final boolean mirrored;
    /** Optional platform image (e.g. android.graphics.Bitmap) so adapters can avoid re-conversion. */
    private final Object platformImage;
    private byte[] luma;

    public FaceFrame(long frameId, long timestampNanos, int width, int height, byte[] rgba,
                     boolean mirrored, Object platformImage) {
        this.frameId = frameId;
        this.timestampNanos = timestampNanos;
        this.width = width;
        this.height = height;
        this.rgba = rgba;
        this.mirrored = mirrored;
        this.platformImage = platformImage;
    }

    public long getFrameId() { return frameId; }
    public long getTimestampNanos() { return timestampNanos; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public byte[] getRgba() { return rgba; }
    /** True when the frame is a selfie-style mirror image (left/right swapped). */
    public boolean isMirrored() { return mirrored; }
    public Object getPlatformImage() { return platformImage; }

    /** Structural validity: non-empty and the buffer matches the declared size. */
    public boolean isValid() {
        return width > 0 && height > 0 && rgba != null && rgba.length == width * height * 4;
    }

    /** Lazily computed 8-bit luma plane (BT.601), cached per frame. */
    public byte[] getLuma() {
        if (luma == null) {
            byte[] out = new byte[width * height];
            for (int i = 0, p = 0; i < out.length; i++, p += 4) {
                int r = rgba[p] & 0xFF, g = rgba[p + 1] & 0xFF, b = rgba[p + 2] & 0xFF;
                out[i] = (byte) ((77 * r + 150 * g + 29 * b) >> 8);
            }
            luma = out;
        }
        return luma;
    }

    @Override
    public String toString() {
        return "FaceFrame{id=" + frameId + ", " + width + "x" + height + ", mirrored=" + mirrored + "}";
    }
}

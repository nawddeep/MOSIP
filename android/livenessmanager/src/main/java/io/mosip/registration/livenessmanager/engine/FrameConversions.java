/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.engine;

/** Pixel layout conversions. Pure Java so they are unit-tested on the JVM. */
public final class FrameConversions {
    private FrameConversions() {
    }

    /**
     * Tightly packed RGBA_8888 to tightly packed BGR (OpenCV order, as MiniFASNet expects).
     *
     * @param reuse buffer reused when it already has the right size; may be null
     * @return the BGR buffer (reuse if it fitted)
     */
    public static byte[] rgbaToBgr(byte[] rgba, int pixels, byte[] reuse) {
        if (rgba == null || rgba.length < pixels * 4) {
            throw new IllegalArgumentException("RGBA buffer smaller than " + pixels + " pixels");
        }
        byte[] out = reuse != null && reuse.length == pixels * 3 ? reuse : new byte[pixels * 3];
        for (int p = 0, i = 0, o = 0; p < pixels; p++, i += 4, o += 3) {
            out[o] = rgba[i + 2];
            out[o + 1] = rgba[i + 1];
            out[o + 2] = rgba[i];
        }
        return out;
    }
}

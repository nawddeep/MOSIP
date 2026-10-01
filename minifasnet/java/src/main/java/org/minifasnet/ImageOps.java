package org.minifasnet;

/**
 * Preprocessing, ported from the upstream repo / OpenCV so the Java output matches Python:
 *  - getNewBox : src/generate_patches.py CropImage._get_new_box
 *  - cropResize: the CANONICAL resize of this module = OpenCV's generic fixed-point INTER_LINEAR.
 *                Bit-identical to python/minifasnet_pad.py:resize_bilinear_u8 (verified in tests/test_parity.py).
 *                NOT bit-identical to every cv2.resize build: some OpenCV builds route resize through vendor HALs
 *                (e.g. Carotene/KleidiCV on arm64) that differ by +-1 pixel. That noise is measured in the parity
 *                report. Do not replace this with a platform resizer (Android Bitmap scaling etc.).
 */
final class ImageOps {
    private ImageOps() {}

    /** Returns inclusive {x1, y1, x2, y2}. Same float math and int() truncation as Python. */
    static int[] getNewBox(int srcW, int srcH, double bx, double by, double bw, double bh, double scale) {
        // Shrink the scale if the scaled box would not fit inside the frame.
        scale = Math.min((srcH - 1) / bh, Math.min((srcW - 1) / bw, scale));
        double newW = bw * scale, newH = bh * scale;
        double cx = bw / 2 + bx, cy = bh / 2 + by;
        double x1 = cx - newW / 2, y1 = cy - newH / 2;
        double x2 = cx + newW / 2, y2 = cy + newH / 2;
        // Shift (do not pad) the window back inside the frame.
        if (x1 < 0) { x2 -= x1; x1 = 0; }
        if (y1 < 0) { y2 -= y1; y1 = 0; }
        if (x2 > srcW - 1) { x1 -= x2 - srcW + 1; x2 = srcW - 1; }
        if (y2 > srcH - 1) { y1 -= y2 - srcH + 1; y2 = srcH - 1; }
        return new int[] {(int) x1, (int) y1, (int) x2, (int) y2}; // (int) truncates toward zero, like Python int()
    }

    private static final int COEF_BITS = 11;          // OpenCV INTER_RESIZE_COEF_BITS
    private static final int COEF_SCALE = 1 << COEF_BITS;

    /** round-half-to-even, like OpenCV cvRound / saturate_cast<short>(float). */
    private static int cvRound(float v) { return (int) Math.rint(v); }

    /**
     * Crop [x1..x2]x[y1..y2] (inclusive) from an HxWx3 BGR byte array and resize to outW x outH with
     * OpenCV's 8-bit INTER_LINEAR fixed-point algorithm (half-pixel centers, 11-bit coefficients).
     * Returns HWC bytes (BGR) of the resized patch.
     */
    static byte[] cropResize(byte[] src, int srcW, int x1, int y1, int x2, int y2, int outW, int outH) {
        final int pw = x2 - x1 + 1, ph = y2 - y1 + 1, cn = 3;
        double scaleX = (double) pw / outW, scaleY = (double) ph / outH;

        int[] xofs = new int[outW];
        int[] xa0 = new int[outW], xa1 = new int[outW];
        for (int dx = 0; dx < outW; dx++) {
            float fx = (float) ((dx + 0.5) * scaleX - 0.5);
            int sx = (int) Math.floor(fx);
            fx -= sx;
            if (sx < 0) { fx = 0; sx = 0; }
            if (sx >= pw - 1) { fx = 0; sx = pw - 1; }
            xofs[dx] = sx;
            xa0[dx] = cvRound((1.f - fx) * COEF_SCALE);
            xa1[dx] = cvRound(fx * COEF_SCALE);
        }
        int[] yofs = new int[outH];
        int[] yb0 = new int[outH], yb1 = new int[outH];
        for (int dy = 0; dy < outH; dy++) {
            float fy = (float) ((dy + 0.5) * scaleY - 0.5);
            int sy = (int) Math.floor(fy);
            fy -= sy;
            if (sy < 0) { fy = 0; sy = 0; }
            if (sy >= ph - 1) { fy = 0; sy = ph - 1; }
            yofs[dy] = sy;
            yb0[dy] = cvRound((1.f - fy) * COEF_SCALE);
            yb1[dy] = cvRound(fy * COEF_SCALE);
        }

        byte[] out = new byte[outW * outH * cn];
        int[] rowA = new int[outW * cn];
        int[] rowB = new int[outW * cn];
        for (int dy = 0; dy < outH; dy++) {
            int sy0 = yofs[dy], sy1 = Math.min(sy0 + 1, ph - 1);   // neighbour row, clamped at the border
            hresize(src, srcW, x1, y1 + sy0, pw, xofs, xa0, xa1, outW, rowA);
            hresize(src, srcW, x1, y1 + sy1, pw, xofs, xa0, xa1, outW, rowB);
            for (int i = 0; i < outW * cn; i++) {
                // OpenCV's generic SIMD vertical pass (VResizeLinearVec_32s8u):
                // each row value is pre-shifted >>4 and multiplied by beta with a 16-bit "mulhi" (>>16),
                // then summed, +2, >>2. It is NOT the same rounding as the scalar (v + 2^21) >> 22 path
                // (that one differs by 1 on ~10% of pixels, which the model amplifies to ~5e-3 in the score).
                int r = ((((rowA[i] >> 4) * yb0[dy]) >> 16) + (((rowB[i] >> 4) * yb1[dy]) >> 16) + 2) >> 2;
                out[dy * outW * cn + i] = (byte) (r < 0 ? 0 : (r > 255 ? 255 : r));
            }
        }
        return out;
    }

    /** Horizontal pass for one source row (row index already offset into the frame). */
    private static void hresize(byte[] src, int srcW, int x1, int row, int pw, int[] xofs,
                                int[] a0, int[] a1, int outW, int[] dst) {
        int base = (row * srcW + x1) * 3;
        for (int dx = 0; dx < outW; dx++) {
            int s0 = xofs[dx], s1 = Math.min(s0 + 1, pw - 1);
            for (int c = 0; c < 3; c++) {
                dst[dx * 3 + c] = (src[base + s0 * 3 + c] & 0xFF) * a0[dx] + (src[base + s1 * 3 + c] & 0xFF) * a1[dx];
            }
        }
    }

    /** HWC uint8 BGR -> NCHW float32, values 0..255. No /255, no mean/std, no BGR->RGB (upstream does none). */
    static float[] toTensor(byte[] hwc, int w, int h) {
        float[] t = new float[3 * h * w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int c = 0; c < 3; c++)
                    t[c * h * w + y * w + x] = hwc[(y * w + x) * 3 + c] & 0xFF;
        return t;
    }
}

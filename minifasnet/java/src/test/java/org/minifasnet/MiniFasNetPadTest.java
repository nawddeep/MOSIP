package org.minifasnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Unit tests. Cross-language numeric parity lives in minifasnet/tests/test_parity.py. */
class MiniFasNetPadTest {
    static MiniFasNetPad pad;
    static final int W = 480, H = 640;
    static final byte[] GRAY = new byte[W * H * 3];

    @BeforeAll static void load() {
        java.util.Arrays.fill(GRAY, (byte) 128);
        pad = new MiniFasNetPad(System.getProperty("modelDir"), 32);
    }

    @AfterAll static void close() { pad.close(); }

    @Test void newBoxMatchesPythonValues() {
        // Expected values computed with python/minifasnet_pad.py:get_new_box (upstream port).
        assertArrayEquals(new int[] {0, 6, 479, 501}, ImageOps.getNewBox(480, 640, 105, 147, 207, 214, 2.7));
        assertArrayEquals(new int[] {209, 315, 479, 639}, ImageOps.getNewBox(480, 640, 400, 500, 100, 120, 2.7));
        assertArrayEquals(new int[] {79, 159, 479, 639}, ImageOps.getNewBox(480, 640, 400, 500, 100, 120, 4.0));
    }

    @Test void sameSizeResizeIsIdentity() {
        byte[] img = new byte[80 * 80 * 3];
        for (int i = 0; i < img.length; i++) img[i] = (byte) (i * 31);
        assertArrayEquals(img, ImageOps.cropResize(img, 80, 0, 0, 79, 79, 80, 80));
    }

    @Test void validFrameGivesScoreInRange() {
        PadResult r = pad.predict(GRAY, W, H, new double[] {150, 200, 150, 180});
        assertTrue(r.realScore >= 0 && r.realScore <= 1);
        assertEquals(2, r.perModelScores.size());
    }

    private static PadException.Code codeOf(Runnable r) {
        return assertThrows(PadException.class, r::run).getCode();
    }

    @Test void invalidInputsAreRejected() {
        assertEquals(PadException.Code.INVALID_FRAME, codeOf(() -> pad.predict(null, W, H, new double[] {1, 1, 50, 50})));
        assertEquals(PadException.Code.INVALID_FRAME, codeOf(() -> pad.predict(new byte[10], W, H, new double[] {1, 1, 50, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {1, 1, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {Double.NaN, 1, 50, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {10, 10, 0, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {5000, 5000, 60, 60})));
        assertEquals(PadException.Code.FACE_TOO_SMALL, codeOf(() -> pad.predict(GRAY, W, H, new double[] {10, 10, 10, 10})));
    }

    @Test void missingModelDirFailsClearly() {
        assertEquals(PadException.Code.MODEL_LOAD, codeOf(() -> new MiniFasNetPad("/nonexistent", 32)));
    }
}

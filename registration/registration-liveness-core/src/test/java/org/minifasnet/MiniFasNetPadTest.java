package org.minifasnet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Unit tests. Cross-language numeric parity lives in minifasnet/tests/test_parity.py. */
class MiniFasNetPadTest {
    static final String MODEL_DIR = System.getProperty("modelDir", "src/main/resources/liveness/models/minifasnet");
    static final int W = 480, H = 640;
    static final byte[] GRAY = new byte[W * H * 3];
    static MiniFasNetPad pad;

    @BeforeAll static void load() {
        Arrays.fill(GRAY, (byte) 128);
        pad = new MiniFasNetPad(MODEL_DIR, 32);
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
        assertEquals(3, r.perModelScores.get(0).getProbs().length);
    }

    @Test void oneThreadGivesSameScore() {
        double[] box = {150, 200, 150, 180};
        try (MiniFasNetPad oneThread = new MiniFasNetPad(MODEL_DIR, 32, 1)) {
            assertEquals(pad.predict(GRAY, W, H, box).realScore, oneThread.predict(GRAY, W, H, box).realScore, 1e-9);
        }
    }

    @Test void toStringIsLocaleIndependent() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // decimal comma locale
            String s = pad.predict(GRAY, W, H, new double[] {150, 200, 150, 180}).toString();
            assertTrue(s.matches(".*realScore=\\d\\.\\d{6},.*"), s);
        } finally {
            Locale.setDefault(old);
        }
    }

    private static PadException.Code codeOf(Executable call) {
        return assertThrows(PadException.class, call).getCode();
    }

    @Test void invalidInputsAreRejected() {
        assertEquals(PadException.Code.INVALID_FRAME, codeOf(() -> pad.predict(null, W, H, new double[] {1, 1, 50, 50})));
        assertEquals(PadException.Code.INVALID_FRAME, codeOf(() -> pad.predict(new byte[10], W, H, new double[] {1, 1, 50, 50})));
        assertEquals(PadException.Code.INVALID_FRAME, codeOf(() -> pad.predict(new byte[3], 1, 1, new double[] {0, 0, 1, 1})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, null)));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {1, 1, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {Double.NaN, 1, 50, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {1, 1, Double.POSITIVE_INFINITY, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {10, 10, 0, 50})));
        assertEquals(PadException.Code.INVALID_BOX, codeOf(() -> pad.predict(GRAY, W, H, new double[] {5000, 5000, 60, 60})));
        assertEquals(PadException.Code.FACE_TOO_SMALL, codeOf(() -> pad.predict(GRAY, W, H, new double[] {10, 10, 10, 10})));
    }

    @Test void badConstructionFailsClearly() {
        assertEquals(PadException.Code.MODEL_LOAD, codeOf(() -> new MiniFasNetPad("/nonexistent", 32)));
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetPad(MODEL_DIR, -1));
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetPad(MODEL_DIR, 32, -1));
    }

    @Test void closedInstanceRejectsCalls() {
        MiniFasNetPad p = new MiniFasNetPad(MODEL_DIR, 32);
        p.close();
        assertThrows(IllegalStateException.class, () -> p.predict(GRAY, W, H, new double[] {150, 200, 150, 180}));
    }
}

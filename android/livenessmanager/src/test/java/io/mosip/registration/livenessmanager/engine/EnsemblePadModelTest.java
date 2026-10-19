package io.mosip.registration.livenessmanager.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.minifasnet.PadException;

import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.PadModel;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;

public class EnsemblePadModelTest {

    static final class FakeScorer implements EnsemblePadModel.Scorer {
        byte[] lastBgr;
        int lastW;
        int lastH;
        double[] lastBox;
        double score = 0.97;
        PadException toThrow;
        boolean closed;

        @Override
        public double realScore(byte[] bgr, int width, int height, double[] box) {
            if (toThrow != null) throw toThrow;
            lastBgr = bgr.clone();
            lastW = width;
            lastH = height;
            lastBox = box.clone();
            return score;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static FaceFrame frame() {
        // 2x1 RGBA: pixel 0 = (R10,G20,B30), pixel 1 = (R40,G50,B60)
        return new FaceFrame(1, 1, 2, 1, new byte[]{10, 20, 30, (byte) 255, 40, 50, 60, (byte) 255}, false, null);
    }

    @Test
    public void rgbaIsConvertedToBgrAndBoxIsXYWH() throws Exception {
        FakeScorer s = new FakeScorer();
        EnsemblePadModel m = new EnsemblePadModel(() -> s, "test");
        m.initialize();
        FaceObservation face = SyntheticFace.neutral();
        assertEquals(0.97f, m.liveScore(frame(), face), 1e-6);
        assertArrayEquals(new byte[]{30, 20, 10, 60, 50, 40}, s.lastBgr);
        assertEquals(2, s.lastW);
        assertEquals(1, s.lastH);
        assertArrayEquals(new double[]{face.getLeft(), face.getTop(), face.getWidth(), face.getHeight()}, s.lastBox, 1e-6);
    }

    @Test
    public void unscorableBoxesAreSkippedNotErrors() throws Exception {
        FakeScorer s = new FakeScorer();
        EnsemblePadModel m = new EnsemblePadModel(() -> s, "test");
        m.initialize();
        for (PadException.Code c : new PadException.Code[]{PadException.Code.INVALID_BOX,
                PadException.Code.FACE_TOO_SMALL, PadException.Code.INVALID_FRAME}) {
            s.toThrow = new PadException(c, "x");
            assertTrue(c.name(), Float.isNaN(m.liveScore(frame(), SyntheticFace.neutral())));
        }
        assertTrue(Float.isNaN(PadModel.NOT_SCORABLE));
    }

    @Test
    public void inferenceAndModelErrorsFailClosed() throws Exception {
        FakeScorer s = new FakeScorer();
        EnsemblePadModel m = new EnsemblePadModel(() -> s, "test");
        m.initialize();
        s.toThrow = new PadException(PadException.Code.INFERENCE, "boom");
        try {
            m.liveScore(frame(), SyntheticFace.neutral());
            fail();
        } catch (LivenessEngine.EngineException expected) {
            assertTrue(!expected.isIntegrityFailure());
        }
        s.toThrow = new PadException(PadException.Code.MODEL_LOAD, "gone");
        try {
            m.liveScore(frame(), SyntheticFace.neutral());
            fail();
        } catch (LivenessEngine.EngineException expected) {
            assertTrue(expected.isIntegrityFailure());
        }
    }

    @Test
    public void notInitialisedFailsAndCloseReleasesScorer() throws Exception {
        FakeScorer s = new FakeScorer();
        EnsemblePadModel m = new EnsemblePadModel(() -> s, "test");
        try {
            m.liveScore(frame(), SyntheticFace.neutral());
            fail();
        } catch (LivenessEngine.EngineException expected) {
            assertTrue(expected.getMessage().contains("not initialized"));
        }
        m.initialize();
        m.close();
        assertTrue(s.closed);
    }

    @Test
    public void loadFailurePropagatesFromFactory() {
        EnsemblePadModel m = new EnsemblePadModel(() -> {
            throw new LivenessEngine.EngineException("Model digest mismatch", null, true);
        }, "test");
        try {
            m.initialize();
            fail();
        } catch (LivenessEngine.EngineException expected) {
            assertTrue(expected.isIntegrityFailure());
        }
    }

    @Test
    public void bgrBufferIsReused() {
        byte[] rgba = new byte[16];
        byte[] first = FrameConversions.rgbaToBgr(rgba, 4, null);
        assertTrue(first == FrameConversions.rgbaToBgr(rgba, 4, first));
        assertEquals(12, first.length);
    }
}

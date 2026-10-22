package io.mosip.registration.livenessmanager.quality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Random;

import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.QualityResult;

public class LumaQualityAnalyzerTest {
    private final LumaQualityAnalyzer qa = new LumaQualityAnalyzer();
    private final LivenessPolicy policy = new LivenessPolicy();

    /** Frame with uniform base level plus optional random texture of the given amplitude. */
    public static FaceFrame frame(int base, int texture) {
        int w = 640, h = 480;
        byte[] rgba = new byte[w * h * 4];
        Random r = new Random(7);
        for (int i = 0; i < w * h; i++) {
            int v = Math.max(0, Math.min(255, base + (texture == 0 ? 0 : r.nextInt(2 * texture + 1) - texture)));
            rgba[i * 4] = (byte) v;
            rgba[i * 4 + 1] = (byte) v;
            rgba[i * 4 + 2] = (byte) v;
            rgba[i * 4 + 3] = (byte) 255;
        }
        return new FaceFrame(1, 1, w, h, rgba, false, null);
    }

    @Test
    public void goodFrameHasNoHint() {
        QualityResult q = qa.analyze(frame(130, 40), SyntheticFace.neutral(), null, policy);
        assertNull(q.getHint());
        assertEquals(130, q.getBrightness(), 3);
    }

    @Test
    public void darkFrameAsksForLighting() {
        assertEquals(QualityHint.LIGHTING, qa.analyze(frame(20, 10), SyntheticFace.neutral(), null, policy).getHint());
        assertEquals(QualityHint.LIGHTING, qa.analyze(frame(245, 5), SyntheticFace.neutral(), null, policy).getHint());
    }

    @Test
    public void featurelessFaceIsBlurry() {
        assertEquals(QualityHint.BLURRY, qa.analyze(frame(130, 0), SyntheticFace.neutral(), null, policy).getHint());
    }

    @Test
    public void smallFaceIsFar() {
        FaceObservation small = SyntheticFace.face(320, 240, 60, 0.3, 0.7, 0);
        assertEquals(QualityHint.FAR, qa.analyze(frame(130, 40), small, null, policy).getHint());
    }

    @Test
    public void largeMotionIsBlurry() {
        FaceObservation prev = SyntheticFace.face(250, 240, 200, 0.3, 0.7, 0);
        assertEquals(QualityHint.BLURRY, qa.analyze(frame(130, 40), SyntheticFace.neutral(), prev, policy).getHint());
    }
}

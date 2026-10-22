package io.mosip.registration.livenessmanager.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.api.FaceDetector;
import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.api.PadModel;
import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.FaceObservation;
import io.mosip.registration.livenessmanager.model.FrameAnalysis;
import io.mosip.registration.livenessmanager.model.LivenessPolicy;
import io.mosip.registration.livenessmanager.quality.LumaQualityAnalyzer;
import io.mosip.registration.livenessmanager.quality.LumaQualityAnalyzerTest;

public class DefaultLivenessEngineTest {

    static final class Detector implements FaceDetector {
        List<FaceObservation> faces = new ArrayList<>(Collections.singletonList(SyntheticFace.neutral()));

        @Override public void initialize() { }
        @Override public List<FaceObservation> detect(FaceFrame frame) { return faces; }
        @Override public String describe() { return "det"; }
        @Override public void close() { }
    }

    static final class Pad implements PadModel {
        float score = 0.9f;
        int calls;

        @Override public void initialize() { }
        @Override public float liveScore(FaceFrame frame, FaceObservation face) { calls++; return score; }
        @Override public String describe() { return "pad"; }
        @Override public void close() { }
    }

    @Test
    public void runsPadOnlyWhenAskedAndFaceUsable() throws Exception {
        Detector d = new Detector();
        Pad p = new Pad();
        DefaultLivenessEngine e = new DefaultLivenessEngine(d, p, new LumaQualityAnalyzer());
        e.initialize();
        FaceFrame f = LumaQualityAnalyzerTest.frame(130, 40);
        FrameAnalysis a = e.analyze(f, new LivenessPolicy(), true);
        assertEquals(1, a.getFaceCount());
        assertEquals(0.9f, a.getPadLiveScore(), 1e-6);
        assertNull(e.analyze(f, new LivenessPolicy(), false).getPadLiveScore());
        d.faces = new ArrayList<>(Collections.singletonList(SyntheticFace.face(320, 240, 40, 0.3, 0.7, 0)));
        assertNull("too-far face is not scored", e.analyze(f, new LivenessPolicy(), true).getPadLiveScore());
        assertEquals(1, p.calls);
    }

    @Test
    public void multipleFacesReportCountWithoutFace() throws Exception {
        Detector d = new Detector();
        d.faces = new ArrayList<>(java.util.Arrays.asList(SyntheticFace.neutral(), SyntheticFace.neutral()));
        DefaultLivenessEngine e = new DefaultLivenessEngine(d, new Pad(), new LumaQualityAnalyzer());
        e.initialize();
        FrameAnalysis a = e.analyze(LumaQualityAnalyzerTest.frame(130, 40), new LivenessPolicy(), true);
        assertEquals(2, a.getFaceCount());
        assertNull(a.getFace());
    }

    @Test
    public void outOfRangeScoreAndUninitialisedUseFailClosed() throws Exception {
        Pad p = new Pad();
        p.score = 1.5f;
        DefaultLivenessEngine e = new DefaultLivenessEngine(new Detector(), p, new LumaQualityAnalyzer());
        try {
            e.analyze(LumaQualityAnalyzerTest.frame(130, 40), new LivenessPolicy(), true);
            fail();
        } catch (LivenessEngine.EngineException expected) {
            assertTrue(expected.getMessage().contains("not initialized"));
        }
        e.initialize();
        try {
            e.analyze(LumaQualityAnalyzerTest.frame(130, 40), new LivenessPolicy(), true);
            fail();
        } catch (LivenessEngine.EngineException expected) {
            assertTrue(expected.getMessage().contains("out-of-range"));
        }
    }

    @Test
    public void notScorableFrameIsSkippedNotFailed() throws Exception {
        Pad p = new Pad();
        p.score = PadModel.NOT_SCORABLE;
        DefaultLivenessEngine e = new DefaultLivenessEngine(new Detector(), p, new LumaQualityAnalyzer());
        e.initialize();
        FrameAnalysis a = e.analyze(LumaQualityAnalyzerTest.frame(130, 40), new LivenessPolicy(), true);
        assertEquals(1, a.getFaceCount());
        assertNull(a.getPadLiveScore());
    }
}

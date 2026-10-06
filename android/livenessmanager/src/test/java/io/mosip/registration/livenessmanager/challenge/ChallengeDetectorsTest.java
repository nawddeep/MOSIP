package io.mosip.registration.livenessmanager.challenge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.mosip.registration.livenessmanager.SyntheticFace;
import io.mosip.registration.livenessmanager.api.ChallengeDetector;
import io.mosip.registration.livenessmanager.api.ChallengeDetector.Result;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.FaceObservation;

public class ChallengeDetectorsTest {

    private static List<FaceObservation> baseline() {
        List<FaceObservation> b = new ArrayList<>();
        for (int i = 0; i < 5; i++) b.add(SyntheticFace.neutral());
        return b;
    }

    private static FaceObservation f(double ear, double mouth, double yaw) {
        return SyntheticFace.face(320, 240, 200, ear, mouth, yaw);
    }

    @Test
    public void geometryMatchesSyntheticConstruction() {
        FaceObservation n = f(0.3, 0.7, 0);
        assertEquals(0.3, FaceGeometry.eyeAspectRatio(n), 1e-3);
        assertEquals(0.7, FaceGeometry.mouthRatio(n), 1e-3);
        assertEquals(0.0, FaceGeometry.yawDegrees(n, false), 1e-3);
        assertEquals(25.0, FaceGeometry.yawDegrees(f(0.3, 0.7, 25), false), 0.5);
        assertEquals(-25.0, FaceGeometry.yawDegrees(f(0.3, 0.7, 25), true), 0.5);
    }

    @Test
    public void blinkNeedsCloseThenReopen() {
        ChallengeDetector d = new BlinkDetector();
        d.begin(baseline(), false);
        assertEquals(Result.PENDING, d.onFace(f(0.29, 0.7, 0)));
        assertEquals(Result.PENDING, d.onFace(f(0.08, 0.7, 0)));
        assertEquals(Result.PENDING, d.onFace(f(0.10, 0.7, 0)));
        assertEquals(Result.COMPLETED, d.onFace(f(0.30, 0.7, 0)));
    }

    @Test
    public void staticOpenEyesNeverBlink() {
        ChallengeDetector d = new BlinkDetector();
        d.begin(baseline(), false);
        for (int i = 0; i < 100; i++) assertEquals(Result.PENDING, d.onFace(f(0.3, 0.7, 0)));
    }

    @Test
    public void partialSquintIsNotABlink() {
        ChallengeDetector d = new BlinkDetector();
        d.begin(baseline(), false);
        for (int i = 0; i < 20; i++) {
            assertEquals(Result.PENDING, d.onFace(f(0.24, 0.7, 0)));
            assertEquals(Result.PENDING, d.onFace(f(0.30, 0.7, 0)));
        }
    }

    @Test
    public void smileNeedsSustainedWidening() {
        ChallengeDetector d = new SmileDetector();
        d.begin(baseline(), false);
        assertEquals(Result.PENDING, d.onFace(f(0.3, 0.85, 0)));
        assertEquals(Result.PENDING, d.onFace(f(0.3, 0.70, 0)));
        assertEquals(Result.PENDING, d.onFace(f(0.3, 0.85, 0)));
        assertEquals(Result.PENDING, d.onFace(f(0.3, 0.85, 0)));
        assertEquals(Result.COMPLETED, d.onFace(f(0.3, 0.85, 0)));
    }

    @Test
    public void turnLeftUnmirrored() {
        ChallengeDetector d = new HeadTurnDetector(ChallengeType.TURN_LEFT);
        d.begin(baseline(), false);
        assertEquals(Result.PENDING, d.onFace(f(0.3, 0.7, 10)));
        assertEquals(Result.PENDING, d.onFace(f(0.3, 0.7, 25)));
        assertEquals(Result.COMPLETED, d.onFace(f(0.3, 0.7, 28)));
    }

    @Test
    public void turnLeftMirroredUsesOppositeImageDirection() {
        ChallengeDetector d = new HeadTurnDetector(ChallengeType.TURN_LEFT);
        d.begin(baseline(), true);
        d.onFace(f(0.3, 0.7, -25));
        assertEquals(Result.COMPLETED, d.onFace(f(0.3, 0.7, -25)));
    }

    @Test
    public void turningTheWrongWayFails() {
        ChallengeDetector d = new HeadTurnDetector(ChallengeType.TURN_RIGHT);
        d.begin(baseline(), false);
        assertEquals(Result.WRONG_ACTION, d.onFace(f(0.3, 0.7, 20)));
    }

    @Test
    public void factoryCoversAllTypes() {
        for (ChallengeType t : ChallengeType.values()) {
            assertEquals(t, ChallengeDetectorFactory.DEFAULT.create(t).getType());
        }
    }
}

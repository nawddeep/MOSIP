package io.mosip.registration.livenessmanager.audit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.mosip.registration.livenessmanager.model.FaceFrame;
import io.mosip.registration.livenessmanager.model.LivenessEvent;

/** Structural guarantees behind "never show scores" and "never log frames". */
public class PrivacyContractTest {

    private static final Set<String> FORBIDDEN_WORDS = new HashSet<>(Arrays.asList(
            "score", "threshold", "model", "attack", "pad", "confidence"));

    @Test
    public void flutterEventHasNoScoreOrModelFields() {
        for (Field f : LivenessEvent.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName().toLowerCase();
            for (String w : FORBIDDEN_WORDS) assertFalse(f.getName(), name.contains(w));
            Class<?> t = f.getType();
            assertFalse(f.getName(), t == float.class || t == double.class || t == Float.class || t == Double.class);
        }
    }

    @Test
    public void auditRecordCannotCarryImageData() {
        for (Field f : LivenessAuditRecord.class.getDeclaredFields()) {
            Class<?> t = f.getType();
            assertFalse(f.getName(), t.isArray());
            assertFalse(f.getName(), t == Object.class || t == FaceFrame.class);
        }
    }

    @Test
    public void frameToStringHasNoPixels() {
        FaceFrame frame = new FaceFrame(9, 1, 2, 1, new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, false, null);
        String s = frame.toString();
        assertTrue(s.contains("2x1"));
        assertFalse(s.contains("[B"));
    }
}

package io.mosip.registration.livenessmanager.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.mosip.registration.livenessmanager.model.LivenessState;

/** Phase 1 exit check: the documented state diagram covers every state in code. */
public class ArchitectureDocTest {
    @Test
    public void stateDiagramListsEveryState() throws Exception {
        String doc = new String(Files.readAllBytes(Paths.get("../../docs/liveness/01-architecture.md")), StandardCharsets.UTF_8);
        int start = doc.indexOf("stateDiagram-v2");
        String diagram = doc.substring(start, doc.indexOf("```", start));
        for (LivenessState s : LivenessState.values()) {
            assertTrue("missing in diagram: " + s, diagram.contains(s.name()));
        }
    }
}

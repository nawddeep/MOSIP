package org.minifasnet;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Dev/test CLI: reads a RAW BGR frame (HxWx3 bytes, no header) so Java and Python see identical pixels.
 * Usage: Cli <modelDir> <frame.bgr> <width> <height> <x> <y> <w> <h>
 */
public final class Cli {
    public static void main(String[] a) throws Exception {
        byte[] frame = Files.readAllBytes(Paths.get(a[1]));
        int w = Integer.parseInt(a[2]), h = Integer.parseInt(a[3]);
        double[] box = {Double.parseDouble(a[4]), Double.parseDouble(a[5]), Double.parseDouble(a[6]), Double.parseDouble(a[7])};
        try (MiniFasNetPad pad = new MiniFasNetPad(a[0], 32)) {
            PadResult r = pad.predict(frame, w, h, box);
            System.out.printf("real_score=%.9f label=%s label_confidence=%.9f%n", r.realScore, r.label, r.labelConfidence);
        } catch (PadException e) {
            System.out.println("ERROR " + e.getCode() + ": " + e.getMessage());
            System.exit(2);
        }
    }
}

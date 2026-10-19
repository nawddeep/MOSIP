package org.minifasnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Golden scores from upstream's parity report (minifasnet/tests/out/parity_report.md), computed with the same face
 * boxes. Images are the upstream Silent-Face-Anti-Spoofing samples (not committed: faces stay out of git).
 * Skipped unless -PminifasnetSamples points at a folder containing them.
 * JPEG decoding differs slightly between ImageIO and OpenCV, so the tolerance is 0.02, not the 1e-6 of the parity report.
 */
class GoldenSamplesTest {
    static MiniFasNetPad pad;

    @BeforeAll static void load() {
        pad = new MiniFasNetPad(MiniFasNetPadTest.MODEL_DIR, 32, 1);
    }

    @AfterAll static void close() { pad.close(); }

    @ParameterizedTest
    @CsvSource({
            "image_T1.jpg, 105, 147, 207, 214, 0.994769",
            "image_F1.jpg, 178, 136, 222, 224, 0.084346",
            "image_F2.jpg, 120, 252, 252, 256, 0.181286",
    })
    void upstreamSamplesMatchGoldenScores(String file, double x, double y, double w, double h, double golden) throws Exception {
        String dir = System.getProperty("samplesDir", "");
        File f = new File(dir, file);
        assumeTrue(!dir.isEmpty() && f.isFile(), "samples not provided (-PminifasnetSamples)");
        BufferedImage img = ImageIO.read(f);
        // The upstream samples carry EXIF orientation 6 (rotate 90 deg clockwise). cv2.imread, which produced the golden
        // scores, applies it; ImageIO does not. Rotate here so the golden face boxes land on the same pixels.
        int srcW = img.getWidth(), srcH = img.getHeight();
        int width = srcH, height = srcW;
        byte[] bgr = new byte[width * height * 3];
        for (int yy = 0, i = 0; yy < height; yy++) {
            for (int xx = 0; xx < width; xx++) {
                int rgb = img.getRGB(yy, srcH - 1 - xx);
                bgr[i++] = (byte) rgb;          // B
                bgr[i++] = (byte) (rgb >> 8);   // G
                bgr[i++] = (byte) (rgb >> 16);  // R
            }
        }
        PadResult r = pad.predict(bgr, width, height, new double[] {x, y, w, h});
        assertEquals(golden, r.realScore, 0.02, file);
    }
}

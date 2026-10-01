package org.minifasnet;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dev/test CLI. Frames are RAW BGR files (HxWx3 bytes, no header) so Java and Python see identical pixels
 * (avoids JPEG-decoder differences).
 *
 *   predict <modelDir> <frame.bgr> <w> <h> <x> <y> <bw> <bh>
 *   batch   <modelDir> <manifest.tsv> <out.tsv>      manifest rows: id  file.bgr  w  h  x  y  bw  bh
 *   bench   <modelDir> <frame.bgr> <w> <h> <x> <y> <bw> <bh> <warmup> <iters>
 *   resize  <in.bgr> <w> <h> <outW> <outH> <out.bgr>
 */
public final class Cli {
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "predict": predict(a); break;
            case "batch": batch(a); break;
            case "bench": bench(a); break;
            case "resize": {  // resize <in.bgr> <w> <h> <outW> <outH> <out.bgr> -- canonical resize, for pixel parity tests
                int w = Integer.parseInt(a[2]), h = Integer.parseInt(a[3]);
                Files.write(Paths.get(a[6]), ImageOps.cropResize(Files.readAllBytes(Paths.get(a[1])), w, 0, 0, w - 1, h - 1,
                        Integer.parseInt(a[4]), Integer.parseInt(a[5])));
                break;
            }
            default: throw new IllegalArgumentException("unknown command: " + a[0]);
        }
    }

    private static double[] box(String[] a, int i) {
        return new double[] {Double.parseDouble(a[i]), Double.parseDouble(a[i + 1]),
                Double.parseDouble(a[i + 2]), Double.parseDouble(a[i + 3])};
    }

    private static void predict(String[] a) throws Exception {
        byte[] frame = Files.readAllBytes(Paths.get(a[2]));
        try (MiniFasNetPad pad = new MiniFasNetPad(a[1], 32)) {
            PadResult r = pad.predict(frame, Integer.parseInt(a[3]), Integer.parseInt(a[4]), box(a, 5));
            System.out.printf("real_score=%.9f label=%s label_confidence=%.9f%n", r.realScore, r.label, r.labelConfidence);
        } catch (PadException e) {
            System.out.println("ERROR " + e.getCode() + ": " + e.getMessage());
            System.exit(2);
        }
    }

    /** Output columns: id, status (OK | error code), real_score, label, p_model0, p_model1 (real probs). */
    private static void batch(String[] a) throws Exception {
        Path manifest = Paths.get(a[2]);
        try (MiniFasNetPad pad = new MiniFasNetPad(a[1], 32);
             BufferedReader in = Files.newBufferedReader(manifest);
             PrintStream out = new PrintStream(Files.newOutputStream(Paths.get(a[3])))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split("\t");
                byte[] frame = Files.readAllBytes(manifest.getParent().resolve(f[1]));
                try {
                    PadResult r = pad.predict(frame, Integer.parseInt(f[2]), Integer.parseInt(f[3]), box(f, 4));
                    StringBuilder sb = new StringBuilder(f[0] + "\tOK\t" + String.format("%.12f", r.realScore) + "\t" + r.label);
                    for (ModelScore m : r.perModelScores) sb.append('\t').append(String.format("%.12f", m.realProb));
                    out.println(sb);
                } catch (PadException e) {
                    out.println(f[0] + "\t" + e.getCode() + "\t\t\t\t");
                }
            }
        }
    }

    /** Per-frame latency of predict() = crop + resize + tensor + inference for all models. Warm-up excluded. */
    private static void bench(String[] a) throws Exception {
        byte[] frame = Files.readAllBytes(Paths.get(a[2]));
        int w = Integer.parseInt(a[3]), h = Integer.parseInt(a[4]);
        double[] b = box(a, 5);
        int warmup = Integer.parseInt(a[9]), iters = Integer.parseInt(a[10]);
        try (MiniFasNetPad pad = new MiniFasNetPad(a[1], 32)) {
            for (int i = 0; i < warmup; i++) pad.predict(frame, w, h, b);
            List<Double> ms = new ArrayList<>();
            for (int i = 0; i < iters; i++) {
                long t0 = System.nanoTime();
                pad.predict(frame, w, h, b);
                ms.add((System.nanoTime() - t0) / 1e6);
            }
            double[] s = ms.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double mean = Arrays.stream(s).average().orElse(0);
            double p95 = s[(int) Math.ceil(0.95 * s.length) - 1];
            System.out.printf("java iters=%d mean_ms=%.3f p95_ms=%.3f min_ms=%.3f%n", iters, mean, p95, s[0]);
        }
    }
}

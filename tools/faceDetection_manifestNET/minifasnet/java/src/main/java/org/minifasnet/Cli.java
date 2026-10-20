package org.minifasnet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

/**
 * Dev/test CLI. Frames are RAW BGR files (HxWx3 bytes, no header) so Java and Python see identical pixels
 * (avoids JPEG-decoder differences). All numbers are printed with Locale.ROOT ('.' decimal separator).
 */
public final class Cli {
    private static final String USAGE = String.join("\n",
            "usage:",
            "  predict <modelDir> <frame.bgr> <w> <h> <x> <y> <bw> <bh>",
            "  batch   <modelDir> <manifest.tsv> <out.tsv>     manifest rows: id  file.bgr  w  h  x  y  bw  bh",
            "  bench   <modelDir> <frame.bgr> <w> <h> <x> <y> <bw> <bh> <warmup> <iters> [intraOpThreads]",
            "  resize  <in.bgr> <w> <h> <outW> <outH> <out.bgr>  (canonical resize only; pixel parity tests)");

    private Cli() {}

    public static void main(String[] a) throws Exception {
        String cmd = a.length > 0 ? a[0] : "";
        int need = cmd.equals("predict") ? 9 : cmd.equals("batch") ? 4 : cmd.equals("bench") ? 11 : cmd.equals("resize") ? 7 : -1;
        if (need < 0 || a.length < need) {
            System.err.println(USAGE);
            System.exit(64);
        }
        switch (cmd) {
            case "predict": predict(a); break;
            case "batch": batch(a); break;
            case "bench": bench(a); break;
            default: resize(a); break;
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
            System.out.printf(Locale.ROOT, "real_score=%.9f label=%s label_confidence=%.9f%n", r.realScore, r.label, r.labelConfidence);
        } catch (PadException e) {
            System.out.println("ERROR " + e.getCode() + ": " + e.getMessage());
            System.exit(2);
        }
    }

    /** Output columns: id, status (OK | error code), real_score, label, real prob per model. */
    private static void batch(String[] a) throws Exception {
        Path manifest = Paths.get(a[2]).toAbsolutePath();
        Path dir = manifest.getParent();
        try (MiniFasNetPad pad = new MiniFasNetPad(a[1], 32);
             BufferedReader in = Files.newBufferedReader(manifest);
             BufferedWriter out = Files.newBufferedWriter(Paths.get(a[3]))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split("\t");
                if (f.length != 8) throw new IllegalArgumentException("manifest row needs 8 tab-separated fields: " + line);
                byte[] frame = Files.readAllBytes(dir.resolve(f[1]));
                StringBuilder sb = new StringBuilder(f[0]);
                try {
                    PadResult r = pad.predict(frame, Integer.parseInt(f[2]), Integer.parseInt(f[3]), box(f, 4));
                    sb.append("\tOK\t").append(String.format(Locale.ROOT, "%.12f", r.realScore)).append('\t').append(r.label);
                    for (ModelScore m : r.perModelScores) sb.append('\t').append(String.format(Locale.ROOT, "%.12f", m.realProb));
                } catch (PadException e) {
                    sb.append('\t').append(e.getCode()).append("\t\t");
                }
                out.write(sb.toString());
                out.newLine();
            }
        }
    }

    /** Per-frame latency of predict() = crop + resize + tensor + inference for all models. Warm-up excluded. */
    private static void bench(String[] a) throws Exception {
        byte[] frame = Files.readAllBytes(Paths.get(a[2]));
        int w = Integer.parseInt(a[3]), h = Integer.parseInt(a[4]);
        double[] b = box(a, 5);
        int warmup = Integer.parseInt(a[9]), iters = Integer.parseInt(a[10]);
        int threads = a.length > 11 ? Integer.parseInt(a[11]) : 0;
        if (warmup < 0 || iters < 1) throw new IllegalArgumentException("need warmup >= 0 and iters >= 1");
        try (MiniFasNetPad pad = new MiniFasNetPad(a[1], 32, threads)) {
            for (int i = 0; i < warmup; i++) pad.predict(frame, w, h, b);
            double[] ms = new double[iters];
            for (int i = 0; i < iters; i++) {
                long t0 = System.nanoTime();
                pad.predict(frame, w, h, b);
                ms[i] = (System.nanoTime() - t0) / 1e6;
            }
            Arrays.sort(ms);
            double mean = Arrays.stream(ms).average().orElse(0);
            double p95 = ms[(int) Math.ceil(0.95 * ms.length) - 1];
            System.out.printf(Locale.ROOT, "java iters=%d threads=%d mean_ms=%.3f p95_ms=%.3f min_ms=%.3f%n",
                    iters, threads, mean, p95, ms[0]);
        }
    }

    private static void resize(String[] a) throws Exception {
        int w = Integer.parseInt(a[2]), h = Integer.parseInt(a[3]);
        byte[] src = Files.readAllBytes(Paths.get(a[1]));
        if ((long) w * h * 3 != src.length) throw new IllegalArgumentException("input length != w*h*3");
        Files.write(Paths.get(a[6]), ImageOps.cropResize(src, w, 0, 0, w - 1, h - 1,
                Integer.parseInt(a[4]), Integer.parseInt(a[5])));
    }
}

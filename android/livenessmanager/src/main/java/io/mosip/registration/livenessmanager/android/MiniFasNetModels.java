/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.content.Context;

import org.minifasnet.MiniFasNetPad;
import org.minifasnet.PadException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.config.CanonicalJson;
import io.mosip.registration.livenessmanager.engine.EnsemblePadModel;

/**
 * Opens the vendored MiniFASNet ensemble on Android. The library loads every {@code *.onnx} in a directory, but APK
 * assets are not files, so the SHA-256-verified models are copied once into app-private no-backup storage (a folder
 * named after the manifest digest, holding only the manifest's models) and the library is pointed at it.
 */
final class MiniFasNetModels {
    private MiniFasNetModels() {
    }

    static EnsemblePadModel createPadModel(Context context, ModelManifest.PadEntry pad) {
        StringBuilder versions = new StringBuilder();
        for (ModelManifest.Entry m : pad.models) {
            if (versions.length() > 0) versions.append('+');
            versions.append(m.version);
        }
        return new EnsemblePadModel(() -> open(context, pad), "minifasnet-ensemble:" + versions);
    }

    private static EnsemblePadModel.Scorer open(Context context, ModelManifest.PadEntry pad) throws LivenessEngine.EngineException {
        File dir = materialize(context, pad);
        final MiniFasNetPad lib;
        try {
            lib = new MiniFasNetPad(dir.getPath(), pad.minFaceSize, pad.intraOpThreads);
        } catch (PadException | IllegalArgumentException e) {
            throw new LivenessEngine.EngineException("PAD models failed to load", e, true);
        }
        return new EnsemblePadModel.Scorer() {
            @Override
            public double realScore(byte[] bgr, int width, int height, double[] box) {
                return lib.predict(bgr, width, height, box).realScore;
            }

            @Override
            public void close() {
                lib.close();
            }
        };
    }

    /** Copies verified models into {@code noBackupFiles/liveness_models/<digest>/} unless already identical there. */
    static File materialize(Context context, ModelManifest.PadEntry pad) throws LivenessEngine.EngineException {
        StringBuilder key = new StringBuilder();
        for (ModelManifest.Entry m : pad.models) key.append(m.sha256);
        File dir = new File(context.getNoBackupFilesDir(), "liveness_models/" + CanonicalJson.sha256Hex(key.toString()).substring(0, 16));
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new LivenessEngine.EngineException("Cannot create model directory", null);
        }
        Set<String> expected = new HashSet<>();
        for (ModelManifest.Entry m : pad.models) {
            String name = new File(m.file).getName();
            expected.add(name);
            File target = new File(dir, name);
            if (target.isFile() && m.sha256.equalsIgnoreCase(hashOf(target))) {
                continue;
            }
            byte[] bytes = ModelManifest.loadVerified(context.getAssets(), m);
            File tmp = new File(dir, name + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes);
                out.getFD().sync();
            } catch (IOException e) {
                throw new LivenessEngine.EngineException("Cannot write model " + name, e);
            }
            if (!tmp.renameTo(target)) {
                throw new LivenessEngine.EngineException("Cannot install model " + name, null);
            }
        }
        // The library loads every *.onnx in the folder: remove anything the manifest does not list.
        File[] present = dir.listFiles();
        if (present != null) {
            for (File f : present) {
                if (!expected.contains(f.getName()) && !f.delete()) {
                    throw new LivenessEngine.EngineException("Unexpected file in model directory", null, true);
                }
            }
        }
        return dir;
    }

    private static String hashOf(File f) {
        try {
            return CanonicalJson.sha256Hex(Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            return "";
        }
    }
}

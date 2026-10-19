/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.content.res.AssetManager;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.mosip.registration.livenessmanager.api.LivenessEngine;
import io.mosip.registration.livenessmanager.config.CanonicalJson;

/**
 * Pinned model files (assets/liveness/models/manifest.json). Models are loaded into memory and their
 * SHA-256 is checked before use, so a swapped or corrupted model fails closed (ENGINE_ERROR).
 */
public final class ModelManifest {
    public static final String DIR = "liveness/models/";

    public static class Entry {
        public String file;
        public String sha256;
        public String version;
        public String license;
    }

    /** MiniFASNet ensemble (vendored org.minifasnet library): every listed model is run and averaged. */
    public static class PadEntry {
        public String engine;
        public int minFaceSize = 32;
        public int intraOpThreads = 1;
        public java.util.List<Entry> models;
        public String source;
        public String license;
    }

    public Entry faceLandmarker;
    public PadEntry pad;

    public static ModelManifest load(AssetManager assets) throws LivenessEngine.EngineException {
        try (InputStream in = assets.open(DIR + "manifest.json")) {
            ModelManifest m = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ModelManifest.class);
            if (m == null || m.faceLandmarker == null || m.pad == null || m.pad.models == null || m.pad.models.isEmpty()) {
                throw new LivenessEngine.EngineException("Model manifest incomplete", null, true);
            }
            return m;
        } catch (IOException | RuntimeException e) {
            throw new LivenessEngine.EngineException("Model manifest unreadable", e, true);
        }
    }

    /** Reads an asset fully and verifies its digest. */
    public static byte[] loadVerified(AssetManager assets, Entry entry) throws LivenessEngine.EngineException {
        byte[] bytes;
        try (InputStream in = assets.open(DIR + entry.file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4 * 1024 * 1024);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            bytes = out.toByteArray();
        } catch (IOException e) {
            throw new LivenessEngine.EngineException("Model missing: " + entry.file, e, true);
        }
        String actual = CanonicalJson.sha256Hex(bytes);
        if (!actual.equalsIgnoreCase(entry.sha256)) {
            throw new LivenessEngine.EngineException("Model digest mismatch: " + entry.file, null, true);
        }
        return bytes;
    }
}

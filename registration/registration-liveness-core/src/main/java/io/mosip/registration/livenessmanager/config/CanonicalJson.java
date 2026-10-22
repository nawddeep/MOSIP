/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical JSON used for the config integrity digest: object keys sorted (by UTF-16 code unit),
 * no whitespace, numbers in plain form without trailing zeros, strings escaped by Gson without HTML escaping.
 */
public final class CanonicalJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private CanonicalJson() {
    }

    public static String of(JsonElement e) {
        StringBuilder sb = new StringBuilder();
        write(e, sb);
        return sb.toString();
    }

    private static void write(JsonElement e, StringBuilder sb) {
        if (e == null || e.isJsonNull()) {
            sb.append("null");
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            List<String> keys = new ArrayList<>(o.keySet());
            Collections.sort(keys);
            sb.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(GSON.toJson(new JsonPrimitive(keys.get(i)))).append(':');
                write(o.get(keys.get(i)), sb);
            }
            sb.append('}');
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append(',');
                write(a.get(i), sb);
            }
            sb.append(']');
        } else {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                sb.append(new BigDecimal(p.getAsString()).stripTrailingZeros().toPlainString());
            } else {
                sb.append(GSON.toJson(p));
            }
        }
    }

    /** Lower-case hex SHA-256 of the canonical form of {@code doc} minus its "integrity" member. */
    public static String integrityDigest(JsonObject doc) {
        JsonObject copy = doc.deepCopy();
        copy.remove("integrity");
        return sha256Hex(of(copy));
    }

    public static String sha256Hex(String s) {
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : d) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

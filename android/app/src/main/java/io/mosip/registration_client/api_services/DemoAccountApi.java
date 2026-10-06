/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration_client.api_services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.mosip.registration_client.BuildConfig;

/**
 * Local demo accounts for trying the app on a phone without a MOSIP server. <b>Debug builds only</b>:
 * in release builds every call fails with NOT_AVAILABLE, so this can never become a way around MOSIP
 * authentication. Passwords are stored as PBKDF2-HMAC-SHA256 (random salt) in app-private storage,
 * never in plain text, and are never synced or uploaded.
 */
public class DemoAccountApi implements MethodChannel.MethodCallHandler {
    public static final String CHANNEL = "io.mosip.registration_client/demo_account";
    private static final String PREFS = "demo_accounts";
    private static final String KEY_PREFIX = "acct.";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public DemoAccountApi(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setup(BinaryMessenger messenger) {
        new MethodChannel(messenger, CHANNEL).setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (!BuildConfig.DEBUG) {
            result.error("NOT_AVAILABLE", "Demo accounts are disabled in release builds", null);
            return;
        }
        String username = normalise(call.argument("username"));
        switch (call.method) {
            case "isAvailable":
                result.success(true);
                break;
            case "exists":
                result.success(username != null && prefs.contains(KEY_PREFIX + username));
                break;
            case "create":
                create(username, call.argument("fullName"), call.argument("password"), result);
                break;
            case "verify":
                verify(username, call.argument("password"), result);
                break;
            default:
                result.notImplemented();
        }
    }

    private void create(String username, String fullName, String password, MethodChannel.Result result) {
        if (username == null || username.length() > 50 || password == null || password.length() < 8) {
            result.error("INVALID", "Username required; password must be at least 8 characters", null);
            return;
        }
        if (prefs.contains(KEY_PREFIX + username)) {
            result.error("EXISTS", "A demo account with this username already exists", null);
            return;
        }
        try {
            byte[] salt = new byte[SALT_BYTES];
            random.nextBytes(salt);
            JSONObject o = new JSONObject();
            o.put("fullName", fullName == null || fullName.trim().isEmpty() ? username : fullName.trim());
            o.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
            o.put("hash", Base64.encodeToString(hash(password, salt, ITERATIONS), Base64.NO_WRAP));
            o.put("iterations", ITERATIONS);
            prefs.edit().putString(KEY_PREFIX + username, o.toString()).apply();
            result.success(true);
        } catch (Exception e) {
            result.error("FAILED", "Could not create demo account", null);
        }
    }

    private void verify(String username, String password, MethodChannel.Result result) {
        String stored = username == null ? null : prefs.getString(KEY_PREFIX + username, null);
        if (stored == null || password == null) {
            result.success(null);
            return;
        }
        try {
            JSONObject o = new JSONObject(stored);
            byte[] salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP);
            byte[] expected = Base64.decode(o.getString("hash"), Base64.NO_WRAP);
            byte[] actual = hash(password, salt, o.optInt("iterations", ITERATIONS));
            if (!MessageDigest.isEqual(expected, actual)) {
                result.success(null);
                return;
            }
            Map<String, Object> user = new HashMap<>();
            user.put("username", username);
            user.put("fullName", o.optString("fullName", username));
            result.success(user);
        } catch (JSONException | RuntimeException e) {
            result.success(null);
        } catch (Exception e) {
            result.error("FAILED", "Could not verify demo account", null);
        }
    }

    static byte[] hash(String password, byte[] salt, int iterations) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private static String normalise(String username) {
        if (username == null) return null;
        String u = username.trim().toLowerCase(java.util.Locale.ROOT);
        return u.isEmpty() ? null : u;
    }
}

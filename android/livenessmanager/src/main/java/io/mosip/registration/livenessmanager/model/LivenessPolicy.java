/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-workflow liveness policy. Field names match the config JSON (see docs/liveness/liveness-config.schema.json).
 * Defaults here are used for any field missing from the JSON.
 */
public class LivenessPolicy {
    public boolean enabled = true;
    /** Mean passive live score at or above this passes without a challenge. */
    public double passiveThreshold = 0.90;
    /** Mean live score below this is rejected as an attack (PAD_FAILED) without offering a challenge. */
    public double padRejectThreshold = 0.30;
    public List<String> challengeTypes = new ArrayList<>(Arrays.asList("BLINK", "SMILE", "TURN_LEFT", "TURN_RIGHT"));
    /** How many distinct challenges are chained when passive confidence is low. */
    public int challengeCount = 1;
    public int challengeTimeoutSeconds = 8;
    /** Maximum attempts per session (the UI shows "Attempt n of maxRetries"). */
    public int maxRetries = 3;
    /** Minimum face width as a fraction of the shorter frame side. */
    public double minFaceSize = 0.25;
    public int maxFaces = 1;
    /** Good-quality frames scored before the passive decision. */
    public int passiveFrames = 5;
    /**
     * Minimum mean landmark motion (face widths per frame) across the passive window for a passive
     * pass. Live faces on a real sensor always jitter; replayed/injected identical frames do not.
     * Below it, a high passive score is escalated to an active challenge instead of passing.
     */
    public double minPassiveMotion = 0.002;
    /** Consecutive good frames needed in POSITIONING before scoring starts. */
    public int positioningStableFrames = 5;
    public Quality quality = new Quality();

    public static class Quality {
        /** Mean luma (0-255) of the face region. */
        public double minBrightness = 50;
        public double maxBrightness = 220;
        /** Variance of Laplacian of the face region (downscaled to 128px wide). */
        public double minSharpness = 25;
        /** Max face-centre movement between consecutive frames, as a fraction of face width. */
        public double maxMotion = 0.15;
    }

    public List<ChallengeType> resolveChallengeTypes() {
        List<ChallengeType> out = new ArrayList<>();
        if (challengeTypes != null) {
            for (String s : challengeTypes) {
                ChallengeType t = ChallengeType.valueOf(s);
                if (!out.contains(t)) out.add(t);
            }
        }
        return out;
    }
}

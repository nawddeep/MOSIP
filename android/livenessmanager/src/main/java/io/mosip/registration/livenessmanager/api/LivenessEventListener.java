/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.api;

import io.mosip.registration.livenessmanager.model.LivenessEvent;

/** Receives UI-safe state events (forwarded to the Flutter EventChannel). */
public interface LivenessEventListener {
    void onEvent(LivenessEvent event);
}

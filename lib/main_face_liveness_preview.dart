/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/face_liveness/liveness_debug_menu.dart';

/// Development entrypoint: opens the face liveness debug menu directly.
///   flutter run -t lib/main_face_liveness_preview.dart
/// Uses the same native bridge as the full app (MainActivity), so "Real engine" works too.
void main() {
  runApp(const MaterialApp(debugShowCheckedModeBanner: false, home: LivenessDebugMenu()));
}

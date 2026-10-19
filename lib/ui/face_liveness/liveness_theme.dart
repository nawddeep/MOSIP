/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/model/face_liveness_event.dart';

/// Single source of colours, text styles and spacing for the face liveness screens.
class LivenessTheme {
  LivenessTheme._();

  static const Color background = Color(0xFF0F1215);
  static const Color ovalFill = Color(0xFF151A20);
  static const Color text = Color(0xFFF2F4F7);
  static const Color textSecondary = Color(0xFFA5AEB8);

  static const Color checking = Color(0xFFA9BCCF);
  static const Color challenge = Color(0xFFE5B14B);
  static const Color success = Color(0xFF6CC08B);
  static const Color failure = Color(0xFFE0746B);

  static const String fontFamily = 'IBMPlexSans';

  static const double gutter = 24;
  static const double gap = 12;
  static const double buttonHeight = 52;
  static const double minTouchTarget = 44;
  static const double maxContentWidth = 560;
  static const Duration stateTransition = Duration(milliseconds: 220);

  static const TextStyle title = TextStyle(
      fontFamily: fontFamily,
      fontSize: 17,
      fontWeight: FontWeight.w600,
      color: text,
      height: 1.3);
  static const TextStyle message = TextStyle(
      fontFamily: fontFamily,
      fontSize: 26,
      fontWeight: FontWeight.w600,
      color: text,
      height: 1.2,
      letterSpacing: -0.5);
  static const TextStyle body = TextStyle(
      fontFamily: fontFamily,
      fontSize: 16,
      fontWeight: FontWeight.w400,
      color: textSecondary,
      height: 1.45);
  static const TextStyle caption = TextStyle(
      fontFamily: fontFamily,
      fontSize: 13,
      fontWeight: FontWeight.w500,
      color: textSecondary,
      height: 1.3);
  static const TextStyle button = TextStyle(
      fontFamily: fontFamily,
      fontSize: 16,
      fontWeight: FontWeight.w600,
      height: 1.2);

  /// Ring colour for a state. Positioning and guidance use a dimmed "checking" tone.
  static Color ringColor(LivenessUiState? s) {
    if (s == null) return checking.withOpacity(0.55);
    switch (s) {
      case LivenessUiState.passiveCheck:
        return checking;
      case LivenessUiState.challenge:
        return challenge;
      case LivenessUiState.success:
        return success;
      case LivenessUiState.livenessFailed:
      case LivenessUiState.padFailed:
      case LivenessUiState.challengeTimeout:
      case LivenessUiState.maxRetries:
      case LivenessUiState.deviceUnavailable:
      case LivenessUiState.cameraError:
      case LivenessUiState.invalidFrames:
      case LivenessUiState.engineError:
        return failure;
      case LivenessUiState.idle:
      case LivenessUiState.positioning:
      case LivenessUiState.noFace:
      case LivenessUiState.multipleFaces:
      case LivenessUiState.poorQuality:
        return checking.withOpacity(0.55);
    }
  }

  static ThemeData materialTheme() => ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: background,
        fontFamily: fontFamily,
        colorScheme: const ColorScheme.dark(
          primary: text,
          onPrimary: background,
          surface: ovalFill,
          onSurface: text,
          error: failure,
        ),
      );
}

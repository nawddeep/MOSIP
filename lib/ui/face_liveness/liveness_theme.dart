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

  // Desktop "ink" theme (website/ :root), see docs/ui/design-system.md.
  static const Color background = Color(0xFF0B0B0C); // ink-0
  static const Color panel = Color(0xFF111113); // ink-1, camera panel
  static const Color ovalFill = Color(0xFF161618); // ink-2
  static const Color raised = Color(0xFF1C1C1F); // ink-3
  static const Color line = Color(0x1CF5F5F7); // line-on-ink, 11%
  static const Color lineStrong = Color(0x38F5F5F7); // 22%, outlined buttons
  static const Color text = Color(0xFFF5F5F7); // fg-on-ink
  static const Color textSecondary = Color(0xFF8E8E93); // mute-on-ink

  /// Desktop focus-blue: plain accent blue is 4.3:1 on ink, below AA.
  static const Color checking = Color(0xFF86A6F5);
  static const Color challenge = Color(0xFFCDA866); // warn-ink
  static const Color success = Color(0xFF86AE90); // ok-ink
  static const Color failure = Color(0xFFD48A82); // err-ink

  /// Primary button fill (white label, 4.5:1).
  static const Color accent = Color(0xFF3B6FE8);
  static const Color onAccent = Color(0xFFFFFFFF);

  static const String fontFamily = 'IBMPlexSans';
  /// Same named script fallback as RegistrationTheme.fontFallback.
  static const List<String> fontFallback = <String>['RegistrationScriptFallback'];

  static const double gutter = 24;
  static const double gap = 12;
  static const double radius = 5;
  static const double radiusLg = 8;
  static const double radiusBadge = 3;
  static const double buttonHeight = 52;
  static const double minTouchTarget = 48;
  static const double maxContentWidth = 560;
  static const Duration stateTransition = Duration(milliseconds: 220);

  static const TextStyle title = TextStyle(
      fontFamily: fontFamily,
      fontFamilyFallback: fontFallback,
      fontSize: 17,
      fontWeight: FontWeight.w600,
      color: text,
      height: 1.3);
  static const TextStyle message = TextStyle(
      fontFamily: fontFamily,
      fontFamilyFallback: fontFallback,
      fontSize: 26,
      fontWeight: FontWeight.w600,
      color: text,
      height: 1.2,
      letterSpacing: -0.5);
  static const TextStyle body = TextStyle(
      fontFamily: fontFamily,
      fontFamilyFallback: fontFallback,
      fontSize: 16,
      fontWeight: FontWeight.w400,
      color: textSecondary,
      height: 1.45);
  static const TextStyle caption = TextStyle(
      fontFamily: fontFamily,
      fontFamilyFallback: fontFallback,
      fontSize: 13,
      fontWeight: FontWeight.w500,
      color: textSecondary,
      height: 1.3);
  static const TextStyle button = TextStyle(
      fontFamily: fontFamily,
      fontFamilyFallback: fontFallback,
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

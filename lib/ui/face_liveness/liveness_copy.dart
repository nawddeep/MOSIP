/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/ui/face_liveness/widgets/liveness_oval.dart';

/// Screen copy with multi-language ARB localization and screen-reader accessibility support.
/// Kept generic on purpose per ISO/IEC 30107 privacy requirements (no internal attack or score details).
class LivenessCopy {
  LivenessCopy._();

  // English fallback constants (preserved for backward compatibility with tests & offline state)
  static const String introTitle = 'Before we start';
  static const String introBody = 'Look at the camera. Keep your face inside the oval.';
  static const String start = 'Start';

  static const String tryAgain = 'Try again';
  static const String cancel = 'Cancel';
  static const String recoveryOptions = 'Recovery options';
  static const String retryConnection = 'Retry connection';
  static const String continueLabel = 'Continue';
  static const String offline = 'Offline';

  static String getIntroTitle([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_intro_title;
    }
    return introTitle;
  }

  static String getIntroBody([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_intro_body;
    }
    return introBody;
  }

  static String getStart([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_start;
    }
    return start;
  }

  static String getTryAgain([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_try_again;
    }
    return tryAgain;
  }

  static String getCancel([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_cancel;
    }
    return cancel;
  }

  static String getRecoveryOptions([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_recovery_options;
    }
    return recoveryOptions;
  }

  static String getRetryConnection([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_retry_connection;
    }
    return retryConnection;
  }

  static String getContinueLabel([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_continue;
    }
    return continueLabel;
  }

  static String getOffline([BuildContext? context]) {
    if (context != null) {
      final loc = AppLocalizations.of(context);
      if (loc != null) return loc.liveness_offline;
    }
    return offline;
  }

  static String workflowTitle(LivenessWorkflow w, [BuildContext? context]) {
    final loc = context != null ? AppLocalizations.of(context) : null;
    switch (w) {
      case LivenessWorkflow.resident:
        return loc?.liveness_workflow_resident ?? 'Resident registration';
      case LivenessWorkflow.operator:
        return loc?.liveness_workflow_operator ?? 'Operator login';
      case LivenessWorkflow.supervisor:
        return loc?.liveness_workflow_supervisor ?? 'Supervisor approval';
    }
  }

  static String attempt(int n, int max, [BuildContext? context]) {
    final loc = context != null ? AppLocalizations.of(context) : null;
    if (loc != null) {
      return loc.liveness_attempt(n, max);
    }
    return 'Attempt $n of $max';
  }

  static String challenge(LivenessChallenge? c, [BuildContext? context]) {
    final loc = context != null ? AppLocalizations.of(context) : null;
    switch (c) {
      case LivenessChallenge.blink:
        return loc?.liveness_challenge_blink ?? 'Please blink';
      case LivenessChallenge.smile:
        return loc?.liveness_challenge_smile ?? 'Please smile';
      case LivenessChallenge.turnLeft:
        return loc?.liveness_challenge_turn_left ?? 'Please turn your head to the left';
      case LivenessChallenge.turnRight:
        return loc?.liveness_challenge_turn_right ?? 'Please turn your head to the right';
      case null:
        return loc?.liveness_challenge_generic ?? 'Please follow the instruction';
    }
  }

  static String poorQuality(LivenessHint? h, [BuildContext? context]) {
    final loc = context != null ? AppLocalizations.of(context) : null;
    switch (h) {
      case LivenessHint.lighting:
        return loc?.liveness_hint_lighting ?? 'Improve the lighting';
      case LivenessHint.far:
        return loc?.liveness_hint_far ?? 'Move closer';
      case LivenessHint.blurry:
      case null:
        return loc?.liveness_hint_blurry ?? 'Hold still.';
    }
  }

  /// Screen-reader accessibility announcement for the countdown timer.
  static String countdown(int seconds, [BuildContext? context]) {
    final loc = context != null ? AppLocalizations.of(context) : null;
    if (loc != null) {
      return loc.liveness_semantics_countdown(seconds);
    }
    return '$seconds seconds remaining';
  }

  /// Screen-reader accessibility announcement for the guide oval state.
  static String ovalSemantics(OvalMode mode, [BuildContext? context]) {
    switch (mode) {
      case OvalMode.positioning:
        return forEvent(const FaceLivenessEvent(state: LivenessUiState.positioning), context)[0]!;
      case OvalMode.checking:
        return forEvent(const FaceLivenessEvent(state: LivenessUiState.passiveCheck), context)[0]!;
      case OvalMode.challenge:
        return challenge(null, context);
      case OvalMode.success:
        return forEvent(const FaceLivenessEvent(state: LivenessUiState.success), context)[0]!;
      case OvalMode.failure:
        return forEvent(const FaceLivenessEvent(state: LivenessUiState.livenessFailed), context)[0]!;
    }
  }

  /// Main message and optional secondary line for a state.
  static List<String?> forEvent(FaceLivenessEvent e, [BuildContext? context]) {
    final loc = context != null ? AppLocalizations.of(context) : null;
    switch (e.state) {
      case LivenessUiState.idle:
      case LivenessUiState.positioning:
        return <String?>[
          loc?.liveness_state_position_face ?? 'Position your face in the oval',
          loc?.liveness_state_look_camera ?? 'Look directly at the camera.'
        ];
      case LivenessUiState.passiveCheck:
        return <String?>[
          loc?.liveness_state_checking ?? 'Checking face liveness…',
          loc?.liveness_state_hold_still ?? 'Hold still for a moment.'
        ];
      case LivenessUiState.challenge:
        return <String?>[challenge(e.challengeType, context), null];
      case LivenessUiState.challengeTimeout:
        return <String?>[loc?.liveness_state_timeout ?? 'Time ran out. Please try again.', null];
      case LivenessUiState.noFace:
        return <String?>[loc?.liveness_state_no_face ?? 'We cannot see your face. Move into the oval.', null];
      case LivenessUiState.multipleFaces:
        return <String?>[loc?.liveness_state_multiple_faces ?? 'Only one person should be in view.', null];
      case LivenessUiState.poorQuality:
        return <String?>[poorQuality(e.hint, context), null];
      case LivenessUiState.livenessFailed:
        return <String?>[loc?.liveness_state_failed ?? 'We could not verify face liveness. Please try again.', null];
      case LivenessUiState.padFailed:
        return <String?>[loc?.liveness_state_pad_failed ?? 'Face verification could not be completed. Please try again.', null];
      case LivenessUiState.maxRetries:
        return <String?>[
          loc?.liveness_state_max_retries(e.maxAttempts) ??
              'We could not complete face verification. All ${e.maxAttempts} attempts have been used. Please follow the recovery process.',
          null
        ];
      case LivenessUiState.deviceUnavailable:
      case LivenessUiState.cameraError:
      case LivenessUiState.invalidFrames:
        return <String?>[
          loc?.liveness_state_camera_unavailable ??
              'Camera device not available. Check the device connection and try again.',
          null
        ];
      case LivenessUiState.engineError:
        return <String?>[loc?.liveness_state_engine_error ?? 'Face verification could not start. Please try again.', null];
      case LivenessUiState.success:
        return <String?>[loc?.liveness_state_success ?? 'Good, face captured successfully.', null];
    }
  }
}

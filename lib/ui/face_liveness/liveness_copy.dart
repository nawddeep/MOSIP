/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:registration_client/model/face_liveness_event.dart';

/// Screen copy. Kept verbatim from the design brief; generic on purpose (no attack or score detail).
class LivenessCopy {
  LivenessCopy._();

  static const String introTitle = 'Before we start';
  static const String introBody = 'Look at the camera. Keep your face inside the oval.';
  static const String start = 'Start';

  static const String tryAgain = 'Try again';
  static const String cancel = 'Cancel';
  static const String recoveryOptions = 'Recovery options';
  static const String retryConnection = 'Retry connection';
  static const String continueLabel = 'Continue';
  static const String offline = 'Offline';

  static String workflowTitle(LivenessWorkflow w) {
    switch (w) {
      case LivenessWorkflow.resident:
        return 'Resident registration';
      case LivenessWorkflow.operator:
        return 'Operator login';
      case LivenessWorkflow.supervisor:
        return 'Supervisor approval';
    }
  }

  static String attempt(int n, int max) => 'Attempt $n of $max';

  static String challenge(LivenessChallenge? c) {
    switch (c) {
      case LivenessChallenge.blink:
        return 'Please blink';
      case LivenessChallenge.smile:
        return 'Please smile';
      case LivenessChallenge.turnLeft:
        return 'Please turn your head to the left';
      case LivenessChallenge.turnRight:
        return 'Please turn your head to the right';
      case null:
        return 'Please follow the instruction';
    }
  }

  static String poorQuality(LivenessHint? h) {
    switch (h) {
      case LivenessHint.lighting:
        return 'Improve the lighting';
      case LivenessHint.far:
        return 'Move closer';
      case LivenessHint.blurry:
      case null:
        return 'Hold still.';
    }
  }

  /// Main message and optional secondary line for a state.
  static List<String?> forEvent(FaceLivenessEvent e) {
    switch (e.state) {
      case LivenessUiState.idle:
      case LivenessUiState.positioning:
        return <String?>['Position your face in the oval', 'Look directly at the camera.'];
      case LivenessUiState.passiveCheck:
        return <String?>['Checking face liveness…', 'Hold still for a moment.'];
      case LivenessUiState.challenge:
        return <String?>[challenge(e.challengeType), null];
      case LivenessUiState.challengeTimeout:
        return <String?>['Time ran out. Please try again.', null];
      case LivenessUiState.noFace:
        return <String?>['We cannot see your face. Move into the oval.', null];
      case LivenessUiState.multipleFaces:
        return <String?>['Only one person should be in view.', null];
      case LivenessUiState.poorQuality:
        return <String?>[poorQuality(e.hint), null];
      case LivenessUiState.livenessFailed:
        return <String?>['We could not verify face liveness. Please try again.', null];
      case LivenessUiState.padFailed:
        return <String?>['Face verification could not be completed. Please try again.', null];
      case LivenessUiState.maxRetries:
        return <String?>[
          'We could not complete face verification. All ${e.maxAttempts} attempts have been used. Please follow the recovery process.',
          null
        ];
      case LivenessUiState.deviceUnavailable:
      case LivenessUiState.cameraError:
      case LivenessUiState.invalidFrames:
        return <String?>['Camera device not available. Check the device connection and try again.', null];
      case LivenessUiState.engineError:
        return <String?>['Face verification could not start. Please try again.', null];
      case LivenessUiState.success:
        return <String?>['Good, face captured successfully.', null];
    }
  }
}

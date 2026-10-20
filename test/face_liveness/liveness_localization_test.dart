import 'package:flutter_test/flutter_test.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/widgets/liveness_oval.dart';

void main() {
  group('LivenessCopy Localization and Fallbacks', () {
    test('default constants match specification', () {
      expect(LivenessCopy.introTitle, 'Before we start');
      expect(LivenessCopy.start, 'Start');
      expect(LivenessCopy.tryAgain, 'Try again');
      expect(LivenessCopy.cancel, 'Cancel');
      expect(LivenessCopy.recoveryOptions, 'Recovery options');
      expect(LivenessCopy.retryConnection, 'Retry connection');
      expect(LivenessCopy.continueLabel, 'Continue');
      expect(LivenessCopy.offline, 'Offline');
    });

    test('getters return fallback constants when context is null', () {
      expect(LivenessCopy.getIntroTitle(null), LivenessCopy.introTitle);
      expect(LivenessCopy.getIntroBody(null), LivenessCopy.introBody);
      expect(LivenessCopy.getStart(null), LivenessCopy.start);
      expect(LivenessCopy.getTryAgain(null), LivenessCopy.tryAgain);
      expect(LivenessCopy.getCancel(null), LivenessCopy.cancel);
      expect(LivenessCopy.getRecoveryOptions(null), LivenessCopy.recoveryOptions);
      expect(LivenessCopy.getRetryConnection(null), LivenessCopy.retryConnection);
      expect(LivenessCopy.getContinueLabel(null), LivenessCopy.continueLabel);
      expect(LivenessCopy.getOffline(null), LivenessCopy.offline);
    });

    test('workflow titles return valid labels', () {
      expect(LivenessCopy.workflowTitle(LivenessWorkflow.resident), 'Resident registration');
      expect(LivenessCopy.workflowTitle(LivenessWorkflow.operator), 'Operator login');
      expect(LivenessCopy.workflowTitle(LivenessWorkflow.supervisor), 'Supervisor approval');
    });

    test('attempt formatting matches n of max', () {
      expect(LivenessCopy.attempt(1, 3), 'Attempt 1 of 3');
      expect(LivenessCopy.attempt(2, 3), 'Attempt 2 of 3');
      expect(LivenessCopy.attempt(3, 3), 'Attempt 3 of 3');
    });

    test('challenge instructions are clear and polite', () {
      expect(LivenessCopy.challenge(LivenessChallenge.blink), 'Please blink');
      expect(LivenessCopy.challenge(LivenessChallenge.smile), 'Please smile');
      expect(LivenessCopy.challenge(LivenessChallenge.turnLeft), 'Please turn your head to the left');
      expect(LivenessCopy.challenge(LivenessChallenge.turnRight), 'Please turn your head to the right');
      expect(LivenessCopy.challenge(null), 'Please follow the instruction');
    });

    test('quality hints are actionable', () {
      expect(LivenessCopy.poorQuality(LivenessHint.lighting), 'Improve the lighting');
      expect(LivenessCopy.poorQuality(LivenessHint.far), 'Move closer');
      expect(LivenessCopy.poorQuality(LivenessHint.blurry), 'Hold still.');
    });

    test('countdown timer accessibility announcement', () {
      expect(LivenessCopy.countdown(5), '5 seconds remaining');
      expect(LivenessCopy.countdown(1), '1 seconds remaining');
    });

    test('oval semantics announcements cover all modes', () {
      for (final mode in OvalMode.values) {
        final label = LivenessCopy.ovalSemantics(mode);
        expect(label, isNotEmpty);
        expect(label, isNot(contains('null')));
      }
    });

    test('privacy contract: no scores, thresholds or model names in any state copy', () {
      for (final state in LivenessUiState.values) {
        final event = FaceLivenessEvent(
          state: state,
          workflow: LivenessWorkflow.resident,
          attempt: 1,
          maxAttempts: 3,
          challengeType: LivenessChallenge.blink,
          secondsRemaining: 8,
          hint: LivenessHint.lighting,
        );
        final copy = LivenessCopy.forEvent(event);
        final combined = '${copy[0]} ${copy[1] ?? ""}';
        expect(combined, isNot(matches(RegExp(r'score|threshold|MiniFASNet|MediaPipe|ONNX|0\.\d', caseSensitive: false))));
      }
    });
  });
}

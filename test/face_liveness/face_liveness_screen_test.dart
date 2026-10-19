import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_screen.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/liveness_theme.dart';
import 'package:registration_client/ui/face_liveness/mock_face_liveness_service.dart';
import 'package:registration_client/ui/face_liveness/widgets/challenge_icon.dart';
import 'package:registration_client/ui/face_liveness/widgets/liveness_controls.dart';
import 'package:registration_client/ui/face_liveness/widgets/liveness_oval.dart';

FaceLivenessEvent ev(LivenessUiState s,
        {int attempt = 1,
        LivenessChallenge? c,
        int? secs,
        LivenessHint? hint,
        LivenessWorkflow w = LivenessWorkflow.resident}) =>
    FaceLivenessEvent(
        state: s,
        workflow: w,
        attempt: attempt,
        maxAttempts: 3,
        challengeType: c,
        secondsRemaining: secs,
        hint: hint);

class Expectation {
  const Expectation(this.text, this.buttons, this.mode);
  final String text;
  final List<String> buttons;
  final OvalMode mode;
}

final Map<LivenessUiState, FaceLivenessEvent> samples =
    <LivenessUiState, FaceLivenessEvent>{
  LivenessUiState.positioning: ev(LivenessUiState.positioning),
  LivenessUiState.passiveCheck: ev(LivenessUiState.passiveCheck),
  LivenessUiState.challenge:
      ev(LivenessUiState.challenge, c: LivenessChallenge.blink, secs: 8),
  LivenessUiState.success: ev(LivenessUiState.success),
  LivenessUiState.livenessFailed:
      ev(LivenessUiState.livenessFailed, attempt: 2),
  LivenessUiState.padFailed: ev(LivenessUiState.padFailed),
  LivenessUiState.challengeTimeout: ev(LivenessUiState.challengeTimeout),
  LivenessUiState.noFace: ev(LivenessUiState.noFace),
  LivenessUiState.multipleFaces: ev(LivenessUiState.multipleFaces),
  LivenessUiState.poorQuality:
      ev(LivenessUiState.poorQuality, hint: LivenessHint.lighting),
  LivenessUiState.deviceUnavailable: ev(LivenessUiState.deviceUnavailable),
  LivenessUiState.cameraError: ev(LivenessUiState.cameraError),
  LivenessUiState.invalidFrames: ev(LivenessUiState.invalidFrames),
  LivenessUiState.engineError: ev(LivenessUiState.engineError),
  LivenessUiState.maxRetries: ev(LivenessUiState.maxRetries, attempt: 3),
};

const String deviceMsg =
    'Camera device not available. Check the device connection and try again.';
final Map<LivenessUiState, Expectation> expected =
    <LivenessUiState, Expectation>{
  LivenessUiState.positioning: const Expectation(
      'Position your face in the oval',
      <String>['Cancel'],
      OvalMode.positioning),
  LivenessUiState.passiveCheck: const Expectation(
      'Checking face liveness…', <String>['Cancel'], OvalMode.checking),
  LivenessUiState.challenge:
      const Expectation('Please blink', <String>['Cancel'], OvalMode.challenge),
  LivenessUiState.success: const Expectation(
      'Good, face captured successfully.',
      <String>['Continue'],
      OvalMode.success),
  LivenessUiState.livenessFailed: const Expectation(
      'We could not verify face liveness. Please try again.',
      <String>['Try again', 'Cancel'],
      OvalMode.failure),
  LivenessUiState.padFailed: const Expectation(
      'Face verification could not be completed. Please try again.',
      <String>['Try again', 'Cancel'],
      OvalMode.failure),
  LivenessUiState.challengeTimeout: const Expectation(
      'Time ran out. Please try again.',
      <String>['Try again', 'Cancel'],
      OvalMode.failure),
  LivenessUiState.noFace: const Expectation(
      'We cannot see your face. Move into the oval.',
      <String>['Cancel'],
      OvalMode.positioning),
  LivenessUiState.multipleFaces: const Expectation(
      'Only one person should be in view.',
      <String>['Cancel'],
      OvalMode.positioning),
  LivenessUiState.poorQuality: const Expectation(
      'Improve the lighting', <String>['Cancel'], OvalMode.positioning),
  LivenessUiState.deviceUnavailable: const Expectation(
      deviceMsg, <String>['Retry connection', 'Cancel'], OvalMode.failure),
  LivenessUiState.cameraError: const Expectation('Camera could not start.',
      <String>['Try again', 'Cancel'], OvalMode.failure),
  LivenessUiState.invalidFrames: const Expectation(
      'Camera images could not be read.',
      <String>['Try again', 'Cancel'],
      OvalMode.failure),
  LivenessUiState.engineError: const Expectation(
      'Face verification could not start. Please try again.',
      <String>['Try again', 'Cancel'],
      OvalMode.failure),
  LivenessUiState.maxRetries: const Expectation(
      'We could not complete face verification. All 3 attempts have been used. Please follow the recovery process.',
      <String>['Recovery options'],
      OvalMode.failure),
};

Widget host(Widget child, {bool reduceMotion = false}) => MaterialApp(
      home: MediaQuery(
        data: MediaQueryData(
            size: const Size(400, 860), disableAnimations: reduceMotion),
        child: child,
      ),
    );

/// First pump starts animations triggered by the last change; the second advances them.
Future<void> step(WidgetTester tester, int ms) async {
  await tester.pump();
  await tester.pump(Duration(milliseconds: ms));
}

void main() {
  group('every state renders its copy, colour and actions', () {
    for (final s in samples.keys) {
      testWidgets('state ${s.name}', (tester) async {
        tester.view.physicalSize = const Size(800, 1720);
        tester.view.devicePixelRatio = 2;
        addTearDown(tester.view.reset);
        final mock = MockFaceLivenessService(autoPlay: false);
        await tester.pumpWidget(host(FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            initialEvent: samples[s])));
        await step(tester, 600);

        final e = expected[s]!;
        expect(find.text(e.text), findsOneWidget);
        for (final b in e.buttons) {
          expect(find.widgetWithText(LivenessPillButton, b), findsOneWidget,
              reason: b);
        }
        expect(
            find.byType(LivenessPillButton), findsNWidgets(e.buttons.length));

        final oval = tester.widget<LivenessOval>(find.byType(LivenessOval));
        expect(oval.mode, e.mode);
        expect(oval.color, LivenessTheme.ringColor(s));
        expect(find.text('Resident registration'), findsOneWidget);
        // Nothing score-like ever appears.
        expect(find.textContaining(RegExp(r'score|threshold|model|%|0\.\d')),
            findsNothing);
      });
    }
  });

  test('status colours follow the design tokens', () {
    expect(LivenessTheme.ringColor(LivenessUiState.passiveCheck),
        const Color(0xFFA9BCCF));
    expect(LivenessTheme.ringColor(LivenessUiState.challenge),
        const Color(0xFFE5B14B));
    expect(LivenessTheme.ringColor(LivenessUiState.success),
        const Color(0xFF6CC08B));
    expect(LivenessTheme.ringColor(LivenessUiState.padFailed),
        const Color(0xFFE0746B));
    expect(LivenessTheme.background, const Color(0xFF0F1215));
    expect(LivenessTheme.ovalFill, const Color(0xFF151A20));
  });

  testWidgets('intro, start, challenge, success, continue pops true',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    bool? result;
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () async {
            result = await Navigator.of(context).push<bool>(
                MaterialPageRoute<bool>(
                    builder: (_) => FaceLivenessScreen(
                        workflow: LivenessWorkflow.operator, service: mock)));
          },
          child: const Text('open'),
        ),
      ),
    ));
    await tester.tap(find.text('open'));
    await tester.pump();
    await step(tester, 500);
    expect(find.text(LivenessCopy.introTitle), findsOneWidget);
    expect(find.text(LivenessCopy.introBody), findsOneWidget);
    expect(find.text('Operator login'), findsOneWidget);

    await tester.ensureVisible(find.text('Start'));
    await tester.tap(find.text('Start'));
    await tester.pump();
    expect(mock.calls, contains('start'));

    mock.emit(ev(LivenessUiState.positioning, w: LivenessWorkflow.operator));
    await step(tester, 500);
    expect(find.text('Look directly at the camera.'), findsOneWidget);

    mock.emit(ev(LivenessUiState.challenge,
        c: LivenessChallenge.turnLeft, secs: 8, w: LivenessWorkflow.operator));
    await step(tester, 500);
    expect(find.text('Please turn your head to the left'), findsOneWidget);
    expect(find.byType(ChallengeIcon), findsOneWidget);

    mock.emit(ev(LivenessUiState.challenge,
        c: LivenessChallenge.turnLeft, secs: 4, w: LivenessWorkflow.operator));
    await step(tester, 1100);
    final oval = tester.widget<LivenessOval>(find.byType(LivenessOval));
    expect(oval.timerFraction, closeTo(3 / 8, 0.01),
        reason: 'ring drains towards (secs-1)/total');

    mock.emit(ev(LivenessUiState.success, w: LivenessWorkflow.operator));
    await step(tester, 800);
    await tester.ensureVisible(find.text('Continue'));
    await tester.tap(find.text('Continue'));
    await step(tester, 500);
    expect(result, isTrue);
  });

  testWidgets('try again sends retry; cancel sends cancel and pops false',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    bool? result;
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () async {
            result = await Navigator.of(context).push<bool>(
                MaterialPageRoute<bool>(
                    builder: (_) => FaceLivenessScreen(
                        workflow: LivenessWorkflow.resident,
                        service: mock,
                        initialEvent: ev(LivenessUiState.padFailed))));
          },
          child: const Text('open'),
        ),
      ),
    ));
    await tester.tap(find.text('open'));
    await tester.pump();
    await step(tester, 600);
    await tester.ensureVisible(find.text('Try again'));
    await tester.tap(find.text('Try again'));
    await tester.pump();
    expect(mock.calls, contains('retry'));
    mock.emit(ev(LivenessUiState.positioning, attempt: 2));
    await step(tester, 600);
    expect(find.text('Attempt 2 of 3'), findsOneWidget);
    await tester.ensureVisible(find.text('Cancel'));
    await tester.tap(find.text('Cancel'));
    await step(tester, 600);
    expect(mock.calls, contains('cancel'));
    expect(result, isFalse);
  });

  testWidgets('events for another workflow are ignored', (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(FaceLivenessScreen(
        workflow: LivenessWorkflow.supervisor,
        service: mock,
        initialEvent:
            ev(LivenessUiState.positioning, w: LivenessWorkflow.supervisor))));
    mock.emit(ev(LivenessUiState.success, w: LivenessWorkflow.resident));
    await step(tester, 600);
    expect(find.text('Good, face captured successfully.'), findsNothing);
    expect(find.text('Supervisor approval'), findsOneWidget);
  });

  testWidgets('reduce motion: no looping animation, screen settles',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            initialEvent: ev(LivenessUiState.challenge,
                c: LivenessChallenge.smile, secs: 6)),
        reduceMotion: true));
    await tester.pumpAndSettle();
    expect(find.text('Please smile'), findsOneWidget);
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('poor quality hints map to the right instruction',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            initialEvent: ev(LivenessUiState.positioning)),
        reduceMotion: true));
    mock.emit(ev(LivenessUiState.poorQuality, hint: LivenessHint.far));
    await tester.pumpAndSettle();
    expect(find.text('Move closer'), findsOneWidget);
    mock.emit(ev(LivenessUiState.poorQuality, hint: LivenessHint.blurry));
    await tester.pumpAndSettle();
    expect(find.text('Hold still.'), findsOneWidget);
  });

  testWidgets('offline indicator follows connectivity', (tester) async {
    final offline = StreamController<bool>();
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            offlineStream: offline.stream,
            initialEvent: ev(LivenessUiState.positioning)),
        reduceMotion: true));
    expect(find.text('Offline'), findsNothing);
    offline.add(true);
    await tester.pumpAndSettle();
    expect(find.text('Offline'), findsOneWidget);
    await offline.close();
  });

  testWidgets('pill buttons are 52 px tall and touch targets at least 44 px',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            initialEvent: ev(LivenessUiState.livenessFailed)),
        reduceMotion: true));
    await tester.pumpAndSettle();
    for (final e in find.byType(LivenessPillButton).evaluate()) {
      expect(tester.getSize(find.byWidget(e.widget)).height, 52);
    }
    expect(tester.getSize(find.byTooltip('Back')).height,
        greaterThanOrEqualTo(44));
  });

  testWidgets('recovery explains next steps without accepting verification',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            initialEvent: ev(LivenessUiState.maxRetries, attempt: 3)),
        reduceMotion: true));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Recovery options'));
    await tester.tap(find.text('Recovery options'));
    await tester.pumpAndSettle();
    expect(find.text('Face verification needs assistance'), findsOneWidget);
    expect(
        find.textContaining('Ask the registration operator'), findsOneWidget);
    expect(mock.calls, isNot(contains('start')));
    await tester.tap(find.text('Stay here'));
    await tester.pumpAndSettle();
    expect(find.text('Recovery options'), findsOneWidget);
  });

  testWidgets(
      'challenge progress survives guidance and advances on a new action',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            initialEvent: ev(LivenessUiState.challenge,
                c: LivenessChallenge.blink, secs: 8)),
        reduceMotion: true));
    await tester.pumpAndSettle();
    expect(find.text('Challenge 1'), findsOneWidget);
    mock.emit(ev(LivenessUiState.poorQuality, hint: LivenessHint.lighting));
    await tester.pumpAndSettle();
    mock.emit(
        ev(LivenessUiState.challenge, c: LivenessChallenge.blink, secs: 5));
    await tester.pumpAndSettle();
    expect(find.text('Challenge 1'), findsOneWidget);
    mock.emit(
        ev(LivenessUiState.challenge, c: LivenessChallenge.smile, secs: 8));
    await tester.pumpAndSettle();
    expect(find.text('Action detected. Challenge 2'), findsOneWidget);
  });

  testWidgets('small landscape screen and large text keep recovery reachable',
      (tester) async {
    tester.view.physicalSize = const Size(568, 320);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(MaterialApp(
        home: MediaQuery(
            data: const MediaQueryData(
                textScaleFactor: 2, disableAnimations: true),
            child: FaceLivenessScreen(
                workflow: LivenessWorkflow.resident,
                service: mock,
                initiallyOffline: true,
                initialEvent: ev(LivenessUiState.maxRetries, attempt: 3)))));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.ensureVisible(find.text('Recovery options'));
    await tester.ensureVisible(find.text('Recovery options'));
    await tester.tap(find.text('Recovery options'));
    await tester.pumpAndSettle();
    expect(find.text('Face verification needs assistance'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('operator success describes verification rather than capture',
      (tester) async {
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.operator,
            service: mock,
            initialEvent:
                ev(LivenessUiState.success, w: LivenessWorkflow.operator)),
        reduceMotion: true));
    await tester.pumpAndSettle();
    expect(find.text('Face verification successful.'), findsOneWidget);
    expect(find.text('Continue to complete authentication.'), findsOneWidget);
  });

  testWidgets(
      'denied camera permission prevents engine start and offers settings',
      (tester) async {
    const permissionChannel =
        MethodChannel('flutter.baseflow.com/permissions/methods');
    tester.binding.defaultBinaryMessenger
        .setMockMethodCallHandler(permissionChannel, (call) async => 4);
    addTearDown(() => tester.binding.defaultBinaryMessenger
        .setMockMethodCallHandler(permissionChannel, null));
    final mock = MockFaceLivenessService(autoPlay: false);
    await tester.pumpWidget(host(
        FaceLivenessScreen(
            workflow: LivenessWorkflow.resident,
            service: mock,
            requestCameraPermission: () async => false),
        reduceMotion: true));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Start'));
    await tester.tap(find.text('Start'));
    await tester.pumpAndSettle();
    expect(find.text('Allow camera access'), findsOneWidget);
    expect(find.text('Open settings'), findsOneWidget);
    expect(mock.calls, isNot(contains('start')));
  });

  group('event parsing', () {
    test('maps native names', () {
      final e = FaceLivenessEvent.fromMap(<String, Object?>{
        'state': 'PASSIVE_CHECK',
        'workflow': 'SUPERVISOR',
        'attempt': 2,
        'maxAttempts': 3,
        'challengeType': 'TURN_LEFT',
        'secondsRemaining': 5,
        'hint': 'BLURRY',
      });
      expect(e.state, LivenessUiState.passiveCheck);
      expect(e.workflow, LivenessWorkflow.supervisor);
      expect(e.challengeType, LivenessChallenge.turnLeft);
      expect(e.hint, LivenessHint.blurry);
      expect(e.attempt, 2);
    });

    test('unknown state fails closed to engineError', () {
      final e = FaceLivenessEvent.fromMap(
          <String, Object?>{'state': 'SOMETHING_NEW', 'workflow': 'RESIDENT'});
      expect(e.state, LivenessUiState.engineError);
    });

    test('every Dart state has a native counterpart name', () {
      const native = <String>[
        'IDLE',
        'POSITIONING',
        'PASSIVE_CHECK',
        'CHALLENGE',
        'SUCCESS',
        'LIVENESS_FAILED',
        'PAD_FAILED',
        'CHALLENGE_TIMEOUT',
        'NO_FACE',
        'MULTIPLE_FACES',
        'POOR_QUALITY',
        'DEVICE_UNAVAILABLE',
        'CAMERA_ERROR',
        'INVALID_FRAMES',
        'ENGINE_ERROR',
        'MAX_RETRIES'
      ];
      for (final n in native) {
        final e = FaceLivenessEvent.fromMap(
            <String, Object?>{'state': n, 'workflow': 'RESIDENT'});
        expect(e.state == LivenessUiState.engineError, n == 'ENGINE_ERROR',
            reason: n);
      }
      expect(LivenessUiState.values.length, native.length);
    });
  });
}

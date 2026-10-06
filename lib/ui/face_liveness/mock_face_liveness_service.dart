/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'dart:async';
import 'dart:math' as math;

import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/platform_spi/face_liveness_service.dart';

/// Scenarios the mock can play end to end.
enum MockLivenessScript { passivePass, challengePass, padFailThenPass, maxRetries, deviceErrorThenPass }

/// Event-stream mock of the native engine, for UI development, widget tests and the debug menu.
/// With [autoPlay] it plays [script] on a timer; otherwise call [emit] to drive it.
class MockFaceLivenessService implements FaceLivenessService {
  MockFaceLivenessService({
    this.script = MockLivenessScript.challengePass,
    this.maxAttempts = 3,
    this.enabled = true,
    this.autoPlay = true,
    this.stepDuration = const Duration(milliseconds: 1500),
  });

  final MockLivenessScript script;
  final int maxAttempts;
  final bool enabled;
  final bool autoPlay;
  final Duration stepDuration;

  final StreamController<FaceLivenessEvent> _events = StreamController<FaceLivenessEvent>.broadcast();
  final StreamController<PreviewTransform> _previews = StreamController<PreviewTransform>.broadcast();
  final List<Timer> _timers = <Timer>[];
  final math.Random _random = math.Random();
  LivenessWorkflow _workflow = LivenessWorkflow.resident;
  int _attempt = 1;

  /// Commands received, for tests.
  final List<String> calls = <String>[];

  void emit(FaceLivenessEvent e) => _events.add(e);

  @override
  Stream<FaceLivenessEvent> get events => _events.stream;

  @override
  Stream<PreviewTransform> get previewTransforms => _previews.stream;

  @override
  Future<bool> isEnabled(LivenessWorkflow workflow) async => enabled;

  @override
  Future<int> getMaxAttempts(LivenessWorkflow workflow) async => maxAttempts;

  @override
  Future<int?> start(LivenessWorkflow workflow) async {
    calls.add('start');
    _workflow = workflow;
    _attempt = 1;
    if (autoPlay) _playAttempt();
    return null;
  }

  @override
  Future<void> retry() async {
    calls.add('retry');
    if (!autoPlay) return;
    final last = _lastState;
    if (last == LivenessUiState.livenessFailed ||
        last == LivenessUiState.padFailed ||
        last == LivenessUiState.challengeTimeout) {
      _attempt++;
    }
    _playAttempt(afterError: last == LivenessUiState.deviceUnavailable);
  }

  @override
  Future<void> cancel() async {
    calls.add('cancel');
    _cancelTimers();
  }

  @override
  Future<bool> setSource({required bool mock, String? path, bool mirrored = false}) async => true;

  LivenessUiState? _lastState;

  void _cancelTimers() {
    for (final t in _timers) {
      t.cancel();
    }
    _timers.clear();
  }

  void _at(int step, FaceLivenessEvent e) {
    _timers.add(Timer(stepDuration * step, () {
      _lastState = e.state;
      _events.add(e);
    }));
  }

  FaceLivenessEvent _e(LivenessUiState s, {LivenessChallenge? c, int? secs, LivenessHint? hint}) =>
      FaceLivenessEvent(
          state: s,
          workflow: _workflow,
          attempt: _attempt,
          maxAttempts: maxAttempts,
          challengeType: c,
          secondsRemaining: secs,
          hint: hint);

  void _playAttempt({bool afterError = false}) {
    _cancelTimers();
    if (script == MockLivenessScript.deviceErrorThenPass && !afterError && _lastState == null) {
      _at(1, _e(LivenessUiState.deviceUnavailable));
      return;
    }
    _at(0, _e(LivenessUiState.positioning));
    _at(1, _e(LivenessUiState.poorQuality, hint: LivenessHint.lighting));
    _at(2, _e(LivenessUiState.positioning));
    _at(3, _e(LivenessUiState.passiveCheck));
    switch (script) {
      case MockLivenessScript.passivePass:
      case MockLivenessScript.deviceErrorThenPass:
        _at(4, _e(LivenessUiState.success));
        break;
      case MockLivenessScript.challengePass:
        final c = LivenessChallenge.values[_random.nextInt(LivenessChallenge.values.length)];
        for (var i = 0; i < 3; i++) {
          _at(4 + i, _e(LivenessUiState.challenge, c: c, secs: 8 - i));
        }
        _at(7, _e(LivenessUiState.success));
        break;
      case MockLivenessScript.padFailThenPass:
        _at(4, _attempt == 1 ? _e(LivenessUiState.padFailed) : _e(LivenessUiState.success));
        break;
      case MockLivenessScript.maxRetries:
        if (_attempt < maxAttempts) {
          _at(4, _e(LivenessUiState.challenge, c: LivenessChallenge.blink, secs: 8));
          _at(5, _e(LivenessUiState.challengeTimeout));
        } else {
          _at(4, _e(LivenessUiState.maxRetries));
        }
        break;
    }
  }

  void dispose() {
    _cancelTimers();
    _events.close();
    _previews.close();
  }
}

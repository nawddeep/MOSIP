/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

/// Workflows that run face liveness. Names match the native enum.
enum LivenessWorkflow { resident, operator, supervisor }

/// Liveness states sent by native code (see docs/liveness/01-architecture.md).
enum LivenessUiState {
  idle,
  positioning,
  passiveCheck,
  challenge,
  success,
  livenessFailed,
  padFailed,
  challengeTimeout,
  noFace,
  multipleFaces,
  poorQuality,
  deviceUnavailable,
  cameraError,
  invalidFrames,
  engineError,
  maxRetries,
}

enum LivenessChallenge { blink, smile, turnLeft, turnRight }

enum LivenessHint { lighting, far, blurry }

String _toNative(String camel) => camel
    .replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m[1]}_${m[2]}')
    .toUpperCase();

T? _fromNative<T extends Enum>(List<T> values, Object? raw) {
  if (raw == null) return null;
  for (final v in values) {
    if (_toNative(v.name) == raw) return v;
  }
  return null;
}

String workflowToNative(LivenessWorkflow w) => _toNative(w.name);

/// UI-safe event. Contains no scores, thresholds or model details by design.
class FaceLivenessEvent {
  const FaceLivenessEvent({
    required this.state,
    required this.workflow,
    this.attempt = 1,
    this.maxAttempts = 3,
    this.challengeType,
    this.secondsRemaining,
    this.hint,
  });

  final LivenessUiState state;
  final LivenessWorkflow workflow;
  final int attempt;
  final int maxAttempts;
  final LivenessChallenge? challengeType;
  final int? secondsRemaining;
  final LivenessHint? hint;

  /// Parses the EventChannel map. Unknown states map to engineError so the UI fails closed.
  factory FaceLivenessEvent.fromMap(Map<dynamic, dynamic> map) {
    return FaceLivenessEvent(
      state: _fromNative(LivenessUiState.values, map['state']) ??
          LivenessUiState.engineError,
      workflow: _fromNative(LivenessWorkflow.values, map['workflow']) ??
          LivenessWorkflow.resident,
      attempt: (map['attempt'] as int?) ?? 1,
      maxAttempts: (map['maxAttempts'] as int?) ?? 3,
      challengeType: _fromNative(LivenessChallenge.values, map['challengeType']),
      secondsRemaining: map['secondsRemaining'] as int?,
      hint: _fromNative(LivenessHint.values, map['hint']),
    );
  }

  bool get isFailure =>
      state == LivenessUiState.livenessFailed ||
      state == LivenessUiState.padFailed ||
      state == LivenessUiState.challengeTimeout ||
      state == LivenessUiState.maxRetries;

  bool get isSystemError =>
      state == LivenessUiState.deviceUnavailable ||
      state == LivenessUiState.cameraError ||
      state == LivenessUiState.invalidFrames ||
      state == LivenessUiState.engineError;

  bool get isGuidance =>
      state == LivenessUiState.noFace ||
      state == LivenessUiState.multipleFaces ||
      state == LivenessUiState.poorQuality;

  @override
  String toString() =>
      'FaceLivenessEvent($state, $workflow, $attempt/$maxAttempts, $challengeType, $secondsRemaining, $hint)';
}

/// How to draw the raw native preview texture upright.
class PreviewTransform {
  const PreviewTransform({
    required this.width,
    required this.height,
    required this.rotationDegrees,
    required this.mirror,
  });

  final int width;
  final int height;
  final int rotationDegrees;
  final bool mirror;

  factory PreviewTransform.fromMap(Map<dynamic, dynamic> m) => PreviewTransform(
        width: (m['width'] as int?) ?? 480,
        height: (m['height'] as int?) ?? 640,
        rotationDegrees: (m['rotationDegrees'] as int?) ?? 0,
        mirror: (m['mirror'] as bool?) ?? true,
      );
}

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/platform_spi/face_liveness_service.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/liveness_theme.dart';
import 'package:registration_client/ui/face_liveness/widgets/challenge_icon.dart';
import 'package:registration_client/ui/face_liveness/widgets/liveness_controls.dart';
import 'package:registration_client/ui/face_liveness/widgets/liveness_oval.dart';

/// Face liveness screen. Rendering is a pure function of the latest [FaceLivenessEvent]; the only
/// local UI state is the "Before we start" intro shown until the user presses Start.
///
/// Pops `true` on SUCCESS + Continue, `false` on cancel / back / recovery.
class FaceLivenessScreen extends StatefulWidget {
  const FaceLivenessScreen({
    super.key,
    required this.workflow,
    required this.service,
    this.requestCameraPermission,
    this.offlineStream,
    this.initiallyOffline = false,
    this.onRecovery,
    this.initialEvent,
  });

  final LivenessWorkflow workflow;
  final FaceLivenessService service;

  /// Asks for the camera permission before starting; null skips the request (mock/debug).
  final Future<bool> Function()? requestCameraPermission;
  final Stream<bool>? offlineStream;
  final bool initiallyOffline;

  /// Called for "Recovery options" after all attempts are used. Defaults to closing the screen.
  final VoidCallback? onRecovery;

  /// Debug menu only: render this state immediately, skipping the intro.
  final FaceLivenessEvent? initialEvent;

  @override
  State<FaceLivenessScreen> createState() => FaceLivenessScreenState();
}

class FaceLivenessScreenState extends State<FaceLivenessScreen> {
  FaceLivenessEvent? _event;
  int _maxAttempts = 3;
  int? _textureId;
  PreviewTransform? _transform;
  bool _offline = false;
  bool _busy = false;
  StreamSubscription<FaceLivenessEvent>? _eventSub;
  StreamSubscription<PreviewTransform>? _previewSub;
  StreamSubscription<bool>? _offlineSub;

  LivenessChallenge? _challenge;
  int _challengeTotalSeconds = 1;

  FaceLivenessEvent? get event => _event;

  @override
  void initState() {
    super.initState();
    _offline = widget.initiallyOffline;
    _event = widget.initialEvent;
    if (_event != null) _trackChallenge(_event!);
    _eventSub = widget.service.events.listen(_onEvent);
    _previewSub = widget.service.previewTransforms.listen((t) {
      if (mounted) setState(() => _transform = t);
    });
    _offlineSub = widget.offlineStream?.listen((o) {
      if (mounted) setState(() => _offline = o);
    });
    widget.service.getMaxAttempts(widget.workflow).then((m) {
      if (mounted) setState(() => _maxAttempts = m);
    });
  }

  @override
  void dispose() {
    _eventSub?.cancel();
    _previewSub?.cancel();
    _offlineSub?.cancel();
    final s = _event?.state;
    if (s != null && s != LivenessUiState.success && s != LivenessUiState.maxRetries) {
      widget.service.cancel();
    }
    super.dispose();
  }

  void _onEvent(FaceLivenessEvent e) {
    if (!mounted || e.workflow != widget.workflow) return;
    setState(() {
      _busy = false;
      _event = e;
      _maxAttempts = e.maxAttempts;
      _trackChallenge(e);
      if (e.state == LivenessUiState.success || e.state == LivenessUiState.maxRetries) {
        // Camera is released natively on these states; drop the stale texture.
        _textureId = null;
      }
    });
  }

  void _trackChallenge(FaceLivenessEvent e) {
    if (e.state == LivenessUiState.challenge) {
      if (e.challengeType != _challenge) {
        _challenge = e.challengeType;
        _challengeTotalSeconds = (e.secondsRemaining ?? 1).clamp(1, 600);
      }
    } else {
      _challenge = null;
    }
  }

  Future<void> _start() async {
    setState(() => _busy = true);
    final permission = widget.requestCameraPermission;
    if (permission != null) {
      // If denied, native start reports DEVICE_UNAVAILABLE and the error screen explains it.
      await permission();
    }
    try {
      final id = await widget.service.start(widget.workflow);
      if (mounted) setState(() => _textureId = id);
    } catch (_) {
      _onEvent(
          FaceLivenessEvent(state: LivenessUiState.engineError, workflow: widget.workflow, maxAttempts: _maxAttempts));
    }
  }

  Future<void> _retry() async {
    setState(() => _busy = true);
    try {
      await widget.service.retry();
    } catch (_) {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _cancel() async {
    await widget.service.cancel();
    if (mounted) Navigator.of(context).maybePop(false);
  }

  void _recovery() {
    if (widget.onRecovery != null) {
      widget.onRecovery!();
    } else {
      Navigator.of(context).maybePop(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    final duration = reduceMotion ? Duration.zero : LivenessTheme.stateTransition;
    final e = _event;
    return Theme(
      data: LivenessTheme.materialTheme(),
      child: WillPopScope(
        onWillPop: () async {
          if (e?.state != LivenessUiState.success) await widget.service.cancel();
          return true;
        },
        child: Scaffold(
          backgroundColor: LivenessTheme.background,
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                // Keep a phone-like column on tablets so lines and pill buttons stay readable.
                constraints: const BoxConstraints(maxWidth: LivenessTheme.maxContentWidth),
                child: LayoutBuilder(builder: (context, constraints) {
                  final ovalWidth = (constraints.maxWidth * 0.72).clamp(180.0, 320.0).toDouble();
                  final ovalSpace = constraints.maxHeight * 0.55;
                  final fitWidth = ovalWidth * 1.3 + 24 > ovalSpace ? (ovalSpace - 24) / 1.3 : ovalWidth;
                  return Column(
                    children: <Widget>[
                      _topBar(e),
                      Expanded(
                        child: Center(
                          child: LivenessOval(
                            key: const ValueKey<String>('liveness-oval'),
                            state: e?.state,
                            color: LivenessTheme.ringColor(e?.state),
                            timerFraction: 1,
                            reduceMotion: reduceMotion,
                            width: fitWidth.clamp(140.0, 320.0).toDouble(),
                            preview: _preview(),
                          ).wrapTimer(e, _challengeTotalSeconds, reduceMotion),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: LivenessTheme.gutter),
                        child: _fadeRise(duration, _message(e, reduceMotion), _messageKey(e)),
                      ),
                      const SizedBox(height: LivenessTheme.gap * 2),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(
                            LivenessTheme.gutter, 0, LivenessTheme.gutter, LivenessTheme.gutter),
                        child: _fadeRise(duration, _buttons(e), 'buttons-${_buttonGroup(e)}'),
                      ),
                    ],
                  );
                }),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _topBar(FaceLivenessEvent? e) {
    final attempt = e?.attempt ?? 1;
    final failedNow = e != null && (e.isFailure && e.state != LivenessUiState.maxRetries);
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 4, LivenessTheme.gutter, 0),
      child: Row(
        children: <Widget>[
          SizedBox(
            width: 48,
            height: 48,
            child: IconButton(
              tooltip: 'Back',
              icon: const Icon(Icons.arrow_back, color: LivenessTheme.text),
              onPressed: _cancel,
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(LivenessCopy.workflowTitle(widget.workflow),
                    style: LivenessTheme.title, overflow: TextOverflow.ellipsis),
                const SizedBox(height: 4),
                AttemptIndicator(
                  attempt: e?.state == LivenessUiState.maxRetries ? _maxAttempts : attempt,
                  maxAttempts: _maxAttempts,
                  currentFailed: failedNow || e?.state == LivenessUiState.maxRetries,
                ),
              ],
            ),
          ),
          if (_offline) const OfflineIndicator(),
        ],
      ),
    );
  }

  Widget? _preview() {
    final id = _textureId;
    // No live preview on error states: the camera is stopped and the texture would be black.
    if (id == null || (_event != null && (_event!.isSystemError || _event!.isFailure))) return null;
    final t = _transform ?? const PreviewTransform(width: 480, height: 640, rotationDegrees: 0, mirror: true);
    final quarterTurns = (t.rotationDegrees ~/ 90) % 4;
    final rotated = quarterTurns.isOdd;
    final w = (rotated ? t.height : t.width).toDouble();
    final h = (rotated ? t.width : t.height).toDouble();
    Widget tex = RotatedBox(quarterTurns: quarterTurns, child: Texture(textureId: id));
    if (t.mirror) {
      tex = Transform(alignment: Alignment.center, transform: Matrix4.diagonal3Values(-1, 1, 1), child: tex);
    }
    return FittedBox(fit: BoxFit.cover, clipBehavior: Clip.hardEdge, child: SizedBox(width: w, height: h, child: tex));
  }

  String _messageKey(FaceLivenessEvent? e) {
    if (e == null) return 'intro';
    final copy = LivenessCopy.forEvent(e);
    return '${e.state}-${copy[0]}';
  }

  Widget _message(FaceLivenessEvent? e, bool reduceMotion) {
    if (e == null) {
      return const Column(mainAxisSize: MainAxisSize.min, children: <Widget>[
        Text(LivenessCopy.introTitle, style: LivenessTheme.message, textAlign: TextAlign.center),
        SizedBox(height: 8),
        Text(LivenessCopy.introBody, style: LivenessTheme.body, textAlign: TextAlign.center),
      ]);
    }
    final copy = LivenessCopy.forEvent(e);
    return Semantics(
      liveRegion: true,
      child: Column(mainAxisSize: MainAxisSize.min, children: <Widget>[
        if (e.state == LivenessUiState.challenge && e.challengeType != null) ...<Widget>[
          ChallengeIcon(challenge: e.challengeType!, reduceMotion: reduceMotion),
          const SizedBox(height: 10),
        ],
        Text(copy[0]!, style: LivenessTheme.message, textAlign: TextAlign.center),
        if (copy[1] != null) ...<Widget>[
          const SizedBox(height: 8),
          Text(copy[1]!, style: LivenessTheme.body, textAlign: TextAlign.center),
        ],
      ]),
    );
  }

  String _buttonGroup(FaceLivenessEvent? e) {
    if (e == null) return 'intro';
    if (e.state == LivenessUiState.success) return 'success';
    if (e.state == LivenessUiState.maxRetries) return 'max';
    if (e.isFailure) return 'failure';
    if (e.isSystemError) return 'system';
    return 'running';
  }

  Widget _buttons(FaceLivenessEvent? e) {
    final group = _buttonGroup(e);
    final children = <Widget>[];
    switch (group) {
      case 'intro':
        children.add(LivenessPillButton(label: LivenessCopy.start, onPressed: _busy ? null : _start));
        break;
      case 'success':
        children.add(LivenessPillButton(
            label: LivenessCopy.continueLabel, onPressed: () => Navigator.of(context).maybePop(true)));
        break;
      case 'max':
        children.add(LivenessPillButton(label: LivenessCopy.recoveryOptions, onPressed: _recovery));
        break;
      case 'failure':
        children.add(LivenessPillButton(label: LivenessCopy.tryAgain, onPressed: _busy ? null : _retry));
        children.add(const SizedBox(height: LivenessTheme.gap));
        children.add(LivenessPillButton(label: LivenessCopy.cancel, onPressed: _cancel, primary: false));
        break;
      case 'system':
        children.add(LivenessPillButton(label: LivenessCopy.retryConnection, onPressed: _busy ? null : _retry));
        children.add(const SizedBox(height: LivenessTheme.gap));
        children.add(LivenessPillButton(label: LivenessCopy.cancel, onPressed: _cancel, primary: false));
        break;
      default:
        children.add(LivenessPillButton(label: LivenessCopy.cancel, onPressed: _cancel, primary: false));
    }
    return Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: children);
  }

  Widget _fadeRise(Duration duration, Widget child, String key) {
    return AnimatedSwitcher(
      duration: duration,
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      transitionBuilder: (child, animation) => FadeTransition(
        opacity: animation,
        child: SlideTransition(
          position: Tween<Offset>(begin: const Offset(0, 0.12), end: Offset.zero).animate(animation),
          child: child,
        ),
      ),
      child: KeyedSubtree(key: ValueKey<String>(key), child: child),
    );
  }
}

extension _TimerRing on LivenessOval {
  /// During a challenge, drains the ring smoothly between the native once-per-second updates.
  Widget wrapTimer(FaceLivenessEvent? e, int totalSeconds, bool reduceMotion) {
    if (e == null || e.state != LivenessUiState.challenge || e.secondsRemaining == null) return this;
    final secs = e.secondsRemaining!;
    final target = ((secs - 1).clamp(0, totalSeconds)) / totalSeconds;
    final from = secs / totalSeconds;
    return TweenAnimationBuilder<double>(
      key: ValueKey<String>('timer-${e.challengeType}'),
      tween: Tween<double>(begin: from, end: reduceMotion ? from : target),
      duration: reduceMotion ? Duration.zero : const Duration(seconds: 1),
      builder: (context, value, _) => LivenessOval(
        key: key,
        state: state,
        color: color,
        timerFraction: value,
        preview: preview,
        reduceMotion: reduceMotion,
        width: width,
      ),
    );
  }
}

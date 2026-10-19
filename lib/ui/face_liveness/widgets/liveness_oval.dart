/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/liveness_theme.dart';

/// Visual mode of the oval, derived from the liveness state.
enum OvalMode { positioning, checking, challenge, success, failure }

OvalMode ovalModeFor(LivenessUiState? s) {
  if (s == null) return OvalMode.positioning;
  switch (s) {
    case LivenessUiState.passiveCheck:
      return OvalMode.checking;
    case LivenessUiState.challenge:
      return OvalMode.challenge;
    case LivenessUiState.success:
      return OvalMode.success;
    case LivenessUiState.livenessFailed:
    case LivenessUiState.padFailed:
    case LivenessUiState.challengeTimeout:
    case LivenessUiState.maxRetries:
    case LivenessUiState.deviceUnavailable:
    case LivenessUiState.cameraError:
    case LivenessUiState.invalidFrames:
    case LivenessUiState.engineError:
      return OvalMode.failure;
    case LivenessUiState.idle:
    case LivenessUiState.positioning:
    case LivenessUiState.noFace:
    case LivenessUiState.multipleFaces:
    case LivenessUiState.poorQuality:
      return OvalMode.positioning;
  }
}

/// Large oval face guide with the camera preview clipped inside and a state ring around it.
///
/// Animations: breathing ring + floating silhouette (positioning), orbiting arc + scan line
/// (checking), draining ring (challenge), green fill + self-drawing check (success), red ring +
/// single shake (failure). With [reduceMotion] all loops stop and changes are instant.
class LivenessOval extends StatefulWidget {
  const LivenessOval({
    super.key,
    required this.state,
    required this.color,
    this.timerFraction = 1,
    this.preview,
    this.reduceMotion = false,
    this.width = 280,
  });

  final LivenessUiState? state;
  final Color color;

  /// Remaining challenge time as a fraction of the total (1 = full ring).
  final double timerFraction;
  final Widget? preview;
  final bool reduceMotion;
  final double width;

  OvalMode get mode => ovalModeFor(state);

  @override
  State<LivenessOval> createState() => _LivenessOvalState();
}

class _LivenessOvalState extends State<LivenessOval> with TickerProviderStateMixin {
  late final AnimationController _loop =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 2400));
  late final AnimationController _success =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 750));
  late final AnimationController _shake =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 450));

  @override
  void initState() {
    super.initState();
    _syncAnimations(null);
  }

  @override
  void didUpdateWidget(LivenessOval old) {
    super.didUpdateWidget(old);
    _syncAnimations(old.mode);
  }

  void _syncAnimations(OvalMode? previous) {
    final mode = widget.mode;
    final loops = !widget.reduceMotion &&
        (mode == OvalMode.positioning || mode == OvalMode.checking || mode == OvalMode.challenge);
    if (loops && !_loop.isAnimating) {
      _loop.repeat();
    } else if (!loops && _loop.isAnimating) {
      _loop.stop();
    }
    if (mode == OvalMode.success && previous != OvalMode.success) {
      widget.reduceMotion ? _success.value = 1 : _success.forward(from: 0);
    } else if (mode != OvalMode.success) {
      _success.value = 0;
    }
    if (mode == OvalMode.failure && previous != OvalMode.failure && !widget.reduceMotion) {
      _shake.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _loop.dispose();
    _success.dispose();
    _shake.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final w = widget.width;
    final h = w * 1.3;
    return AnimatedBuilder(
      animation: Listenable.merge(<Listenable>[_loop, _success, _shake]),
      builder: (context, _) {
        // One damped oscillation for the failure shake.
        final t = _shake.value;
        final dx = t == 0 || t == 1 ? 0.0 : math.sin(t * math.pi * 4) * 10 * (1 - t);
        return Semantics(
          label: LivenessCopy.ovalSemantics(widget.mode, context),
          liveRegion: true,
          child: Transform.translate(
            offset: Offset(dx, 0),
            child: SizedBox(
              width: w + 24,
              height: h + 24,
              child: Stack(
                alignment: Alignment.center,
                children: <Widget>[
                  ClipOval(
                    child: Container(
                      width: w,
                      height: h,
                      color: LivenessTheme.ovalFill,
                      child: widget.preview,
                    ),
                  ),
                  CustomPaint(
                    size: Size(w + 24, h + 24),
                    painter: OvalRingPainter(
                      mode: widget.mode,
                      color: widget.color,
                      loop: widget.reduceMotion ? 0 : _loop.value,
                      timerFraction: widget.timerFraction.clamp(0.0, 1.0).toDouble(),
                      success: _success.value,
                      showSilhouette: widget.preview == null,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class OvalRingPainter extends CustomPainter {
  OvalRingPainter({
    required this.mode,
    required this.color,
    required this.loop,
    required this.timerFraction,
    required this.success,
    required this.showSilhouette,
  });

  final OvalMode mode;
  final Color color;
  final double loop;
  final double timerFraction;
  final double success;
  final bool showSilhouette;

  static const double stroke = 5;

  @override
  void paint(Canvas canvas, Size size) {
    final ovalRect = Rect.fromLTWH(12, 12, size.width - 24, size.height - 24);
    final ringPath = _ovalPathFromTop(ovalRect);
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;

    // Faint track always visible.
    canvas.drawOval(ovalRect, base..color = LivenessTheme.textSecondary.withOpacity(0.18));

    switch (mode) {
      case OvalMode.positioning:
        final breathe = 0.5 + 0.5 * math.sin(loop * 2 * math.pi);
        canvas.drawOval(
            ovalRect,
            base
              ..color = color.withOpacity(0.45 + 0.4 * breathe)
              ..strokeWidth = stroke + 1.5 * breathe);
        if (showSilhouette) {
          _drawSilhouette(canvas, ovalRect, math.sin(loop * 2 * math.pi) * 4);
        }
        break;
      case OvalMode.checking:
        canvas.drawOval(ovalRect, base..color = color.withOpacity(0.35));
        _drawScanLine(canvas, ovalRect, loop);
        _drawArc(canvas, ringPath, (loop) % 1.0, 0.12, base..color = color..strokeWidth = stroke + 1);
        break;
      case OvalMode.challenge:
        _drawArc(canvas, ringPath, 0, timerFraction, base..color = color);
        break;
      case OvalMode.success:
        _drawArc(canvas, ringPath, 0, success, base..color = color);
        _drawCheck(canvas, ovalRect, success);
        break;
      case OvalMode.failure:
        canvas.drawOval(ovalRect, base..color = color);
        break;
    }
  }

  /// Oval path that starts at 12 o'clock and runs clockwise, so arcs drain/fill from the top.
  Path _ovalPathFromTop(Rect r) {
    final path = Path();
    const n = 120;
    for (var i = 0; i <= n; i++) {
      final a = -math.pi / 2 + 2 * math.pi * i / n;
      final p = Offset(r.center.dx + r.width / 2 * math.cos(a), r.center.dy + r.height / 2 * math.sin(a));
      if (i == 0) {
        path.moveTo(p.dx, p.dy);
      } else {
        path.lineTo(p.dx, p.dy);
      }
    }
    return path;
  }

  void _drawArc(Canvas canvas, Path ring, double start, double length, Paint paint) {
    if (length <= 0) return;
    final metric = ring.computeMetrics().first;
    final total = metric.length;
    final from = start * total;
    final to = from + length * total;
    if (to <= total) {
      canvas.drawPath(metric.extractPath(from, to), paint);
    } else {
      canvas.drawPath(metric.extractPath(from, total), paint);
      canvas.drawPath(metric.extractPath(0, to - total), paint);
    }
  }

  void _drawScanLine(Canvas canvas, Rect r, double t) {
    // Ping-pong sweep, eased.
    final p = t < 0.5 ? t * 2 : (1 - t) * 2;
    final eased = Curves.easeInOut.transform(p);
    final y = r.top + r.height * (0.12 + 0.76 * eased);
    canvas.save();
    canvas.clipPath(Path()..addOval(r));
    final band = Rect.fromLTWH(r.left, y - 18, r.width, 36);
    canvas.drawRect(
      band,
      Paint()
        ..shader = ui.Gradient.linear(
          band.topCenter,
          band.bottomCenter,
          <Color>[
            LivenessTheme.checking.withOpacity(0),
            LivenessTheme.checking.withOpacity(0.22),
            LivenessTheme.checking.withOpacity(0),
          ],
          const <double>[0, 0.5, 1],
        ),
    );
    canvas.restore();
  }

  void _drawSilhouette(Canvas canvas, Rect r, double dy) {
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..color = LivenessTheme.textSecondary.withOpacity(0.35);
    final cx = r.center.dx;
    final head = Rect.fromCenter(
        center: Offset(cx, r.top + r.height * 0.42 + dy), width: r.width * 0.46, height: r.height * 0.42);
    canvas.drawOval(head, paint);
    final shoulders = Path()
      ..moveTo(r.left + r.width * 0.16, r.bottom - r.height * 0.04 + dy)
      ..quadraticBezierTo(cx, r.top + r.height * 0.62 + dy, r.right - r.width * 0.16, r.bottom - r.height * 0.04 + dy);
    canvas.drawPath(shoulders, paint);
  }

  void _drawCheck(Canvas canvas, Rect r, double t) {
    if (t <= 0) return;
    final scale = Curves.easeOutBack.transform(t.clamp(0.0, 1.0).toDouble());
    final c = r.center;
    final s = r.width * 0.16 * scale;
    canvas.drawCircle(c, s * 1.25, Paint()..color = LivenessTheme.success.withOpacity(0.18 * scale));
    final path = Path()
      ..moveTo(c.dx - s * 0.55, c.dy + s * 0.02)
      ..lineTo(c.dx - s * 0.12, c.dy + s * 0.45)
      ..lineTo(c.dx + s * 0.62, c.dy - s * 0.42);
    final metric = path.computeMetrics().first;
    final drawn = metric.extractPath(0, metric.length * Curves.easeOut.transform(t));
    canvas.drawPath(
        drawn,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 6
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round
          ..color = LivenessTheme.success);
  }

  @override
  bool shouldRepaint(OvalRingPainter oldDelegate) =>
      oldDelegate.mode != mode ||
      oldDelegate.color != color ||
      oldDelegate.loop != loop ||
      oldDelegate.timerFraction != timerFraction ||
      oldDelegate.success != success ||
      oldDelegate.showSilhouette != showSilhouette;
}

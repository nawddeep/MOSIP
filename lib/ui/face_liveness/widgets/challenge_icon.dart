/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/liveness_theme.dart';

/// Line icon for the active challenge, gently animated (eye closes, mouth curves, arrow slides).
class ChallengeIcon extends StatefulWidget {
  const ChallengeIcon(
      {super.key,
      required this.challenge,
      this.reduceMotion = false,
      this.size = 48});

  final LivenessChallenge challenge;
  final bool reduceMotion;
  final double size;

  @override
  State<ChallengeIcon> createState() => _ChallengeIconState();
}

class _ChallengeIconState extends State<ChallengeIcon>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 1600));

  @override
  void initState() {
    super.initState();
    if (!widget.reduceMotion) _c.repeat();
  }

  @override
  void didUpdateWidget(ChallengeIcon old) {
    super.didUpdateWidget(old);
    if (widget.reduceMotion && _c.isAnimating) {
      _c.stop();
    } else if (!widget.reduceMotion && !_c.isAnimating) {
      _c.repeat();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: LivenessCopy.challenge(widget.challenge, context),
      child: AnimatedBuilder(
        animation: _c,
        builder: (_, __) => CustomPaint(
          size: Size.square(widget.size),
          painter: _ChallengeIconPainter(
              widget.challenge, widget.reduceMotion ? 0.0 : _c.value),
        ),
      ),
    );
  }
}

class _ChallengeIconPainter extends CustomPainter {
  _ChallengeIconPainter(this.challenge, this.t);

  final LivenessChallenge challenge;
  final double t;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.06
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = LivenessTheme.challenge;
    final w = size.width, h = size.height;
    switch (challenge) {
      case LivenessChallenge.blink:
        // Lid closes in the middle of the cycle.
        final closed = t > 0.45 && t < 0.6 ? 1.0 : 0.0;
        final open = 1 - closed;
        final eye = Path()
          ..moveTo(w * 0.1, h * 0.5)
          ..quadraticBezierTo(
              w * 0.5, h * (0.5 - 0.38 * open), w * 0.9, h * 0.5)
          ..quadraticBezierTo(
              w * 0.5, h * (0.5 + 0.38 * open), w * 0.1, h * 0.5);
        canvas.drawPath(eye, p);
        if (open > 0) canvas.drawCircle(Offset(w * 0.5, h * 0.5), w * 0.1, p);
        break;
      case LivenessChallenge.smile:
        canvas.drawCircle(Offset(w * 0.5, h * 0.5), w * 0.42, p);
        canvas.drawCircle(Offset(w * 0.36, h * 0.4), w * 0.02, p);
        canvas.drawCircle(Offset(w * 0.64, h * 0.4), w * 0.02, p);
        final curve = 0.06 + 0.12 * (0.5 + 0.5 * math.sin(t * 2 * math.pi));
        final mouth = Path()
          ..moveTo(w * 0.32, h * 0.6)
          ..quadraticBezierTo(
              w * 0.5, h * (0.6 + curve * 1.6), w * 0.68, h * 0.6);
        canvas.drawPath(mouth, p);
        break;
      case LivenessChallenge.turnLeft:
      case LivenessChallenge.turnRight:
        // Arrows point to the user's left/right as seen in the mirrored selfie preview.
        final dir = challenge == LivenessChallenge.turnLeft ? -1.0 : 1.0;
        final slide = dir * w * 0.1 * math.sin(t * 2 * math.pi).abs();
        canvas.drawCircle(Offset(w * 0.5, h * 0.42), w * 0.22, p);
        final y = h * 0.82;
        final tip = Offset(w * 0.5 + dir * w * 0.36 + slide, y);
        canvas.drawLine(Offset(w * 0.5 - dir * w * 0.2 + slide, y), tip, p);
        canvas.drawLine(tip, Offset(tip.dx - dir * w * 0.12, y - h * 0.1), p);
        canvas.drawLine(tip, Offset(tip.dx - dir * w * 0.12, y + h * 0.1), p);
        break;
    }
  }

  @override
  bool shouldRepaint(_ChallengeIconPainter old) =>
      old.t != t || old.challenge != challenge;
}

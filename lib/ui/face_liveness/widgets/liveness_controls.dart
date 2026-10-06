/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/liveness_theme.dart';

/// Pill button, 52 px tall (touch target well above 44 px).
class LivenessPillButton extends StatelessWidget {
  const LivenessPillButton({super.key, required this.label, required this.onPressed, this.primary = true});

  final String label;
  final VoidCallback? onPressed;
  final bool primary;

  @override
  Widget build(BuildContext context) {
    final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(LivenessTheme.buttonHeight / 2));
    const size = Size.fromHeight(LivenessTheme.buttonHeight);
    if (primary) {
      return ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          minimumSize: size,
          shape: shape,
          elevation: 0,
          backgroundColor: LivenessTheme.text,
          foregroundColor: LivenessTheme.background,
          textStyle: LivenessTheme.button,
        ),
        child: Text(label),
      );
    }
    return OutlinedButton(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        minimumSize: size,
        shape: shape,
        foregroundColor: LivenessTheme.text,
        side: const BorderSide(color: LivenessTheme.textSecondary),
        textStyle: LivenessTheme.button,
      ),
      child: Text(label),
    );
  }
}

/// Dots plus "Attempt n of N". Past attempts red, current attempt filled, future attempts outlined.
class AttemptIndicator extends StatelessWidget {
  const AttemptIndicator({super.key, required this.attempt, required this.maxAttempts, this.currentFailed = false});

  final int attempt;
  final int maxAttempts;
  final bool currentFailed;

  @override
  Widget build(BuildContext context) {
    final dots = <Widget>[];
    for (var i = 1; i <= maxAttempts; i++) {
      final failed = i < attempt || (i == attempt && currentFailed);
      final current = i == attempt && !currentFailed;
      dots.add(Container(
        width: 8,
        height: 8,
        margin: const EdgeInsets.symmetric(horizontal: 3),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: failed ? LivenessTheme.failure : (current ? LivenessTheme.text : Colors.transparent),
          border: Border.all(color: failed ? LivenessTheme.failure : LivenessTheme.textSecondary, width: 1.2),
        ),
      ));
    }
    return Semantics(
      label: LivenessCopy.attempt(attempt, maxAttempts),
      excludeSemantics: true,
      child: Row(mainAxisSize: MainAxisSize.min, children: <Widget>[
        ...dots,
        const SizedBox(width: 8),
        Text(LivenessCopy.attempt(attempt, maxAttempts), style: LivenessTheme.caption),
      ]),
    );
  }
}

class OfflineIndicator extends StatelessWidget {
  const OfflineIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0x99A5AEB8)),
      ),
      child: const Row(mainAxisSize: MainAxisSize.min, children: <Widget>[
        Icon(Icons.cloud_off_outlined, size: 14, color: LivenessTheme.textSecondary),
        SizedBox(width: 4),
        Text(LivenessCopy.offline, style: LivenessTheme.caption),
      ]),
    );
  }
}

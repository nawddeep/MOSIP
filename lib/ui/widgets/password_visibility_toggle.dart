/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/utils/app_config.dart';

/// Eye icon placed in a password field's suffix to show or hide the typed password.
class PasswordVisibilityToggle extends StatelessWidget {
  const PasswordVisibilityToggle({
    super.key,
    required this.obscured,
    required this.onToggle,
    this.size = 22,
  });

  /// True while the password is hidden.
  final bool obscured;
  final VoidCallback onToggle;
  final double size;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return IconButton(
      tooltip: obscured ? l10n.show_password : l10n.hide_password,
      constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
      icon: Icon(
        obscured ? Icons.visibility_outlined : Icons.visibility_off_outlined,
        size: size,
        color: appGreyShade,
      ),
      onPressed: onToggle,
    );
  }
}

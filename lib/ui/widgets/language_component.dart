/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

class LanguageComponent extends StatefulWidget {
  const LanguageComponent({
    super.key,
    required this.title,
    required this.isSelected,
    required this.onTap,
    required this.isMobile,
    required this.isFreezed,
    required this.isDisabled,
  });
  final String title;
  final bool isSelected;
  final VoidCallback onTap;
  final bool isMobile;
  final bool isFreezed;
  final bool isDisabled;

  @override
  State<LanguageComponent> createState() => _LanguageComponentState();
}

class _LanguageComponentState extends State<LanguageComponent> {
  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      label: Text(widget.title),
      selected: widget.isSelected || widget.isFreezed,
      onSelected:
          widget.isDisabled || widget.isFreezed ? null : (_) => widget.onTap(),
      // Desktop chip: pill, hairline; selected = sunken fill, ink border and
      // a check mark (not colour alone).
      selectedColor: RegistrationTheme.sunken,
      backgroundColor: RegistrationTheme.card,
      labelStyle: RegistrationTheme.small.copyWith(
          fontWeight: widget.isSelected || widget.isFreezed
              ? FontWeight.w600
              : FontWeight.w500,
          color: widget.isDisabled
              ? RegistrationTheme.muted
              : RegistrationTheme.ink),
      side: BorderSide(
          color: widget.isSelected || widget.isFreezed
              ? RegistrationTheme.ink
              : RegistrationTheme.lineStrong),
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(RegistrationTheme.rPill)),
      padding: const EdgeInsets.symmetric(
          horizontal: RegistrationTheme.s2, vertical: RegistrationTheme.s2),
      materialTapTargetSize: MaterialTapTargetSize.padded,
    );
  }
}

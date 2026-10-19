/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/utils/app_config.dart';

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
      selectedColor: const Color(0xFFECF2FF),
      backgroundColor: Colors.white,
      labelStyle: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w500,
          color: widget.isDisabled ? appBlackShade3 : appSolidPrimary),
      side: BorderSide(
          color: widget.isSelected ? appSolidPrimary : const Color(0xFFE2E8F2)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
      materialTapTargetSize: MaterialTapTargetSize.padded,
    );
  }
}

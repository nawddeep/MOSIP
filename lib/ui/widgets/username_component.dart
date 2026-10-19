/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/ui/widgets/language_component.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

class UsernameComponent extends StatefulWidget {
  const UsernameComponent({
    Key? key,
    required this.onTap,
    required this.languages,
    required this.mp,
    required this.onChanged,
    required this.isDisabled,
    required this.isMobile,
    this.onTapSignup,
  }) : super(key: key);

  final List<String?> languages;
  final Map<String, String> mp;
  final VoidCallback onTap;
  final Function onChanged;
  final bool isDisabled;
  final bool isMobile;

  /// Opens the sign-up / request-access page. Hidden when null.
  final VoidCallback? onTapSignup;

  @override
  State<UsernameComponent> createState() => _UsernameComponentState();
}

class _UsernameComponentState extends State<UsernameComponent> {
  String? selectedLanguage;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return AutofillGroup(
        child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l.language,
            style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: RegistrationTheme.ink)),
        const SizedBox(height: 12),
        _getLanguageRowList(),
        const SizedBox(height: 24),
        TextField(
          autofillHints: const [AutofillHints.username],
          textInputAction: TextInputAction.next,
          onSubmitted: (_) {
            if (!widget.isDisabled) widget.onTap();
          },
          decoration: InputDecoration(
              labelText: l.username,
              hintText: l.enter_username,
              prefixIcon: const Icon(Icons.person_outline_rounded)),
          style: const TextStyle(fontSize: 16, color: RegistrationTheme.ink),
          onChanged: (v) => widget.onChanged(v),
        ),
        const SizedBox(height: 24),
        ElevatedButton(
            onPressed: widget.isDisabled ? null : widget.onTap,
            child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Text(l.next_button),
              const SizedBox(width: 10),
              const Icon(Icons.arrow_forward_rounded, size: 18),
            ])),
        if (widget.onTapSignup != null) ...[
          const SizedBox(height: 20),
          Wrap(
              alignment: WrapAlignment.center,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(l.signup_no_account,
                    style: const TextStyle(
                        fontSize: 14, color: RegistrationTheme.muted)),
                TextButton(
                    key: const ValueKey<String>('signup-link'),
                    onPressed: widget.onTapSignup,
                    child: Text(l.signup_link)),
              ]),
        ],
      ],
    ));
  }

  _getLanguageRowList() {
    final appLanguage = Provider.of<GlobalProvider>(context, listen: false);
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: widget.languages.map((e) {
        return LanguageComponent(
          title: widget.mp[e] ?? "",
          isDisabled:
              context.read<GlobalProvider>().disabledLanguageMap[e] ?? false,
          isSelected: context.read<GlobalProvider>().selectedLanguage == e,
          onTap: () {
            selectedLanguage = e;
            appLanguage.toggleLocale(e!);
          },
          isMobile: widget.isMobile,
          isFreezed: false,
        );
      }).toList(),
    );
  }
}

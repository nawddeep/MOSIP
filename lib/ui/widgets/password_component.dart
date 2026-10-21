/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:registration_client/ui/widgets/password_visibility_toggle.dart';
import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

class PasswordComponent extends StatefulWidget {
  const PasswordComponent({
    Key? key,
    required this.onTapLogin,
    required this.onTapBack,
    required this.onChanged,
    required this.isLoggingIn,
    required this.isDisabled,
    required this.isMobile,
    required this.onTapForgotPassword,
  }) : super(key: key);

  final VoidCallback onTapLogin;
  final VoidCallback onTapBack;
  final Function onChanged;
  final bool isLoggingIn;
  final bool isDisabled;
  final bool isMobile;
  final VoidCallback onTapForgotPassword;

  @override
  State<PasswordComponent> createState() => _PasswordComponentState();
}

class _PasswordComponentState extends State<PasswordComponent> {
  bool _obscurePassword = true;

  VoidCallback get onTapLogin => widget.onTapLogin;
  VoidCallback get onTapBack => widget.onTapBack;
  Function get onChanged => widget.onChanged;
  bool get isLoggingIn => widget.isLoggingIn;
  bool get isDisabled => widget.isDisabled;
  bool get isMobile => widget.isMobile;
  VoidCallback get onTapForgotPassword => widget.onTapForgotPassword;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return AutofillGroup(
        child:
            Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      TextField(
        obscureText: _obscurePassword,
        autofillHints: const [AutofillHints.password],
        textInputAction: TextInputAction.done,
        onSubmitted: (_) {
          if (!isDisabled && !isLoggingIn) onTapLogin();
        },
        onChanged: (v) => onChanged(v),
        decoration: InputDecoration(
            labelText: l.password,
            hintText: l.enter_password,
            prefixIcon: const Icon(Icons.lock_outline_rounded),
            suffixIcon: PasswordVisibilityToggle(
                obscured: _obscurePassword,
                onToggle: () =>
                    setState(() => _obscurePassword = !_obscurePassword))),
        style: RegistrationTheme.body,
      ),
      const SizedBox(height: RegistrationTheme.s2),
      Align(
          alignment: AlignmentDirectional.centerEnd,
          child: TextButton(
              onPressed: isLoggingIn ? null : onTapForgotPassword,
              child: Text(l.forgot_password))),
      const SizedBox(height: RegistrationTheme.s2),
      ElevatedButton(
          onPressed: isDisabled || isLoggingIn ? null : onTapLogin,
          child: isLoggingIn
              ? const SizedBox(
                  width: RegistrationTheme.iconInline,
                  height: RegistrationTheme.iconInline,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: RegistrationTheme.onAccent))
              : Text(l.login_button)),
      const SizedBox(height: RegistrationTheme.s3),
      OutlinedButton(
          onPressed: isLoggingIn ? null : onTapBack,
          child: Text(l.back_button)),
    ]));
  }
}

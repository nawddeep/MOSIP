/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/foundation.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/platform_android/demo_account_service.dart';
import 'package:registration_client/ui/widgets/password_visibility_toggle.dart';
import 'package:registration_client/utils/app_config.dart';
import 'package:url_launcher/url_launcher.dart';

/// Global param holding the administrator email that receives account requests (optional).
const String signupAdminEmailKey = 'mosip.registration.signup.admin.email';

/// "Sign up" for the registration client.
///
/// MOSIP operator accounts are created by an administrator (Keycloak + Admin Portal), so this page
/// does not create a server account. It prepares an access request for the administrator. In debug
/// builds it can also create a local demo account for exploring the app without a server.
///
/// Pops with the demo username when a demo account was created, otherwise null.
class SignupPage extends StatefulWidget {
  const SignupPage({
    super.key,
    this.adminEmailLoader,
    this.demoAccounts,
    this.launcher,
  });

  /// Returns the configured admin email, or null. Defaults to no address (user picks the recipient).
  final Future<String?> Function()? adminEmailLoader;
  final DemoAccountService? demoAccounts;

  /// Opens a mailto URI; returns false if no email app is available. Injected for tests.
  final Future<bool> Function(Uri uri)? launcher;

  @override
  State<SignupPage> createState() => _SignupPageState();
}

enum _Role { officer, supervisor }

class _SignupPageState extends State<SignupPage> {
  final _requestForm = GlobalKey<FormState>();
  final _demoForm = GlobalKey<FormState>();
  final _fullName = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _username = TextEditingController();
  final _centre = TextEditingController();
  final _note = TextEditingController();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  _Role _role = _Role.officer;
  bool _obscure = true;
  bool _obscureConfirm = true;
  bool _demoAvailable = false;
  bool _busy = false;

  DemoAccountService get _demo =>
      widget.demoAccounts ?? DemoAccountService.instance;

  static final RegExp _emailRe = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
  static final RegExp _phoneRe = RegExp(r'^\+?[0-9 ()-]{7,20}$');
  static final RegExp _usernameRe = RegExp(r'^[A-Za-z0-9._-]{3,50}$');

  @override
  void initState() {
    super.initState();
    if (kDebugMode) {
      _demo.isAvailable().then((v) {
        if (mounted) setState(() => _demoAvailable = v);
      });
    }
  }

  @override
  void dispose() {
    for (final c in <TextEditingController>[
      _fullName,
      _email,
      _phone,
      _username,
      _centre,
      _note,
      _password,
      _confirm
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  String? _required(String? v, AppLocalizations l) =>
      (v == null || v.trim().isEmpty) ? l.signup_required : null;

  String _requestText(AppLocalizations l) {
    final role = _role == _Role.officer
        ? l.signup_role_officer
        : l.signup_role_supervisor;
    final lines = <String>[
      '${l.signup_full_name}: ${_fullName.text.trim()}',
      '${l.signup_email}: ${_email.text.trim()}',
      if (_phone.text.trim().isNotEmpty)
        '${l.signup_phone}: ${_phone.text.trim()}',
      '${l.signup_username}: ${_username.text.trim()}',
      '${l.signup_centre}: ${_centre.text.trim()}',
      '${l.signup_role}: $role',
      if (_note.text.trim().isNotEmpty)
        '${l.signup_note}: ${_note.text.trim()}',
    ];
    return lines.join('\n');
  }

  Future<void> _sendRequest() async {
    final l = AppLocalizations.of(context)!;
    if (!_requestForm.currentState!.validate()) return;
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    String? admin;
    try {
      admin = await (widget.adminEmailLoader?.call() ??
          Future<String?>.value(null));
    } catch (_) {
      admin = null;
    }
    final body = _requestText(l);
    final uri = Uri(
      scheme: 'mailto',
      path: admin ?? '',
      query:
          'subject=${Uri.encodeComponent('${l.signup_title}: ${_username.text.trim()}')}'
          '&body=${Uri.encodeComponent(body)}',
    );
    bool opened = false;
    try {
      opened = widget.launcher != null
          ? await widget.launcher!(uri)
          : await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      opened = false;
    }
    if (!opened) {
      await Clipboard.setData(ClipboardData(text: body));
    }
    if (!mounted) return;
    setState(() => _busy = false);
    messenger.showSnackBar(SnackBar(
        content:
            Text(opened ? l.signup_request_sent : l.signup_request_copied)));
  }

  Future<void> _createDemo() async {
    final l = AppLocalizations.of(context)!;
    final requestOk = _requestFieldValid(_fullName.text, l) &&
        _usernameRe.hasMatch(_username.text.trim());
    if (!requestOk) {
      // Name and username come from the main form; show its errors.
      _requestForm.currentState!.validate();
      return;
    }
    if (!_demoForm.currentState!.validate()) return;
    setState(() => _busy = true);
    final username = _username.text.trim().toLowerCase();
    try {
      await _demo.create(
          username: username,
          fullName: _fullName.text.trim(),
          password: _password.text);
      if (!mounted) return;
      Navigator.of(context).pop(username);
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() => _busy = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(
            e.code == 'EXISTS' ? l.signup_demo_exists : (e.message ?? e.code)),
      ));
    }
  }

  bool _requestFieldValid(String v, AppLocalizations l) =>
      _required(v, l) == null;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Theme(
        data: RegistrationTheme.light(),
        child: Scaffold(
          backgroundColor: RegistrationTheme.canvas,
          appBar: AppBar(
            backgroundColor: RegistrationTheme.canvas,
            foregroundColor: RegistrationTheme.ink,
            title: Text(l.signup_title),
          ),
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
                  children: <Widget>[
                    Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                            color: RegistrationTheme.softBlue,
                            borderRadius: BorderRadius.circular(16)),
                        child: Text(l.signup_intro,
                            style: const TextStyle(
                                fontSize: 15,
                                height: 1.5,
                                color: RegistrationTheme.ink))),
                    const SizedBox(height: 20),
                    Form(
                      key: _requestForm,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: <Widget>[
                          _field(_fullName, l.signup_full_name,
                              validator: (v) => _required(v, l),
                              autofill: AutofillHints.name),
                          _field(_email, l.signup_email,
                              keyboard: TextInputType.emailAddress,
                              autofill: AutofillHints.email,
                              validator: (v) =>
                                  _required(v, l) ??
                                  (_emailRe.hasMatch(v!.trim())
                                      ? null
                                      : l.signup_invalid_email)),
                          _field(_phone, l.signup_phone,
                              keyboard: TextInputType.phone,
                              autofill: AutofillHints.telephoneNumber,
                              validator: (v) => (v == null ||
                                      v.trim().isEmpty ||
                                      _phoneRe.hasMatch(v.trim()))
                                  ? null
                                  : l.signup_invalid_phone),
                          _field(_username, l.signup_username,
                              autofill: AutofillHints.newUsername,
                              validator: (v) =>
                                  _required(v, l) ??
                                  (_usernameRe.hasMatch(v!.trim())
                                      ? null
                                      : l.signup_invalid_username)),
                          _field(_centre, l.signup_centre,
                              validator: (v) => _required(v, l)),
                          Padding(
                            padding: const EdgeInsets.only(bottom: 14),
                            child: DropdownButtonFormField<_Role>(
                              value: _role,
                              decoration: _decoration(l.signup_role),
                              items: <DropdownMenuItem<_Role>>[
                                DropdownMenuItem<_Role>(
                                    value: _Role.officer,
                                    child: Text(l.signup_role_officer)),
                                DropdownMenuItem<_Role>(
                                    value: _Role.supervisor,
                                    child: Text(l.signup_role_supervisor)),
                              ],
                              onChanged: (r) =>
                                  setState(() => _role = r ?? _Role.officer),
                            ),
                          ),
                          _field(_note, l.signup_note, maxLines: 3),
                          const SizedBox(height: 6),
                          SizedBox(
                            height: 52,
                            child: ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: appSolidPrimary,
                                foregroundColor: appWhite,
                                shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(14)),
                              ),
                              onPressed: _busy ? null : _sendRequest,
                              child: Text(l.signup_send,
                                  style: const TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600)),
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (_demoAvailable) ...<Widget>[
                      const SizedBox(height: 28),
                      _demoSection(l),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ));
  }

  Widget _demoSection(AppLocalizations l) {
    return Container(
      key: const ValueKey<String>('demo-section'),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFAEF),
        border: Border.all(color: const Color(0xFFEEDDB5)),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Form(
        key: _demoForm,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Row(children: <Widget>[
              const Icon(Icons.science_outlined, color: Color(0xFF8A6100)),
              const SizedBox(width: 8),
              Expanded(
                child: Text(l.signup_demo_title,
                    style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: appBlack)),
              ),
            ]),
            const SizedBox(height: 8),
            Text(l.signup_demo_body,
                style: const TextStyle(
                    fontSize: 14, height: 1.4, color: appBlack)),
            const SizedBox(height: 14),
            _field(_password, l.signup_password,
                obscure: _obscure,
                autofill: AutofillHints.newPassword,
                suffix: PasswordVisibilityToggle(
                    obscured: _obscure,
                    onToggle: () => setState(() => _obscure = !_obscure)),
                validator: (v) =>
                    (v == null || v.length < 8) ? l.signup_password_min : null),
            _field(_confirm, l.signup_confirm_password,
                obscure: _obscureConfirm,
                suffix: PasswordVisibilityToggle(
                    obscured: _obscureConfirm,
                    onToggle: () =>
                        setState(() => _obscureConfirm = !_obscureConfirm)),
                validator: (v) =>
                    v != _password.text ? l.signup_password_mismatch : null),
            SizedBox(
              height: 52,
              child: OutlinedButton(
                style: OutlinedButton.styleFrom(
                  foregroundColor: appSolidPrimary,
                  side: const BorderSide(color: appSolidPrimary),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14)),
                ),
                onPressed: _busy ? null : _createDemo,
                child: Text(l.signup_create_demo,
                    style: const TextStyle(
                        fontSize: 16, fontWeight: FontWeight.w600)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  InputDecoration _decoration(String label, {Widget? suffix}) =>
      InputDecoration(
        labelText: label,
        suffixIcon: suffix,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(14),
            borderSide: const BorderSide(color: RegistrationTheme.line)),
        filled: true,
        fillColor: Colors.white,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
      );

  Widget _field(
    TextEditingController c,
    String label, {
    String? Function(String?)? validator,
    TextInputType? keyboard,
    bool obscure = false,
    Widget? suffix,
    int maxLines = 1,
    String? autofill,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: TextFormField(
        controller: c,
        validator: validator,
        keyboardType: keyboard,
        obscureText: obscure,
        maxLines: obscure ? 1 : maxLines,
        autofillHints: autofill == null ? null : <String>[autofill],
        decoration: _decoration(label, suffix: suffix),
      ),
    );
  }
}

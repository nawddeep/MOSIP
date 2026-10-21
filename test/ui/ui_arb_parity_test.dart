import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Strings added by the UI redesign (prefix `ui_`) must exist, non-empty, in
/// every shipped locale.
void main() {
  Map<String, dynamic> load(String lang) =>
      jsonDecode(File('assets/l10n/app_$lang.arb').readAsStringSync())
          as Map<String, dynamic>;

  final en = load('en');
  final keys = en.keys.where((k) => k.startsWith('ui_')).toList();

  test('redesign strings exist', () => expect(keys, isNotEmpty));

  for (final lang in const ['fr', 'hi', 'ar', 'kn', 'ta']) {
    test('$lang has every ui_ string', () {
      final arb = load(lang);
      final missing = keys
          .where((k) => arb[k] is! String || (arb[k] as String).trim().isEmpty)
          .toList();
      expect(missing, isEmpty, reason: 'untranslated in $lang');
    });
  }
}

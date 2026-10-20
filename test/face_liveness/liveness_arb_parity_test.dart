import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Every face-liveness string in the English template must be translated in every shipped locale,
/// with the same {placeholders}. Fails when a new liveness key is added in English only.
void main() {
  const locales = <String>['fr', 'hi', 'ar', 'kn', 'ta'];

  Map<String, dynamic> load(String lang) =>
      jsonDecode(File('assets/l10n/app_$lang.arb').readAsStringSync()) as Map<String, dynamic>;

  Set<String> placeholders(String text) =>
      RegExp(r'\{(\w+)\}').allMatches(text).map((m) => m.group(1)!).toSet();

  final en = load('en');
  final livenessKeys = en.keys.where((k) => k.startsWith('liveness_')).toList();

  test('there are liveness strings to check', () {
    expect(livenessKeys.length, greaterThanOrEqualTo(40));
  });

  for (final lang in locales) {
    test('$lang has every liveness string with matching placeholders', () {
      final arb = load(lang);
      final missing = <String>[];
      final wrongPlaceholders = <String>[];
      for (final key in livenessKeys) {
        final value = arb[key];
        if (value is! String || value.trim().isEmpty) {
          missing.add(key);
        } else if (!sameSet(placeholders(value), placeholders(en[key] as String))) {
          wrongPlaceholders.add(key);
        }
      }
      expect(missing, isEmpty, reason: 'untranslated in $lang');
      expect(wrongPlaceholders, isEmpty, reason: 'placeholder mismatch in $lang');
    });
  }
}

bool sameSet(Set<String> a, Set<String> b) => a.length == b.length && a.containsAll(b);

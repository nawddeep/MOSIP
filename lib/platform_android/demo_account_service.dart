/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class DemoAccount {
  const DemoAccount({required this.username, required this.fullName});

  final String username;
  final String fullName;
}

/// Local demo accounts (debug builds only) for exploring the app without a MOSIP server.
/// Native code hashes passwords (PBKDF2) and refuses every call in release builds.
class DemoAccountService {
  DemoAccountService._();

  static final DemoAccountService instance = DemoAccountService._();
  static const MethodChannel _channel = MethodChannel('io.mosip.registration_client/demo_account');

  Future<bool> isAvailable() async {
    if (!kDebugMode) return false;
    try {
      return (await _channel.invokeMethod<bool>('isAvailable')) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  /// True when [username] is a local demo account (debug builds only).
  Future<bool> exists(String username) async {
    if (!kDebugMode) return false;
    try {
      return (await _channel.invokeMethod<bool>(
              'exists', <String, String>{'username': username})) ??
          false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  /// Throws [PlatformException] with code EXISTS / INVALID / NOT_AVAILABLE on failure.
  Future<void> create({required String username, required String fullName, required String password}) async {
    await _channel.invokeMethod<bool>('create', <String, String>{
      'username': username,
      'fullName': fullName,
      'password': password,
    });
  }

  /// Returns the account when [username]/[password] match a local demo account, otherwise null.
  Future<DemoAccount?> verify(String username, String password) async {
    if (!kDebugMode) return null;
    try {
      final r = await _channel.invokeMethod<Map<dynamic, dynamic>>(
          'verify', <String, String>{'username': username, 'password': password});
      if (r == null) return null;
      return DemoAccount(username: r['username'] as String, fullName: r['fullName'] as String);
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }
}

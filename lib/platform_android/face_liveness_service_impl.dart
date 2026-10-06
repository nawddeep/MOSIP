/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/platform_spi/face_liveness_service.dart';

class FaceLivenessServiceImpl implements FaceLivenessService {
  FaceLivenessServiceImpl._() {
    _method.setMethodCallHandler(_onNativeCall);
  }

  static final FaceLivenessServiceImpl instance = FaceLivenessServiceImpl._();

  static const MethodChannel _method =
      MethodChannel('io.mosip.registration_client/face_liveness');
  static const EventChannel _events =
      EventChannel('io.mosip.registration_client/face_liveness/events');

  final StreamController<PreviewTransform> _transforms =
      StreamController<PreviewTransform>.broadcast();
  Stream<FaceLivenessEvent>? _eventStream;

  Future<dynamic> _onNativeCall(MethodCall call) async {
    if (call.method == 'onPreviewTransform' && call.arguments is Map) {
      _transforms.add(PreviewTransform.fromMap(call.arguments as Map));
    }
    return null;
  }

  @override
  Stream<FaceLivenessEvent> get events => _eventStream ??= _events
      .receiveBroadcastStream()
      .where((e) => e is Map)
      .map((e) => FaceLivenessEvent.fromMap(e as Map));

  @override
  Stream<PreviewTransform> get previewTransforms => _transforms.stream;

  Map<String, String> _wf(LivenessWorkflow w) =>
      <String, String>{'workflow': workflowToNative(w)};

  @override
  Future<bool> isEnabled(LivenessWorkflow workflow) async {
    try {
      return (await _method.invokeMethod<bool>('isEnabled', _wf(workflow))) ?? true;
    } on PlatformException catch (e) {
      debugPrint('FaceLiveness isEnabled failed: ${e.code}');
      // Fail closed: if we cannot read the policy, require liveness.
      return true;
    }
  }

  @override
  Future<int> getMaxAttempts(LivenessWorkflow workflow) async {
    try {
      return (await _method.invokeMethod<int>('getMaxAttempts', _wf(workflow))) ?? 3;
    } on PlatformException {
      return 3;
    }
  }

  @override
  Future<int?> start(LivenessWorkflow workflow) async {
    final reply = await _method.invokeMethod<Map<dynamic, dynamic>>('start', _wf(workflow));
    return reply == null ? null : reply['textureId'] as int?;
  }

  @override
  Future<void> retry() => _method.invokeMethod<void>('retry');

  @override
  Future<void> cancel() async {
    try {
      await _method.invokeMethod<void>('cancel');
    } on PlatformException {
      // already released
    }
  }

  @override
  Future<bool> setSource({required bool mock, String? path, bool mirrored = false}) async {
    return (await _method.invokeMethod<bool>('setSource', <String, Object?>{
          'source': mock ? 'mock' : 'camera',
          'path': path,
          'mirrored': mirrored,
        })) ??
        false;
  }
}

FaceLivenessService getFaceLivenessServiceImpl() => FaceLivenessServiceImpl.instance;

/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/platform_spi/face_liveness_service.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_screen.dart';

/// Runs the liveness screen if the workflow's policy requires it.
/// Returns true when liveness passed (or is disabled by policy), false otherwise.
Future<bool> ensureFaceLiveness(BuildContext context, LivenessWorkflow workflow,
    {FaceLivenessService? service}) async {
  final navigator = Navigator.of(context);
  final s = service ?? FaceLivenessService();
  if (!await s.isEnabled(workflow)) return true;
  final connectivity = Connectivity();
  bool offlineNow = false;
  try {
    offlineNow = (await connectivity.checkConnectivity()) == ConnectivityResult.none;
  } catch (_) {
    offlineNow = false;
  }
  final passed = await navigator.push<bool>(MaterialPageRoute<bool>(
    builder: (_) => FaceLivenessScreen(
      workflow: workflow,
      service: s,
      initiallyOffline: offlineNow,
      offlineStream: connectivity.onConnectivityChanged.map((r) => r == ConnectivityResult.none),
      requestCameraPermission: () async => (await Permission.camera.request()).isGranted,
    ),
  ));
  return passed == true;
}

/// Resident face capture gate. Call before `BiometricsApi().invokeDiscoverSbi(fieldId, modality)`.
/// Only the "Face" modality is gated; exception photos and other modalities pass through.
/// Native code enforces the same rule (BiometricsDetailsApi refuses Face capture without a pass).
Future<bool> ensureResidentFaceLiveness(BuildContext context, String modality) async {
  if (modality != 'Face') return true;
  return ensureFaceLiveness(context, LivenessWorkflow.resident);
}

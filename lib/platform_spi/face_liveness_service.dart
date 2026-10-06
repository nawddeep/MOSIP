/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/platform_android/face_liveness_service_impl.dart';

/// Face liveness engine (native). The UI is driven only by [events].
abstract class FaceLivenessService {
  Stream<FaceLivenessEvent> get events;

  Stream<PreviewTransform> get previewTransforms;

  Future<bool> isEnabled(LivenessWorkflow workflow);

  Future<int> getMaxAttempts(LivenessWorkflow workflow);

  /// Starts a session. Returns the Flutter texture id of the camera preview, or null.
  Future<int?> start(LivenessWorkflow workflow);

  Future<void> retry();

  Future<void> cancel();

  /// Debug builds only: switch between the camera and a recorded mock video.
  Future<bool> setSource({required bool mock, String? path, bool mirrored = false});

  factory FaceLivenessService() => getFaceLivenessServiceImpl();
}

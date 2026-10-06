/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/platform_spi/face_liveness_service.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_gate.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_screen.dart';
import 'package:registration_client/ui/face_liveness/liveness_copy.dart';
import 'package:registration_client/ui/face_liveness/liveness_theme.dart';
import 'package:registration_client/ui/face_liveness/mock_face_liveness_service.dart';

/// Debug-only menu: jump to any liveness state, play mock scenarios, or run the real engine on the
/// camera or a recorded mock video. Not reachable in release builds.
class LivenessDebugMenu extends StatefulWidget {
  const LivenessDebugMenu({super.key});

  static Future<void> open(BuildContext context) async {
    if (!kDebugMode) return;
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const LivenessDebugMenu()));
  }

  @override
  State<LivenessDebugMenu> createState() => _LivenessDebugMenuState();
}

class _LivenessDebugMenuState extends State<LivenessDebugMenu> {
  LivenessWorkflow _workflow = LivenessWorkflow.resident;

  static FaceLivenessEvent sampleEvent(LivenessUiState s, LivenessWorkflow w) {
    return FaceLivenessEvent(
      state: s,
      workflow: w,
      attempt: s == LivenessUiState.maxRetries ? 3 : (s == LivenessUiState.livenessFailed ? 2 : 1),
      maxAttempts: 3,
      challengeType: s == LivenessUiState.challenge ? LivenessChallenge.turnLeft : null,
      secondsRemaining: s == LivenessUiState.challenge ? 6 : null,
      hint: s == LivenessUiState.poorQuality ? LivenessHint.far : null,
    );
  }

  void _openState(LivenessUiState? s) {
    final mock = MockFaceLivenessService(autoPlay: false);
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => FaceLivenessScreen(
        workflow: _workflow,
        service: mock,
        initialEvent: s == null ? null : sampleEvent(s, _workflow),
      ),
    ));
  }

  void _openScript(MockLivenessScript script) {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => FaceLivenessScreen(workflow: _workflow, service: MockFaceLivenessService(script: script)),
    ));
  }

  Future<void> _real(bool mockVideo, {String? path}) async {
    final service = FaceLivenessService();
    await service.setSource(mock: mockVideo, path: path);
    if (!mounted) return;
    final ok = await ensureFaceLiveness(context, _workflow, service: service);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Liveness result: ${ok ? 'passed' : 'not passed'}')));
  }

  @override
  Widget build(BuildContext context) {
    final tiles = <Widget>[
      _header('Workflow'),
      Wrap(
        spacing: 8,
        children: LivenessWorkflow.values
            .map((w) => ChoiceChip(
                  label: Text(LivenessCopy.workflowTitle(w)),
                  selected: _workflow == w,
                  onSelected: (_) => setState(() => _workflow = w),
                ))
            .toList(),
      ),
      _header('Jump to state (mock events)'),
      ListTile(title: const Text('INTRO (Before we start)'), onTap: () => _openState(null)),
      ...LivenessUiState.values.where((s) => s != LivenessUiState.idle).map((s) => ListTile(
            title: Text(s.name),
            onTap: () => _openState(s),
          )),
      _header('Scripted scenarios (mock events)'),
      ...MockLivenessScript.values.map((s) => ListTile(title: Text(s.name), onTap: () => _openScript(s))),
      _header('Real engine'),
      ListTile(title: const Text('Camera'), onTap: () => _real(false)),
      ListTile(
          title: const Text('Mock video'),
          subtitle: const Text('Android/data/io.mosip.registration_client/files/liveness_mock/sample.mp4'),
          onTap: () => _real(true, path: 'sample.mp4')),
      ListTile(
          title: const Text('Mock frames folder'),
          subtitle: const Text('Android/data/io.mosip.registration_client/files/liveness_mock/frames/*.jpg'),
          onTap: () => _real(true, path: 'frames')),
    ];
    return Theme(
      data: LivenessTheme.materialTheme(),
      child: Scaffold(
        appBar: AppBar(title: const Text('Liveness debug'), backgroundColor: LivenessTheme.background),
        body: ListView(padding: const EdgeInsets.all(16), children: tiles),
      ),
    );
  }

  Widget _header(String t) => Padding(
        padding: const EdgeInsets.only(top: 16, bottom: 6),
        child: Text(t, style: LivenessTheme.caption),
      );
}

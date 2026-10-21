/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/model/process.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/ui/onboard/portrait/operational_tasks.dart';
import 'package:registration_client/ui/onboard/portrait/registration_tasks.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

class TasksPage extends StatefulWidget {
  const TasksPage({
    super.key,
    required this.operationalTasks,
    required this.getProcessUI,
    required this.syncData,
  });
  final List<Map<String, dynamic>> operationalTasks;
  final Function getProcessUI;
  final Function syncData;

  @override
  State<TasksPage> createState() => _TasksPageState();
}

class _TasksPageState extends State<TasksPage> {
  int currentIndex = 0;

  @override
  void initState() {
    super.initState();
    Provider.of<GlobalProvider>(context, listen: false)
        .getAudit("NAV_DASHBOARD", "REG-MOD-102");
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(RegistrationTheme.gutterPhone,
                RegistrationTheme.s2, RegistrationTheme.gutterPhone, 0),
            // Desktop segmented control: sunken track, white selected segment.
            child: Container(
              padding: const EdgeInsets.all(RegistrationTheme.s1),
              decoration: BoxDecoration(
                  color: RegistrationTheme.sunken,
                  borderRadius: BorderRadius.circular(RegistrationTheme.rLg)),
              child: LayoutBuilder(
                  builder: (context, constraints) => Wrap(
                      children: List.generate(
                          2,
                          (index) => SizedBox(
                                width: MediaQuery.of(context).textScaleFactor >
                                            1.3 &&
                                        constraints.maxWidth < 600
                                    ? constraints.maxWidth
                                    : constraints.maxWidth / 2,
                                child: Semantics(
                                  selected: currentIndex == index,
                                  child: TextButton(
                                    onPressed: () {
                                      if (index == 1)
                                        context.read<GlobalProvider>().getAudit(
                                            'NAV_OPERATIONAL_TASKS',
                                            'REG-MOD-102');
                                      setState(() => currentIndex = index);
                                    },
                                    style: TextButton.styleFrom(
                                        minimumSize: const Size(
                                            RegistrationTheme.minTouch,
                                            RegistrationTheme.minTouch),
                                        backgroundColor: currentIndex == index
                                            ? RegistrationTheme.card
                                            : Colors.transparent,
                                        foregroundColor: currentIndex == index
                                            ? RegistrationTheme.ink
                                            : RegistrationTheme.muted,
                                        side: currentIndex == index
                                            ? const BorderSide(
                                                color: RegistrationTheme.line)
                                            : BorderSide.none,
                                        shape: RoundedRectangleBorder(
                                            borderRadius: BorderRadius.circular(
                                                RegistrationTheme.r))),
                                    child: Text(
                                        index == 0
                                            ? AppLocalizations.of(context)!
                                                .registration_tasks
                                            : AppLocalizations.of(context)!
                                                .operation_tasks,
                                        textAlign: TextAlign.center,
                                        style: RegistrationTheme.small
                                            .copyWith(
                                                fontWeight: FontWeight.w600,
                                                color: currentIndex == index
                                                    ? RegistrationTheme.ink
                                                    : RegistrationTheme.muted)),
                                  ),
                                ),
                              )))),
            ),
          ),
          currentIndex == 0
              ? RegistrationTasks(
                  getProcessUI: (BuildContext context, Process process) {
                    widget.getProcessUI(context, process);
                  },
                  syncData: (BuildContext context) {
                    widget.syncData(context);
                  },
                )
              : OperationalTasks(
                  operationalTasks: widget.operationalTasks,
                ),
          const SizedBox(height: RegistrationTheme.s6),
        ],
      ),
    );
  }
}

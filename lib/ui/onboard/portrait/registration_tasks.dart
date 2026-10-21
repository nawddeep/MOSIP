import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/model/process.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/provider/registration_task_provider.dart';
import 'package:registration_client/provider/sync_provider.dart';
import 'package:registration_client/ui/theme/components.dart';
import 'package:registration_client/ui/onboard/widgets/home_page_card.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

class RegistrationTasks extends StatelessWidget {
  const RegistrationTasks(
      {super.key, required this.getProcessUI, required this.syncData});
  final Function getProcessUI;
  final Function syncData;

  @override
  Widget build(BuildContext context) {
    final entries = context.watch<RegistrationTaskProvider>().listOfProcesses;
    final language = context.watch<GlobalProvider>().selectedLanguage;
    final l = AppLocalizations.of(context)!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          RegistrationTheme.gutterPhone,
          RegistrationTheme.s5,
          RegistrationTheme.gutterPhone,
          RegistrationTheme.s3),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        HomePageCard(
            index: 0,
            // Turns while a global sync runs (motion moment 6).
            icon: SyncSpinner(
                active: context.watch<SyncProvider>().isGlobalSyncInProgress),
            title: l.synchronize_data,
            subtitle: null,
            ontap: () => syncData(context)),
        const SizedBox(height: RegistrationTheme.s2),
        if (entries.isEmpty)
          Panel(
              child: EmptyState(
                  icon: Icons.workspaces_outline,
                  title: l.registration_tasks,
                  message: l.synchronize_data)),
        LayoutBuilder(
            builder: (context, constraints) => Wrap(
                  spacing: RegistrationTheme.s4,
                  children: List.generate(entries.length, (index) {
                    final process =
                        Process.fromJson(jsonDecode(entries[index].toString()));
                    final labels = process.label ?? const <String, String>{};
                    final title = labels[language] ??
                        labels['eng'] ??
                        (labels.isNotEmpty
                            ? labels.values.first
                            : l.registration_tasks);
                    return SizedBox(
                        width: constraints.maxWidth >= 620
                            ? (constraints.maxWidth - RegistrationTheme.s4) /
                                2
                            : constraints.maxWidth,
                        child: HomePageCard(
                            index: index + 1,
                            icon: Icon(_processIcon(process.flow)),
                            title: title,
                            subtitle: null,
                            ontap: () => getProcessUI(context, process)));
                  }),
                )),
      ]),
    );
  }

  IconData _processIcon(String? flow) {
    switch (flow) {
      case 'NEW':
        return Icons.person_add_alt_1_rounded;
      case 'UPDATE':
        return Icons.manage_accounts_outlined;
      case 'LOST':
        return Icons.person_search_outlined;
      case 'CORRECTION':
        return Icons.edit_note_rounded;
      default:
        return Icons.badge_outlined;
    }
  }
}

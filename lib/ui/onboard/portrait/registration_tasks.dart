import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:provider/provider.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/model/process.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/provider/registration_task_provider.dart';
import 'package:registration_client/ui/onboard/widgets/home_page_card.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:registration_client/utils/app_config.dart';

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
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        HomePageCard(
            index: 0,
            icon: SvgPicture.asset(syncDataIcon),
            title: l.synchronize_data,
            subtitle: null,
            ontap: () => syncData(context)),
        const SizedBox(height: 8),
        if (entries.isEmpty)
          Container(
              padding: const EdgeInsets.all(24),
              decoration: RegistrationTheme.surface(),
              child: Column(children: [
                const Icon(Icons.workspaces_outline,
                    size: 32, color: RegistrationTheme.muted),
                const SizedBox(height: 12),
                Text(l.registration_tasks,
                    style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        color: RegistrationTheme.ink)),
                const SizedBox(height: 8),
                Text(l.synchronize_data,
                    style: const TextStyle(color: RegistrationTheme.muted)),
              ])),
        LayoutBuilder(
            builder: (context, constraints) => Wrap(
                  spacing: 16,
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
                            ? (constraints.maxWidth - 16) / 2
                            : constraints.maxWidth,
                        child: HomePageCard(
                            index: index + 1,
                            icon: Icon(_processIcon(process.flow),
                                color: appSolidPrimary),
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

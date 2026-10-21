import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/model/settings.dart';
import 'package:registration_client/provider/auth_provider.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/ui/process_ui/widgets/device_settings_tab.dart';
import 'package:registration_client/utils/app_config.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/platform_spi/sync_response_service.dart';
import 'widgets/scheduled_jobs_settings.dart';

import 'widgets/global_config_settings_tab.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    super.key,
    required this.getSettingsUI,
  });

  final Future<List<String?>> Function(BuildContext) getSettingsUI;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  List<Settings> settingUiSpec = [];
  List<Settings> settingUiByRole  = [];
  bool isLoadingUiSpec = true;
  late AuthProvider authProvider;
  late SyncResponseService syncResponseService;
  List<String?> activeJobs = const [];

  @override
  void initState() {
    super.initState();
    authProvider = Provider.of<AuthProvider>(context, listen: false);
    syncResponseService = SyncResponseService();
    _loadUiSpec();
    _loadActiveJobs();
  }

  Future<void> _loadUiSpec() async {
    final specList = await widget.getSettingsUI(context);
    settingUiSpec = specList
        .whereType<String>()
        .map((e) => Settings.fromJson(json.decode(e) as Map<String, dynamic>))
        .toList();

    // Get current user roles
    final userId = authProvider.userId;
    final List<String?> userRoles =  await authProvider.getUserRole(userId);

    // Filter tabs accessible to the user based on roles
    settingUiByRole = settingUiSpec.where((settings) {
      final access = settings.accessControl ?? [];
      return access.any((role) => userRoles.contains(role));
    }).toList();

    setState(() => isLoadingUiSpec = false);
  }

  Future<void> _loadActiveJobs() async {
    try {
      final jobs = await syncResponseService.getActiveSyncJobs();
      setState(() {
        activeJobs = jobs;
      });
    } catch (e) {
      debugPrint('Failed to load active sync jobs: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isLoadingUiSpec) {
      return const Center(child: CircularProgressIndicator());
    }


    if (settingUiByRole.isEmpty) {
      return SizedBox(
        width: MediaQuery.of(context).size.width,
        height: MediaQuery.of(context).size.height,
        child: Center(
          child: Text(
            AppLocalizations.of(context)!.no_access_to_this_page,
            style: TextStyle(fontSize: RegistrationTheme.fsHeading, color: RegistrationTheme.ink),
          ),
        ),
      );
    }

    return DefaultTabController(
      length: settingUiByRole.length,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: MediaQuery.of(context).size.width,
            // Desktop page header: white, hairline below, ink title.
            decoration: const BoxDecoration(
              color: RegistrationTheme.card,
              border: Border(
                  bottom: BorderSide(color: RegistrationTheme.line)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: RegistrationTheme.gutterPhone,
                      vertical: RegistrationTheme.s4),
                  child: Text(
                    AppLocalizations.of(context)!.settings,
                    style: RegistrationTheme.title,
                  ),
                ),
                Container(
                  child: TabBar(
                    labelColor: RegistrationTheme.ink,
                    unselectedLabelColor: RegistrationTheme.muted,
                    indicatorColor: RegistrationTheme.accent,
                    indicatorWeight: 2.0,
                    indicatorSize: TabBarIndicatorSize.label,
                    tabs: [
                      for (final settings in settingUiByRole)
                        Tab(
                          text:
                          settings.label?[context.read<GlobalProvider>().selectedLanguage] ??
                              settings.label?['eng'] ??
                              (settings.label?.values.first ?? 'Unknown'),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: TabBarView(
              children: [
                for (final settings in settingUiByRole)
                  _buildTabContent(settings),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _getControllerName(Settings settings) {
    if (settings.fxml != null && settings.fxml!.isNotEmpty) {
      return settings.fxml!.replaceAll('.fxml', 'Controller');
    } else {
      return '${settings.name}Controller';
    }
  }

  Widget _buildTabContent(Settings settings) {
    final selectedLang = context.read<GlobalProvider>().selectedLanguage;

    final controllerName = _getControllerName(settings);

    switch (controllerName) {
      case 'ScheduledJobsSettingsController':
        return ScheduledJobsSettings(settings: settings, selectedLan: selectedLang, jobJsonList: activeJobs);
      case 'GlobalConfigSettingsController':
        return GlobalConfigSettingsTab(settings: settings,selectedLan: selectedLang);
      case 'DeviceSettingsController':
        return DeviceSettingsTab(settings: settings, selectedLan: selectedLang);
      default:
        return _buildDescriptionOnlyTab(settings, selectedLang);
    }
  }

  Widget _buildDescriptionOnlyTab(Settings settings, String selectedLang) {
    return Center(
      child: Text(
        settings.description?[selectedLang] ??
            settings.description?['eng'] ??
            (settings.description?.values.first ?? 'No description available'),
        style: const TextStyle(color: RegistrationTheme.ink),
      ),
    );
  }
}
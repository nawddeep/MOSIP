/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/components.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:registration_client/model/process.dart';
import 'package:registration_client/ui/dashboard/user_dashboard.dart';
import 'package:registration_client/ui/onboard/portrait/tasks_page.dart';
import 'package:registration_client/utils/app_config.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

import '../../settings/settings_screen.dart';
import '../../profile/profile.dart';

class MobileHomePage extends StatefulWidget {
  const MobileHomePage({
    super.key,
    required this.operationalTasks,
    required this.getProcessUI,
    required this.syncData,
    required this.getSettingsUI,
  });
  final List<Map<String, dynamic>> operationalTasks;
  final Function getProcessUI;
  final Function syncData;
  final Function getSettingsUI;

  @override
  State<MobileHomePage> createState() => _MobileHomePageState();
}

class _MobileHomePageState extends State<MobileHomePage> {
  int selectedTab = 2;

  changeTab(int index) {
    if (index == 0 || index == 1 || index == 2 || index == 3) {
      setState(() {
        selectedTab = index;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    List<Widget> bottomNavPages = [
      const UserDashBoard(),
      SettingsScreen(getSettingsUI: (BuildContext context) async {
        return await widget.getSettingsUI(context);
      }),
      Container(
        height: ScreenUtil().screenHeight,
        width: ScreenUtil().screenWidth,
        padding: EdgeInsets.symmetric(
          // horizontal: 20.w,
          vertical: 14.h,
        ),
        margin: EdgeInsets.only(
          top: 0,
        ),
        color: RegistrationTheme.canvas,
        child: TasksPage(
          operationalTasks: widget.operationalTasks,
          getProcessUI: (BuildContext context, Process process) {
            widget.getProcessUI(context, process);
          },
          syncData: (BuildContext context) {
            widget.syncData(context);
          },
        ),
      ),
      // const Center(
      //   child: Text("Notifications"),
      // ),
      const ProfilePage(),
    ];

    return Theme(
        data: RegistrationTheme.light(),
        child: SafeArea(
          child: Scaffold(
            backgroundColor: RegistrationTheme.canvas,
            appBar: AppBar(
                title: Text(AppLocalizations.of(context)!.community_reg_text),
                leading: Padding(
                    padding: const EdgeInsets.all(RegistrationTheme.s3),
                    child: Image.asset(appIconLogoOnly)),
                actions: [
                  IconButton(
                      tooltip: AppLocalizations.of(context)!.profile,
                      onPressed: () => changeTab(3),
                      icon: const Icon(Icons.account_circle_outlined)),
                  const SizedBox(width: RegistrationTheme.s2)
                ]),
            bottomNavigationBar: _getBottomNavigationBar(),
            // Fades in once after login (motion moment 1).
            body: FadeRise(
              child: Column(
                children: [
                  Expanded(
                    child: bottomNavPages[selectedTab],
                  ),
                ],
              ),
            ),
          ),
        ));
  }

  _getBottomNavigationBar() {
    final l = AppLocalizations.of(context)!;
    // Colours come from the theme; hairline on top like Desktop panels.
    // 76 tall leaves room for two-line translated labels.
    return DecoratedBox(
      decoration: const BoxDecoration(
          border: Border(top: BorderSide(color: RegistrationTheme.line))),
      child: NavigationBar(
        selectedIndex: selectedTab,
        onDestinationSelected: changeTab,
        elevation: 0,
        height: 76,
        destinations: [
          NavigationDestination(
              icon: const Icon(Icons.dashboard_outlined),
              selectedIcon: const Icon(Icons.dashboard_rounded),
              label: l.dashboard),
          NavigationDestination(
              icon: const Icon(Icons.tune_rounded), label: l.settings),
          NavigationDestination(
              icon: const Icon(Icons.workspaces_outline),
              selectedIcon: const Icon(Icons.workspaces_rounded),
              label: l.registration_tasks),
          NavigationDestination(
              icon: const Icon(Icons.person_outline_rounded),
              selectedIcon: const Icon(Icons.person_rounded),
              label: l.profile),
        ],
      ),
    );
  }
}

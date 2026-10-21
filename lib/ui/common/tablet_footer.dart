/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/global_provider.dart';

import '../../utils/app_config.dart';

class TabletFooter extends StatelessWidget {
  const TabletFooter({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 50,
      width: double.infinity,
      decoration: const BoxDecoration(
        border: Border(
          top: BorderSide(
            color: Color.fromARGB(255, 249, 249, 249),
          ),
        ),
        color: RegistrationTheme.cardAlt,
      ),
      child: Center(
          child: Column(
        children: [
          Text(
            "Community Registration - Client Version ${context.watch<GlobalProvider>().versionNoApp}",
            style: TextStyle(
                color: RegistrationTheme.muted,
                fontSize: RegistrationTheme.fsSmall,
                fontWeight: regular),
          ),
          Text(
            "Git Commit Id ${context.watch<GlobalProvider>().commitIdApp}",
            style: TextStyle(
                color: RegistrationTheme.muted,
                fontSize: RegistrationTheme.fsSmall,
                fontWeight: regular),
          ),
        ],
      )),
    );
  }
}

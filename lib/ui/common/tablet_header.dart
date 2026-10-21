/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/connectivity_provider.dart';

import 'package:registration_client/provider/global_provider.dart';

import '../../utils/app_config.dart';

class TabletHeader extends StatelessWidget {
  const TabletHeader({super.key});

  @override
  Widget build(BuildContext context) {
    double w = ScreenUtil().screenWidth;
    bool isLandscape =
        MediaQuery.of(context).orientation == Orientation.landscape;

    return Container(
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: RegistrationTheme.line,
          ),
        ),
        color: RegistrationTheme.cardAlt,
      ),
      width: w,
      height: 50.h,
      child: Padding(
        padding: EdgeInsets.fromLTRB(17.w, 0, 16.w, 0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: w < 512 ? 0 : 60,
            ),
            Icon(
              Icons.location_pin,
              color: solidPrimary,
              size: 16,
            ),
            SizedBox(
              width: (isLandscape) ? 7.85.w : 1.96.w,
            ),
            Text(
              context.watch<GlobalProvider>().centerName,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: RegistrationTheme.ink, fontWeight: semiBold),
            ),
            SizedBox(
              width: (isLandscape) ? 20.w : 8.w,
            ),
            Icon(
              Icons.circle,
              color: context.watch<ConnectivityProvider>().isConnected
                  ? RegistrationTheme.ok
                  : RegistrationTheme.muted,
              size: 12,
            ),
            SizedBox(
              width: (isLandscape) ? 10.w : 2.5.w,
            ),
            Icon(
              Icons.desktop_mac_outlined,
              color: solidPrimary,
              size: 16,
            ),
            SizedBox(
              width: (isLandscape) ? 7.w : 1.75.w,
            ),
            Text(
              context.watch<GlobalProvider>().machineName.toUpperCase(),
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: RegistrationTheme.ink, fontWeight: semiBold),
            ),
            SizedBox(
              width: w < 512 ? 0 : 60,
            ),
          ],
        ),
      ),
    );
  }
}

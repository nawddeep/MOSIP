/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'package:flutter/material.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

String appIcon = 'assets/images/MOSIP Logo@2x.png';
String appIconLogoOnly = 'assets/images/MOSIP Logo Only.png';
String buildingsX = 'assets/images/buildings.png';
String buildingsXX = 'assets/images/buildings@2x.png';
double appIconScale = 8;
String dashboardIcon = 'assets/svg/Dashboard.svg';
String notificationIcon = 'assets/svg/Notifications.svg';
String settingsIcon = 'assets/svg/Settings.svg';
String mosipLogo = 'assets/svg/MOSIP Logo.svg';
String syncDataIcon = 'assets/svg/Synchronising Data.svg';
String dashboardSelectedIcon = 'assets/svg/DashboardSelected.svg';
String settingsSelectedIcon = 'assets/svg/SettingsSelected.svg';
String profileIcon = 'assets/svg/ProfileIcon.svg';
String profileSelectedIcon = 'assets/svg/ProfileSelected.svg';
// Legacy colour names, re-pointed to the Desktop-matched tokens in
// RegistrationTheme (docs/ui/design-system.md). Names are kept so existing
// widgets follow; new code should use RegistrationTheme directly.
Color solidPrimary = RegistrationTheme.accentPress;

Color backgroundColor = RegistrationTheme.canvas;
Color pureWhite = RegistrationTheme.card;

Color blackShade1 = RegistrationTheme.ink;

List<Color> appColors = [
  RegistrationTheme.accentPress,
  RegistrationTheme.warn,
  RegistrationTheme.accent,
  RegistrationTheme.warnLine,
];

const Color appWhite = RegistrationTheme.card;
const Color appBlack = RegistrationTheme.ink;
const Color appBlackShade1 = RegistrationTheme.ink;
const Color appBlackShade2 = RegistrationTheme.muted;
// Was #999999 (2.8:1, fails AA); muted keeps hints readable.
const Color appBlackShade3 = RegistrationTheme.muted;
const Color appGreyShade = RegistrationTheme.inputBorder;
// Used as fill and as text, so the AA-safe pressed blue.
const Color appSolidPrimary = RegistrationTheme.accentPress;
const Color appBlueShade1 = RegistrationTheme.accent;
const Color appInfoText = RegistrationTheme.focusOnInk;
const Color appButtonBorderText = RegistrationTheme.accentPress;
const Color appYellow = RegistrationTheme.warnLine;
const Color appOrange = RegistrationTheme.warn;
const Color appBackButtonBorder = RegistrationTheme.lineStrong;
const Color buttonDisabled = RegistrationTheme.sunkenStrong;
const Color mandatoryField = RegistrationTheme.err;
const Color previewHeaderColor = RegistrationTheme.muted;
const Color previewHeaderComponentColor = RegistrationTheme.sunken;
const Color authIconBackground = RegistrationTheme.canvas;
const Color authIconBorder = RegistrationTheme.line;
const Color dividerColor = RegistrationTheme.line;
const Color languageSelectedColor = RegistrationTheme.accentPress;
const Color languageFreezedColor = RegistrationTheme.accentTint;
const Color appBlueShade = RegistrationTheme.cardAlt;
const Color greyBorderShade = RegistrationTheme.line;
const Color dropShadow = Color(0X0000000F);
const Color iconContainerColor = RegistrationTheme.sunken;
const Color memoryCardColor = RegistrationTheme.cardAlt;
const Color appRed = RegistrationTheme.err;
const Color dropDownSelector = RegistrationTheme.accentPress;
const Color dropDownDividerColor = RegistrationTheme.line;
const Color dashBoardPacketUploadColor = RegistrationTheme.ok;
const Color dashBoardPacketUploadPendingColor = RegistrationTheme.warn;
const Color dashBoardPacketUploadExceptionColor = RegistrationTheme.err;
const Color logoutButtonColor = RegistrationTheme.err;
const Color bottomBarSelectedColor = RegistrationTheme.sunken;
const Color bulletPointColor = RegistrationTheme.muted;

// Indexed palette used across process_ui. Order must not change.
List<Color> secondaryColors = [
  RegistrationTheme.accent, // 0
  RegistrationTheme.muted, // 1
  RegistrationTheme.muted, // 2 (was #999, fails AA)
  RegistrationTheme.cardAlt, // 3
  RegistrationTheme.sunken, // 4
  RegistrationTheme.muted, // 5
  RegistrationTheme.ok, // 6
  RegistrationTheme.ink, // 7
  RegistrationTheme.ink, // 8
  RegistrationTheme.lineStrong, // 9
  RegistrationTheme.cardAlt, // 10
  RegistrationTheme.ok, // 11
  RegistrationTheme.accentPress, // 12
  RegistrationTheme.line, // 13
  RegistrationTheme.line, // 14
  RegistrationTheme.err, // 15
  RegistrationTheme.warn, // 16
  RegistrationTheme.sunkenStrong, // 17
  RegistrationTheme.cardAlt, // 18
  RegistrationTheme.muted, // 19
  RegistrationTheme.err, // 20
  RegistrationTheme.canvas, // 21
  RegistrationTheme.lineStrong, // 22
  RegistrationTheme.warnTint, // 23
  RegistrationTheme.warn, // 24
  RegistrationTheme.err, // 25
  RegistrationTheme.err, // 26
];
// IBM Plex Sans ships 400/500/600; 300 and 700 would be synthesized.
FontWeight regular = FontWeight.w400;
FontWeight bold = FontWeight.w600;
FontWeight semiBold = FontWeight.w500;

/* For 1.1.5 configuration, the screen headers will be saved in the 
global params for all the below languages. Other languages can also 
be added here. */
List<String> lang = [
  "eng",
  "ara",
  "fra",
];

// bool get isMobile => ScreenUtil().screenWidth < 750;
// bool get isTablet => ScreenUtil().screenWidth <= 1160;
// bool get isDesktop => ScreenUtil().screenWidth > 1160;

bool get isDesktop => ScreenUtil().screenWidth > 1160;
bool get isMobileSize => ScreenUtil().screenWidth < 750;

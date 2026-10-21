/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

import 'dart:async';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'dart:developer';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:provider/provider.dart';

import 'package:registration_client/main.dart';

import 'package:registration_client/pigeon/user_pigeon.dart';

import 'package:registration_client/provider/auth_provider.dart';
import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_gate.dart';
import 'package:registration_client/ui/face_liveness/liveness_debug_menu.dart';
import 'package:registration_client/platform_android/demo_account_service.dart';
import 'package:registration_client/platform_spi/global_config_service.dart';
import 'package:registration_client/ui/signup/signup_page.dart';
import 'package:registration_client/provider/sync_provider.dart';
import 'package:registration_client/ui/dashboard/dashboard_tablet.dart';
import 'package:registration_client/ui/widgets/network_component.dart';
import 'package:registration_client/ui/widgets/sync_alert_dialog.dart';
import 'package:registration_client/ui/machine_keys.dart';
import 'package:registration_client/provider/connectivity_provider.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/utils/app_config.dart';
import 'package:registration_client/utils/responsive.dart';
import 'package:registration_client/ui/widgets/password_component.dart';
import 'package:registration_client/ui/widgets/username_component.dart';
import 'package:colorful_progress_indicators/colorful_progress_indicators.dart';
import 'package:url_launcher/url_launcher.dart';

import '../utils/life_cycle_event_handler.dart';
import '../utils/location_service.dart';

class LoginPage extends StatefulWidget {
  static const route = "/login-page";
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> with WidgetsBindingObserver {
  bool isMobile = true;
  bool isLoggingIn = false;
  String username = '';
  String password = '';
  bool isMachineKeysDialogOpen = false;

  _toggleMachineKeysDialog() {
    setState(() {
      isMachineKeysDialogOpen = false;
    });
  }

  late AuthProvider authProvider;
  late SyncProvider syncProvider;
  late GlobalProvider globalProvider;
  late ConnectivityProvider connectivityProvider;
  late AppLocalizations appLocalizations = AppLocalizations.of(context)!;

  TextEditingController usernameController = TextEditingController();
  TextEditingController passwordController = TextEditingController();

  @override
  void initState() {
    authProvider = Provider.of<AuthProvider>(context, listen: false);
    syncProvider = Provider.of<SyncProvider>(context, listen: false);
    globalProvider = Provider.of<GlobalProvider>(context, listen: false);
    connectivityProvider =
        Provider.of<ConnectivityProvider>(context, listen: false);
    _initializeAppData();
    super.initState();
    WidgetsBinding.instance.addObserver(LifecycleEventHandler(
      resumeCallBack: () async {
        if (mounted) {
          setState(() {
            closeKeyboard();
          });
        }
      },
      suspendingCallBack: () async {
        if (mounted) {
          setState(() {
            closeKeyboard();
          });
        }
      },
    ));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  void closeKeyboard() {
    FocusScope.of(context).unfocus();
  }

  _initializeAppData() async {
    await globalProvider.setMachineDetails();
    await globalProvider.initializeLanguageDataList(false);
    await authProvider.getPasswordLength();
    await globalProvider.initializeLocationHierarchyMap();
    await globalProvider.setGitHeadAttributes();
    await globalProvider.getAudit("REG-LOAD-001", "REG-MOD-101");
  }

  _saveAllHeaders() async {
    for (var header in lang) {
      await globalProvider.saveScreenHeaderToGlobalParam(
          "newRegistrationProcess_$header",
          appLocalizations.newRegistrationProcess(header));
      await globalProvider.saveScreenHeaderToGlobalParam(
          "consentScreenName_$header",
          appLocalizations.consentScreenName(header));
      await globalProvider.saveScreenHeaderToGlobalParam(
          "demographicsScreenName_$header",
          appLocalizations.demographicsScreenName(header));
      await globalProvider.saveScreenHeaderToGlobalParam(
          "documentsScreenName_$header",
          appLocalizations.documentsScreenName(header));
      await globalProvider.saveScreenHeaderToGlobalParam(
          "biometricsScreenName_$header",
          appLocalizations.biometricsScreenName(header));
    }
  }

  @override
  Widget build(BuildContext context) {
    isMobile = MediaQuery.of(context).orientation == Orientation.portrait;
    appLocalizations = AppLocalizations.of(context)!;
    return Stack(children: [
      AccountShell(
        title: appLocalizations.welcome,
        description: appLocalizations.community_reg_text,
        onLogoDoubleTap:
            kDebugMode ? () => LivenessDebugMenu.open(context) : null,
        onLogoLongPress: () {
          setState(() => isMachineKeysDialogOpen = true);
        },
        child: _loginComponent(),
        footer: Text(appLocalizations.info_text,
            textAlign: TextAlign.center,
            style: RegistrationTheme.small),
      ),
      if (isMachineKeysDialogOpen)
        Positioned.fill(
            child: Container(
                color: RegistrationTheme.scrim,
                child: Center(
                    child: MachineKeys(
                        onCloseComponent: _toggleMachineKeysDialog)))),
    ]);
  }

  _getUserValidation() async {
    FocusManager.instance.primaryFocus?.unfocus();
    if (username.isEmpty) {
      _showInSnackBar(appLocalizations.username_required);
      return;
    } else if (username.length > 50) {
      _showInSnackBar(appLocalizations.username_exceed);
      return;
    }

    // Debug builds only: a local demo account has no MOSIP server record,
    // so go straight to the password step (verified locally at login).
    if (kDebugMode && await DemoAccountService.instance.exists(username)) {
      authProvider.setIsValidUser(true);
      return;
    }

    String langCode = globalProvider.selectedLanguage;
    await authProvider.validateUser(username, langCode);

    bool isValid = authProvider.isValidUser;
    if (!isValid) {
      _showInSnackBar(appLocalizations.username_incorrect);
      return;
    }
    globalProvider.getAudit("REG-AUTH-001", "REG-MOD-101");
    final User user = authProvider.currentUser;
    globalProvider.setCenterId(user.centerId!);
    globalProvider.setName(user.name!);
    globalProvider.setCenterName(user.centerName!);
    _showInSnackBar(appLocalizations.user_validated);
  }

  Future<void> _openSignup() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final demoUsername =
        await Navigator.of(context).push<String>(MaterialPageRoute<String>(
      builder: (_) => SignupPage(
        adminEmailLoader: () async {
          final params = await GlobalConfigService().getRegistrationParams();
          final value = params[signupAdminEmailKey]?.toString().trim();
          return (value == null || value.isEmpty) ? null : value;
        },
      ),
    ));
    if (demoUsername != null && mounted) {
      _showInSnackBar(appLocalizations.signup_demo_created(demoUsername));
    }
  }

  void _showInSnackBar(String value) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(value),
      ),
    );
  }

  goToUrl(String url) async {
    if (await canLaunchUrl(Uri.parse(url))) {
      await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
    } else {
      throw 'Could not launch $url';
    }
  }

  _getLoginAction() async {
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    FocusManager.instance.primaryFocus?.unfocus();
    if (password.isEmpty) {
      _showInSnackBar(appLocalizations.password_required);
      return;
    }
    if (password.length > (int.tryParse(authProvider.passwordLength) ?? 50)) {
      _showInSnackBar(appLocalizations.password_exceed);
      return;
    }

    // Debug builds only: a local demo account (created from Sign up) logs in without a MOSIP server.
    if (kDebugMode) {
      final demo = await DemoAccountService.instance.verify(username, password);
      if (demo != null) {
        authProvider.startDemoSession(demo.username, demo.fullName);
        authProvider.setIsSyncing(false);
        await _navigateToHomePage();
        if (mounted) {
          setState(() {
            isLoggingIn = false;
          });
        }
        return;
      }
    }

    setState(() {
      authProvider.setIsSyncing(true);
    });
    await connectivityProvider.checkNetworkConnection();

    await globalProvider.getVersionNoApp();
    String version = globalProvider.versionNoApp;
    await globalProvider.saveVersionToGlobalParam(
        "mosip.registration.server_version", version);
    if (version.startsWith("1.1.5")) {
      await _saveAllHeaders();
    }
    await authProvider.authenticateUser(
        username, password, connectivityProvider.isConnected);

    if (!authProvider.isLoggedIn) {
      authProvider.setIsSyncing(false);
      _showErrorInSnackbar();
      return;
    }

    await syncProvider.getLastSyncTime();
    debugPrint(syncProvider.lastSuccessfulSyncTime);

    await connectivityProvider.checkNetworkConnection();

    if (!connectivityProvider.isConnected) {
      if (!authProvider.isCenterActive) {
        authProvider.setLoginError("REG_CENTER_INACTIVE");
        authProvider.setIsLoggedIn(false);
        authProvider.setIsSyncing(false);
        _showErrorInSnackbar();
        return;
      }

      if (!authProvider.isMachineActive) {
        authProvider.setLoginError("REG_MACHINE_INACTIVE");
        authProvider.setIsLoggedIn(false);
        authProvider.setIsSyncing(false);
        _showErrorInSnackbar();
        return;
      }
    }

    if (!authProvider.isCenterActive || !authProvider.isMachineActive) {
      _showAlertDialog();
      authProvider.setIsSyncing(false);
      return;
    }

    log("machine active: ${authProvider.isMachineActive}");

    if (authProvider.isLoggedIn &&
        (syncProvider.lastSuccessfulSyncTime == "LastSyncTimeIsNull")) {
      log("sync time: ${syncProvider.lastSuccessfulSyncTime}");
      syncProvider.setIsGlobalSyncInProgress(true);
      await _autoSyncHandler();
    } else {
      authProvider.setIsSyncing(false);
      await _navigateToHomePage();
    }
    setState(() {
      isLoggingIn = false;
    });
  }

  _showErrorInSnackbar() {
    String errorMsg = authProvider.loginError;
    if (errorMsg == "") {
      return;
    }
    String snackbarText = "";
    snackbarText = appLocalizations.errors(errorMsg);
    if (snackbarText == "Some error occurred!") {
      snackbarText = errorMsg;
    }
    _showInSnackBar(snackbarText);
  }

  _navigateToHomePage() async {
    if (authProvider.isLoggedIn == true) {
      // Operator face liveness (policy "operator"): login completes only after liveness passes.
      final livenessPassed =
          await ensureFaceLiveness(context, LivenessWorkflow.operator);
      if (!livenessPassed) {
        await authProvider.logoutUser();
        authProvider.setIsLoggedIn(false);
        authProvider.setIsSyncing(false);
        if (mounted) {
          setState(() {
            isLoggingIn = false;
          });
        }
        return;
      }

      // Start new location session so permission is re-requested if user chose "Only this time" previously
      await LocationService.instance.startNewSession();

      if (!context.mounted) return;
      Navigator.popUntil(context, ModalRoute.withName('/login-page'));

      if (authProvider.isOnboarded || authProvider.isDefault) {
        globalProvider.setCurrentIndex(1);
      } else {
        globalProvider.setCurrentIndex(0);
      }

      if (!context.mounted) return;
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (context) => Responsive(
            mobile: DashBoardTabletView(),
            desktop: DashBoardTabletView(),
            tablet: DashBoardTabletView(),
          ),
        ),
      );
    }
  }

  Widget _loginComponent() {
    return Container(
      padding: const EdgeInsets.all(RegistrationTheme.s6),
      decoration: RegistrationTheme.surface(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          if (!context.watch<AuthProvider>().isNetworkPresent) ...[
            Text(
              appLocalizations.login_text,
              style: RegistrationTheme.title,
            ),
            const SizedBox(height: RegistrationTheme.s5),
          ],
          !context.watch<AuthProvider>().isValidUser &&
                  !context.watch<AuthProvider>().isNetworkPresent
              ? UsernameComponent(
                  onTap: () {
                    _getUserValidation();
                  },
                  isDisabled:
                      username.trim().isEmpty || username.trim().length > 50,
                  languages: context.watch<GlobalProvider>().languages,
                  isMobile: isMobile,
                  onTapSignup: _openSignup,
                  mp: context.watch<GlobalProvider>().codeToLanguageMapper,
                  onChanged: (v) {
                    setState(() {
                      username = v;
                    });
                  },
                )
              : const SizedBox(),
          context.watch<AuthProvider>().isValidUser &&
                  !context.watch<AuthProvider>().isNetworkPresent
              ? PasswordComponent(
                  isDisabled: password.isEmpty ||
                      password.length >
                          (int.tryParse(context
                                  .watch<AuthProvider>()
                                  .passwordLength) ??
                              50),
                  onTapLogin: () async {
                    await _getLoginAction();
                    await globalProvider.getThresholdValues();
                  },
                  isMobile: isMobile,
                  onTapBack: () {
                    password = "";
                    FocusManager.instance.primaryFocus?.unfocus();
                    authProvider.setIsValidUser(false);
                    setState(() {
                      username = '';
                      authProvider.setIsSyncing(false);
                    });
                  },
                  onTapForgotPassword: () async {
                    await connectivityProvider.checkNetworkConnection();
                    bool isConnected = connectivityProvider.isConnected;
                    if (isConnected) {
                      await authProvider.getForgotPasswordUrl();
                      String res = authProvider.forgotPasswordUrl;
                      await goToUrl(res);
                    } else {
                      authProvider.setIsNetworkPresent(true);
                    }
                  },
                  onChanged: (v) {
                    setState(() {
                      password = v;
                    });
                  },
                  isLoggingIn: authProvider.isSyncing,
                )
              : const SizedBox(),
          context.watch<AuthProvider>().isNetworkPresent
              ? NetworkComponent(
                  isMobile: isMobile,
                  onTapRetry: () async {
                    await connectivityProvider.checkNetworkConnection();
                    bool isConnected = connectivityProvider.isConnected;
                    if (isConnected) {
                      authProvider.setIsNetworkPresent(false);
                      await authProvider.getForgotPasswordUrl();
                      String res = authProvider.forgotPasswordUrl;
                      await goToUrl(res);
                    }
                  })
              : const SizedBox(),
        ],
      ),
    );
  }

  _autoSyncHandler() async {
    if (syncProvider.isGlobalSyncInProgress) {
      authProvider.setIsSyncing(false);
      showLoadingDialog(context);
      await syncProvider.autoSync(context).then((value) {
        // syncProvider.setIsGlobalSyncInProgress(false);
      });
      showSyncResultDialog();
    }

    await globalProvider.initializeLanguageDataList(false);
    Timer(const Duration(seconds: 5), () {
      if (syncProvider.isAllSyncSuccessful()) {
        RestartWidget.restartApp(context);
      } else {
        SystemChannels.platform.invokeMethod('SystemNavigator.pop');
      }
    });
  }

  showSyncResultDialog() {
    showGeneralDialog(
      context: context,
      barrierDismissible: false,
      pageBuilder: (_, __, ___) {
        return WillPopScope(
          onWillPop: () async => false,
          child: Align(
            alignment: Alignment.center,
            child: Container(
              constraints: const BoxConstraints(maxWidth: 360),
              margin: const EdgeInsets.all(RegistrationTheme.s6),
              decoration: RegistrationTheme.surface(shadow: true),
              child: Padding(
                padding: const EdgeInsets.all(RegistrationTheme.s6),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    SizedBox(
                      height: 56,
                      width: 56,
                      child: syncProvider.isAllSyncSuccessful()
                          ? SvgPicture.asset(
                              "assets/svg/Success Message Icon.svg")
                          : SvgPicture.asset(
                              "assets/svg/Failed Message Icon.svg"),
                    ),
                    const SizedBox(height: RegistrationTheme.s4),
                    DefaultTextStyle(
                      style: RegistrationTheme.heading,
                      child: Text(
                        syncProvider.isAllSyncSuccessful()
                            ? appLocalizations.sync_completed_succesfully
                            : appLocalizations.sync_failed,
                        textAlign: TextAlign.center,
                      ),
                    ),
                    const SizedBox(height: RegistrationTheme.s1),
                    DefaultTextStyle(
                      style: RegistrationTheme.small,
                      child: Text(
                        syncProvider.isAllSyncSuccessful()
                            ? appLocalizations.sync_completed_message
                            : appLocalizations.sync_failed_message,
                        softWrap: true,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  showLoadingDialog(BuildContext context) {
    showGeneralDialog(
      context: context,
      barrierDismissible: false,
      pageBuilder: (_, __, ___) {
        return WillPopScope(
          onWillPop: () async => false,
          child: Align(
            alignment: Alignment.center,
            child: Container(
              constraints: const BoxConstraints(maxWidth: 360),
              margin: const EdgeInsets.all(RegistrationTheme.s6),
              padding: const EdgeInsets.all(RegistrationTheme.s6),
              decoration: RegistrationTheme.surface(shadow: true),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Stack(
                    alignment: Alignment.center,
                    children: [
                      Image.asset(
                        appIconLogoOnly,
                        fit: BoxFit.scaleDown,
                        height: 40,
                        width: 40,
                      ),
                      Transform.scale(
                        scale: 1.6,
                        child: Center(
                          child: ColorfulCircularProgressIndicator(
                            colors: appColors,
                            strokeWidth: 2.2,
                            duration: const Duration(milliseconds: 500),
                            initialColor: appColors[0],
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: RegistrationTheme.s8),
                  DefaultTextStyle(
                      style: RegistrationTheme.bodyStrong,
                      child: Consumer<SyncProvider>(
                        builder: (context, syncP, child) {
                          return Column(children: [
                            Text(
                                "Sync ${syncP.currentSyncProgress.toString()} of 7 "),
                            const SizedBox(height: RegistrationTheme.s3),
                            ExcludeSemantics(
                              child: ClipRRect(
                                borderRadius:
                                    BorderRadius.circular(RegistrationTheme.r),
                                child: LinearProgressIndicator(
                                    minHeight: 4,
                                    value: (syncP.currentSyncProgress / 7)
                                        .clamp(0.0, 1.0)
                                        .toDouble()),
                              ),
                            ),
                          ]);
                        },
                      )),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  _showAlertDialog() {
    return showDialog(
      context: context,
      builder: (BuildContext context) => SyncAlertDialog(
        title: appLocalizations.sync_alert_title,
        content: appLocalizations.sync_alert_content,
        onPressed: () async {
          Navigator.of(context).pop();
          syncProvider.setIsGlobalSyncInProgress(true);
          await _autoSyncHandler();
        },
      ),
    );
  }
}

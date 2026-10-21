import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/provider/registration_task_provider.dart';
import 'package:registration_client/provider/sync_provider.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:registration_client/ui/widgets/username_component.dart';
import 'package:registration_client/ui/signup/signup_page.dart';
import 'package:registration_client/ui/onboard/portrait/mobile_home_page.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_screen.dart';
import 'package:registration_client/ui/face_liveness/mock_face_liveness_service.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import '../widgets/signup_page_test.dart' show FakeDemo;

class PreviewGlobal extends GlobalProvider {
  @override
  Future<void> getAudit(String id, String componentId,
      [dynamic arguments]) async {}
}

void main() {
  for (final size in [const Size(390, 844), const Size(1024, 768)]) {
    for (final surface in [
      'login',
      'signup',
      'home',
      'verification',
      'home-large'
    ]) {
      testWidgets('$surface ${size.width.toInt()}x${size.height.toInt()}',
          (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final fonts = FontLoader('IBMPlexSans');
        for (final weight in ['Regular', 'Medium', 'SemiBold']) {
          fonts.addFont(rootBundle
              .load('assets/fonts/ibm_plex_sans/IBMPlexSans-$weight.ttf'));
        }
        await fonts.load();
        final icons = FontLoader('MaterialIcons')
          ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'));
        await icons.load();
        final tasks = RegistrationTaskProvider();
        tasks.listOfProcesses = [
          jsonEncode({
            'id': 'new',
            'flow': 'NEW',
            'label': {'eng': 'New registration'},
            'icon': 'NewReg.png'
          }),
          jsonEncode({
            'id': 'update',
            'flow': 'UPDATE',
            'label': {'eng': 'Update identity'},
            'icon': 'UINUpdate.png'
          }),
          jsonEncode({
            'id': 'lost',
            'flow': 'LOST',
            'label': {'eng': 'Lost identity number'},
            'icon': 'LostUIN.png'
          }),
        ];
        final login = AccountShell(
            title: 'Welcome',
            description: 'Community Registration Client',
            child: Container(
                padding: const EdgeInsets.all(RegistrationTheme.s6),
                decoration: RegistrationTheme.surface(),
                child: Builder(
                    builder: (context) => Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Text(AppLocalizations.of(context)!.login_text,
                                  style:
                                      Theme.of(context).textTheme.titleLarge),
                              const SizedBox(height: 24),
                              UsernameComponent(
                                  onTap: () {},
                                  languages: const ['eng', 'fra'],
                                  mp: const {
                                    'eng': 'English',
                                    'fra': 'Français'
                                  },
                                  onChanged: (_) {},
                                  isDisabled: false,
                                  isMobile: true,
                                  onTapSignup: () {}),
                            ]))));
        Widget page;
        switch (surface) {
          case 'login':
            page = login;
            break;
          case 'signup':
            page = SignupPage(demoAccounts: FakeDemo(available: false));
            break;
          case 'home-large':
          case 'home':
            page = MobileHomePage(
                operationalTasks: const [],
                getProcessUI: (_, __) {},
                syncData: (_) {},
                getSettingsUI: (_) async => <String>[]);
            break;
          default:
            page = FaceLivenessScreen(
                workflow: LivenessWorkflow.resident,
                service: MockFaceLivenessService(autoPlay: false),
                initialEvent: const FaceLivenessEvent(
                    state: LivenessUiState.challenge,
                    workflow: LivenessWorkflow.resident,
                    challengeType: LivenessChallenge.turnLeft,
                    secondsRemaining: 8));
        }
        await tester.pumpWidget(const MaterialApp(home: SizedBox()));
        await tester.runAsync(() async {
          final context = tester.element(find.byType(MaterialApp));
          await precacheImage(
              const AssetImage('assets/images/MOSIP Logo@2x.png'), context);
          await precacheImage(
              const AssetImage('assets/images/MOSIP Logo Only.png'), context);
        });
        await tester.pumpWidget(MultiProvider(
            providers: [
              ChangeNotifierProvider<GlobalProvider>(
                  create: (_) => PreviewGlobal()),
              ChangeNotifierProvider<RegistrationTaskProvider>.value(
                  value: tasks),
              ChangeNotifierProvider(create: (_) => SyncProvider()),
            ],
            child: ScreenUtilInit(
                designSize: size,
                builder: (_, __) => MaterialApp(
                    debugShowCheckedModeBanner: false,
                    theme: RegistrationTheme.light(),
                    localizationsDelegates:
                        AppLocalizations.localizationsDelegates,
                    supportedLocales: AppLocalizations.supportedLocales,
                    home: MediaQuery(
                        data: MediaQueryData(
                            size: size,
                            disableAnimations: true,
                            textScaleFactor: surface == 'home-large' ? 2 : 1),
                        child: page)))));
        await tester.runAsync(() async {
          final context = tester.element(find.byType(MaterialApp));
          for (final path in [
            'assets/images/MOSIP Logo@2x.png',
            'assets/images/MOSIP Logo Only.png',
            'assets/images/NewReg.png',
            'assets/images/UINUpdate.png',
            'assets/images/LostUIN.png'
          ]) {
            await precacheImage(AssetImage(path), context);
          }
        });
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        if (surface.startsWith('home')) {
          final nav = tester.getRect(find.byType(NavigationBar));
          expect(nav.bottom, lessThanOrEqualTo(size.height));
          expect(nav.top, greaterThan(0));
        }
        await expectLater(
            find.byType(MaterialApp),
            matchesGoldenFile(
                '../../docs/ui/previews/$surface-${size.width.toInt()}x${size.height.toInt()}.png'));
      });
    }
  }
}

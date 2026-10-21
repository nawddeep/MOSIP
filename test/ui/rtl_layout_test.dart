import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/model/face_liveness_event.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/provider/registration_task_provider.dart';
import 'package:registration_client/provider/sync_provider.dart';
import 'package:registration_client/ui/face_liveness/face_liveness_screen.dart';
import 'package:registration_client/ui/face_liveness/mock_face_liveness_service.dart';
import 'package:registration_client/ui/onboard/portrait/mobile_home_page.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:registration_client/ui/widgets/username_component.dart';

class _Global extends GlobalProvider {
  @override
  Future<void> getAudit(String id, String componentId,
      [dynamic arguments]) async {}
}

/// Arabic (RTL) layout checks for the redesigned surfaces, on phone and
/// tablet, including 200% text. Portable: no golden comparison by default.
///
/// To refresh the docs screenshots (macOS, uses the system Arabic font at
/// render time only; the font is never committed):
///   UI_SCREENSHOTS=1 flutter test test/ui/rtl_layout_test.dart --update-goldens
void main() {
  final screenshots = Platform.environment['UI_SCREENSHOTS'] == '1';
  const arabicFontPath = '/System/Library/Fonts/SFArabic.ttf';

  for (final size in const [Size(390, 844), Size(1024, 768)]) {
    for (final surface in const ['login', 'home', 'home-large', 'verification']) {
      final name = '$surface-ar-${size.width.toInt()}x${size.height.toInt()}';
      testWidgets(name, (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);

        final plex = FontLoader('IBMPlexSans');
        for (final w in ['Regular', 'Medium', 'SemiBold']) {
          plex.addFont(
              rootBundle.load('assets/fonts/ibm_plex_sans/IBMPlexSans-$w.ttf'));
        }
        await plex.load();
        final icons = FontLoader('MaterialIcons')
          ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'));
        await icons.load();
        if (screenshots && File(arabicFontPath).existsSync()) {
          final bytes = File(arabicFontPath).readAsBytesSync();
          final ar = FontLoader(RegistrationTheme.fontFallback.first)
            ..addFont(Future.value(ByteData.view(bytes.buffer)));
          await ar.load();
        }

        final tasks = RegistrationTaskProvider()
          ..listOfProcesses = [
            jsonEncode({
              'id': 'new',
              'flow': 'NEW',
              'label': {'eng': 'New registration', 'ara': 'تسجيل جديد'},
            }),
            jsonEncode({
              'id': 'update',
              'flow': 'UPDATE',
              'label': {'eng': 'Update identity', 'ara': 'تحديث الهوية'},
            }),
          ];

        late final Widget page;
        switch (surface) {
          case 'login':
            page = AccountShell(
                title: 'مرحبا',
                description: 'عميل التسجيل المجتمعي',
                child: Container(
                    padding: const EdgeInsets.all(RegistrationTheme.s6),
                    decoration: RegistrationTheme.surface(),
                    child: UsernameComponent(
                        onTap: () {},
                        languages: const ['eng', 'ara'],
                        mp: const {'eng': 'English', 'ara': 'العربية'},
                        onChanged: (_) {},
                        isDisabled: false,
                        isMobile: true,
                        onTapSignup: () {})));
            break;
          case 'home':
          case 'home-large':
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

        final global = _Global()..selectedLanguage = 'ara';
        await tester.pumpWidget(MultiProvider(
            providers: [
              ChangeNotifierProvider<GlobalProvider>.value(value: global),
              ChangeNotifierProvider<RegistrationTaskProvider>.value(
                  value: tasks),
              ChangeNotifierProvider(create: (_) => SyncProvider()),
            ],
            child: ScreenUtilInit(
                designSize: size,
                builder: (_, __) => MaterialApp(
                    debugShowCheckedModeBanner: false,
                    locale: const Locale('ar'),
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
          ]) {
            await precacheImage(AssetImage(path), context);
          }
        });
        await tester.pumpAndSettle();

        // No overflow or layout exception in RTL.
        expect(tester.takeException(), isNull);
        final context = tester.element(find.byWidget(page));
        expect(Directionality.of(context), TextDirection.rtl);

        if (surface.startsWith('home')) {
          // Navigation mirrors: the first destination sits on the right.
          final first =
              tester.getCenter(find.byIcon(Icons.dashboard_outlined)).dx;
          final last =
              tester.getCenter(find.byIcon(Icons.person_outline_rounded)).dx;
          expect(first, greaterThan(last));
        }
        if (surface == 'verification') {
          // Back sits at the start edge, which is the right in RTL.
          expect(tester.getCenter(find.byTooltip('Back')).dx,
              greaterThan(size.width / 2));
        }
        if (surface == 'login') {
          // Chips flow from the right.
          final english = tester.getCenter(find.text('English')).dx;
          final arabic = tester.getCenter(find.text('العربية')).dx;
          expect(english, greaterThan(arabic));
        }

        if (screenshots) {
          await expectLater(find.byType(MaterialApp),
              matchesGoldenFile('../../docs/ui/screenshots/after/$name.png'));
        }
      });
    }
  }
}

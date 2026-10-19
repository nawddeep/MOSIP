import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/global_provider.dart';
import 'package:registration_client/provider/sync_provider.dart';
import 'package:registration_client/provider/registration_task_provider.dart';
import 'package:registration_client/ui/onboard/portrait/registration_tasks.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';
import 'package:registration_client/ui/widgets/username_component.dart';
import 'package:registration_client/ui/widgets/password_component.dart';
import 'package:registration_client/ui/onboard/widgets/home_page_card.dart';

Widget localized(Widget child) => MaterialApp(
    theme: RegistrationTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: child);

void main() {
  testWidgets('keyboard submits username once and disabled action is respected',
      (tester) async {
    var submitted = 0;
    await tester.pumpWidget(ChangeNotifierProvider(
        create: (_) => GlobalProvider(),
        child: localized(Scaffold(
            body: UsernameComponent(
                onTap: () => submitted++,
                languages: const [],
                mp: const {},
                onChanged: (_) {},
                isDisabled: false,
                isMobile: true)))));
    await tester.pumpAndSettle();
    await tester.showKeyboard(find.byType(TextField));
    await tester.testTextInput.receiveAction(TextInputAction.next);
    expect(submitted, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('busy login prevents repeat submission and back navigation',
      (tester) async {
    var submitted = 0;
    await tester.pumpWidget(localized(Scaffold(
        body: PasswordComponent(
            onTapLogin: () => submitted++,
            onTapBack: () => submitted++,
            onChanged: (_) {},
            isLoggingIn: true,
            isDisabled: false,
            isMobile: true,
            onTapForgotPassword: () => submitted++))));
    await tester.pump();
    expect(tester.widget<ElevatedButton>(find.byType(ElevatedButton)).onPressed,
        isNull);
    expect(tester.widget<OutlinedButton>(find.byType(OutlinedButton)).onPressed,
        isNull);
    await tester.showKeyboard(find.byType(TextField));
    await tester.testTextInput.receiveAction(TextInputAction.done);
    expect(submitted, 0);
  });

  for (final size in [const Size(390, 844), const Size(1024, 768)]) {
    testWidgets('account shell fits ${size.width} and enlarged text',
        (tester) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(localized(MediaQuery(
          data: MediaQueryData(size: size, textScaleFactor: 2),
          child: AccountShell(
              title: 'Welcome',
              description: 'Community Registration Client',
              child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: RegistrationTheme.surface(),
                  child: PasswordComponent(
                      onTapLogin: () {},
                      onTapBack: () {},
                      onChanged: (_) {},
                      isLoggingIn: false,
                      isDisabled: false,
                      isMobile: true,
                      onTapForgotPassword: () {}))))));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.byType(OutlinedButton));
      expect(find.byType(OutlinedButton), findsOneWidget);
    });
  }

  testWidgets('task card remains actionable with a long translated label',
      (tester) async {
    var opened = false;
    await tester.pumpWidget(localized(Scaffold(
        body: SizedBox(
            width: 280,
            child: HomePageCard(
                icon: const Icon(Icons.person_add_outlined),
                title: 'Register a resident with a longer workflow name',
                index: 1,
                subtitle: 'Registration workflow',
                ontap: () => opened = true)))));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester
        .tap(find.text('Register a resident with a longer workflow name'));
    expect(opened, isTrue);
  });
  testWidgets(
      'empty workflows keep synchronization available without fabricated tasks',
      (tester) async {
    var syncs = 0;
    await tester.pumpWidget(MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => GlobalProvider()),
          ChangeNotifierProvider(create: (_) => SyncProvider()),
          ChangeNotifierProvider(create: (_) => RegistrationTaskProvider()),
        ],
        child: localized(Scaffold(
            body: RegistrationTasks(
                getProcessUI: (_, __) {}, syncData: (_) => syncs++)))));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byType(HomePageCard), findsOneWidget);
    await tester.tap(find.byType(HomePageCard));
    expect(syncs, 1);
  });

  testWidgets('invalid synchronization timestamp leaves card usable',
      (tester) async {
    final sync = SyncProvider()
      ..setLastSuccessfulSyncTime('LastSyncTimeIsNull');
    await tester.pumpWidget(ChangeNotifierProvider.value(
        value: sync,
        child: localized(Scaffold(
            body: HomePageCard(
                index: 0,
                icon: const Icon(Icons.sync),
                title: 'Synchronize Data',
                subtitle: null,
                ontap: () {})))));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('LastSyncTimeIsNull'), findsNothing);
    expect(find.text('Synchronize Data'), findsOneWidget);
  });
}

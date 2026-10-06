import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:registration_client/ui/widgets/password_component.dart';

void main() {
  testWidgets('login password can be shown and hidden again', (tester) async {
    await tester.pumpWidget(ScreenUtilInit(
      designSize: const Size(390, 844),
      builder: (_, __) => MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SingleChildScrollView(
            child: PasswordComponent(
              onTapLogin: () {},
              onTapBack: () {},
              onChanged: (_) {},
              isLoggingIn: false,
              isDisabled: false,
              isMobile: false,
              onTapForgotPassword: () {},
            ),
          ),
        ),
      ),
    ));
    await tester.pumpAndSettle();

    TextField field() => tester.widget<TextField>(find.byType(TextField));
    await tester.enterText(find.byType(TextField), 'secret-123');
    expect(field().obscureText, isTrue);

    await tester.tap(find.byTooltip('Show password'));
    await tester.pump();
    expect(field().obscureText, isFalse);
    expect(find.text('secret-123'), findsOneWidget);

    await tester.tap(find.byTooltip('Hide password'));
    await tester.pump();
    expect(field().obscureText, isTrue);
  });
}

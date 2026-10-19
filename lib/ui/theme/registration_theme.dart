import 'package:flutter/material.dart';
import 'package:registration_client/utils/app_config.dart';

/// Shared visual language for account and registration workspace screens.
class RegistrationTheme {
  RegistrationTheme._();
  static const canvas = Color(0xFFF4F7FC);
  static const ink = Color(0xFF17243C);
  static const muted = Color(0xFF66748B);
  static const line = Color(0xFFE2E8F2);
  static const softBlue = Color(0xFFECF2FF);
  static const radius = 20.0;

  static ThemeData light() => ThemeData(
        useMaterial3: true,
        fontFamily: 'IBMPlexSans',
        scaffoldBackgroundColor: canvas,
        primaryColor: appSolidPrimary,
        colorScheme: const ColorScheme.light(
            primary: appSolidPrimary,
            secondary: appBlueShade1,
            surface: Colors.white,
            onSurface: ink,
            background: canvas,
            onBackground: ink,
            error: Color(0xFFB42318)),
        textTheme: const TextTheme(
          headlineSmall: TextStyle(
              fontSize: 28,
              height: 1.2,
              fontWeight: FontWeight.w600,
              letterSpacing: -0.7,
              color: ink),
          titleLarge:
              TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: ink),
          titleMedium:
              TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: ink),
          bodyLarge: TextStyle(fontSize: 16, height: 1.5, color: ink),
          bodyMedium: TextStyle(fontSize: 14, height: 1.5, color: muted),
        ),
        appBarTheme: const AppBarTheme(
            backgroundColor: canvas,
            foregroundColor: ink,
            elevation: 0,
            centerTitle: false,
            titleTextStyle: TextStyle(
                fontFamily: 'IBMPlexSans',
                fontSize: 19,
                fontWeight: FontWeight.w600,
                color: ink)),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFFF8FAFD),
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
          labelStyle: const TextStyle(color: muted),
          border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: line)),
          enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: line)),
          focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: appSolidPrimary, width: 1.5)),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
            style: ElevatedButton.styleFrom(
                backgroundColor: appSolidPrimary,
                foregroundColor: Colors.white,
                minimumSize: const Size(48, 52),
                elevation: 0,
                textStyle: const TextStyle(
                    fontFamily: 'IBMPlexSans',
                    fontSize: 16,
                    fontWeight: FontWeight.w600),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)))),
        outlinedButtonTheme: OutlinedButtonThemeData(
            style: OutlinedButton.styleFrom(
                minimumSize: const Size(48, 52),
                foregroundColor: appSolidPrimary,
                side: const BorderSide(color: line),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)))),
        snackBarTheme: SnackBarThemeData(
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14))),
      );

  static BoxDecoration surface({bool shadow = false}) => BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(color: line),
        boxShadow: shadow
            ? const [
                BoxShadow(
                    color: Color(0x0A182C58),
                    blurRadius: 32,
                    offset: Offset(0, 12))
              ]
            : null,
      );
}

/// Responsive account layout. The form stays readable on tablets and scrolls
/// above the keyboard on phones instead of stretching to the viewport width.
class AccountShell extends StatelessWidget {
  const AccountShell(
      {super.key,
      required this.title,
      required this.description,
      required this.child,
      this.footer,
      this.onLogoDoubleTap,
      this.onLogoLongPress});
  final String title;
  final String description;
  final Widget child;
  final Widget? footer;
  final VoidCallback? onLogoDoubleTap;
  final VoidCallback? onLogoLongPress;

  @override
  Widget build(BuildContext context) => Theme(
        data: RegistrationTheme.light(),
        child: Scaffold(
          body: SafeArea(child: LayoutBuilder(builder: (context, constraints) {
            final wide = constraints.maxWidth >= 840;
            final heading =
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              GestureDetector(
                  onDoubleTap: onLogoDoubleTap,
                  onLongPress: onLogoLongPress,
                  child: Image.asset(appIcon,
                      height: 38, fit: BoxFit.contain, semanticLabel: 'MOSIP')),
              const SizedBox(height: 40),
              Text(title,
                  style: Theme.of(context)
                      .textTheme
                      .headlineSmall
                      ?.copyWith(fontSize: wide ? 38 : 30)),
              const SizedBox(height: 14),
              Text(description,
                  style: Theme.of(context)
                      .textTheme
                      .bodyLarge
                      ?.copyWith(color: RegistrationTheme.muted)),
            ]);
            return SingleChildScrollView(
              padding: EdgeInsets.symmetric(
                  horizontal: wide ? 48 : 24, vertical: 32),
              child: Center(
                  child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 1080),
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (wide)
                              Row(
                                  crossAxisAlignment: CrossAxisAlignment.center,
                                  children: [
                                    Expanded(
                                        child: Padding(
                                            padding: const EdgeInsets.only(
                                                right: 72),
                                            child: heading)),
                                    Expanded(child: child),
                                  ])
                            else ...[
                              heading,
                              const SizedBox(height: 32),
                              child
                            ],
                            if (footer != null) ...[
                              const SizedBox(height: 24),
                              footer!
                            ],
                          ]))),
            );
          })),
        ),
      );
}

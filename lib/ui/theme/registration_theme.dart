import 'package:flutter/material.dart';
import 'package:registration_client/utils/app_config.dart';

/// Design tokens and theme for the operator screens, matched to the Desktop
/// client (`website/`, paper theme). Spec: docs/ui/design-system.md.
///
/// Widgets read colours, type, spacing, radii and motion from here instead of
/// literals, so the whole app follows one change.
class RegistrationTheme {
  RegistrationTheme._();

  // ---- Colour: paper -------------------------------------------------------
  static const canvas = Color(0xFFF7F7F5); // paper-0
  static const card = Color(0xFFFFFFFF);
  static const cardAlt = Color(0xFFFAFAF8); // paper-1
  static const sunken = Color(0xFFEDEDE9); // paper-2
  static const sunkenStrong = Color(0xFFE0E0DB); // paper-3
  static const ink = Color(0xFF0A0A0B); // fg-on-paper
  static const muted = Color(0xFF64646A); // mute-on-paper
  static const line = Color(0xFFE4E4E4); // 11% ink on white
  static const lineStrong = Color(0xFFC9C9C9); // 22% ink on white
  /// Field outlines need 3:1 against white (WCAG 1.4.11); Desktop's 22% line does not.
  static const inputBorder = Color(0xFF86868B);
  static const accent = Color(0xFF3B6FE8);
  /// Pressed accent, and every blue text/link on paper (accent text fails AA).
  static const accentPress = Color(0xFF2F5FD0);
  static const accentTint = Color(0xFFE7EEFC);
  static const onAccent = Color(0xFFFFFFFF);
  static const navy = Color(0xFF0F1E4A);
  static const slate = Color(0xFF6F7885);
  static const ok = Color(0xFF3B6948);
  static const okTint = Color(0xFFEDF2EF);
  static const okLine = Color(0xFF9DB4A4);
  static const warn = Color(0xFF7A5A1E);
  static const warnTint = Color(0xFFF3F0EB);
  static const warnLine = Color(0xFFBCAC8E);
  static const err = Color(0xFF9A3F37);
  static const errTint = Color(0xFFF6EEED);
  static const errLine = Color(0xFFCC9F9B);
  static const focus = Color(0xFF2F5FD0);
  /// Snackbar action on the inverted (ink) toast.
  static const focusOnInk = Color(0xFF86A6F5);
  static const scrim = Color(0x6B141416);

  /// Kept for existing callers.
  static const softBlue = accentTint;

  // ---- Shape ---------------------------------------------------------------
  static const double r = 5;
  static const double rLg = 8;
  static const double rBadge = 3;
  static const double rPill = 999;
  /// Kept for existing callers: panels and cards.
  static const double radius = rLg;

  // ---- Space (4-pt grid) ---------------------------------------------------
  static const double s1 = 4;
  static const double s2 = 8;
  static const double s3 = 12;
  static const double s4 = 16;
  static const double s5 = 20;
  static const double s6 = 24;
  static const double s8 = 32;
  static const double s10 = 40;
  static const double gutterPhone = 16;
  static const double gutterTablet = 24;
  static const double formMaxWidth = 560;
  static const double pageMaxWidth = 1100;

  // ---- Size ----------------------------------------------------------------
  static const double minTouch = 48;
  static const double buttonHeight = 52;
  static const double inputHeight = 52;
  static const double rowHeight = 56;
  static const double iconTile = 40;
  static const double iconInline = 20;
  static const double iconNav = 24;

  // ---- Motion --------------------------------------------------------------
  static const Curve ease = Cubic(0.22, 1, 0.36, 1);
  static const Duration fast = Duration(milliseconds: 160);
  static const Duration base = Duration(milliseconds: 280);
  static const Duration slow = Duration(milliseconds: 480);
  static const Duration stagger = Duration(milliseconds: 40);

  /// True when the platform asks for reduced motion; callers skip animation.
  static bool reduceMotion(BuildContext context) =>
      MediaQuery.maybeOf(context)?.disableAnimations ?? false;

  // ---- Type (IBM Plex Sans; Desktop sizes/weights) -------------------------
  static const String fontFamily = 'IBMPlexSans';

  /// Type-scale sizes, for widgets that build their own TextStyle.
  static const double fsXs = 12.5;
  static const double fsSmall = 14.5;
  static const double fsBody = 16;
  static const double fsHeading = 17;
  static const double fsTitle = 21;
  static const double fsDisplay = 28;

  static const TextStyle display = TextStyle(
      fontFamily: fontFamily,
      fontSize: 28,
      height: 1.15,
      fontWeight: FontWeight.w600,
      letterSpacing: -0.6,
      color: ink);
  static const TextStyle title = TextStyle(
      fontFamily: fontFamily,
      fontSize: 21,
      height: 1.25,
      fontWeight: FontWeight.w600,
      letterSpacing: -0.3,
      color: ink);
  static const TextStyle heading = TextStyle(
      fontFamily: fontFamily,
      fontSize: 17,
      height: 1.3,
      fontWeight: FontWeight.w600,
      letterSpacing: -0.1,
      color: ink);
  static const TextStyle body = TextStyle(
      fontFamily: fontFamily,
      fontSize: 16,
      height: 1.5,
      fontWeight: FontWeight.w400,
      color: ink);
  static const TextStyle bodyStrong = TextStyle(
      fontFamily: fontFamily,
      fontSize: 16,
      height: 1.5,
      fontWeight: FontWeight.w500,
      color: ink);
  static const TextStyle small = TextStyle(
      fontFamily: fontFamily,
      fontSize: 14.5,
      height: 1.45,
      fontWeight: FontWeight.w400,
      color: muted);
  static const TextStyle xs = TextStyle(
      fontFamily: fontFamily,
      fontSize: 12.5,
      height: 1.4,
      fontWeight: FontWeight.w500,
      color: muted);
  /// Section label (Desktop mono uppercase); callers upper-case the text.
  static const TextStyle label = TextStyle(
      fontFamily: fontFamily,
      fontSize: 11.5,
      height: 1.3,
      fontWeight: FontWeight.w600,
      letterSpacing: 1.2,
      color: muted);
  /// Label above a form field.
  static const TextStyle fieldLabel = TextStyle(
      fontFamily: fontFamily,
      fontSize: 14.5,
      height: 1.4,
      fontWeight: FontWeight.w500,
      color: ink);
  static const TextStyle button = TextStyle(
      fontFamily: fontFamily,
      fontSize: 16,
      height: 1.2,
      fontWeight: FontWeight.w600);
  static const TextStyle stat = TextStyle(
      fontFamily: fontFamily,
      fontSize: 32,
      height: 1.1,
      fontWeight: FontWeight.w600,
      letterSpacing: -0.8);

  static const TextTheme textTheme = TextTheme(
    displaySmall: display,
    headlineMedium: display,
    headlineSmall: display,
    titleLarge: title,
    titleMedium: heading,
    titleSmall: bodyStrong,
    bodyLarge: body,
    bodyMedium: TextStyle(
        fontFamily: fontFamily,
        fontSize: 14.5,
        height: 1.45,
        fontWeight: FontWeight.w400,
        color: ink),
    bodySmall: small,
    labelLarge: button,
    labelMedium: xs,
    labelSmall: label,
  );

  static OutlineInputBorder _field(Color color, [double width = 1]) =>
      OutlineInputBorder(
          borderRadius: BorderRadius.circular(r),
          borderSide: BorderSide(color: color, width: width));

  static RoundedRectangleBorder get _buttonShape =>
      RoundedRectangleBorder(borderRadius: BorderRadius.circular(r));

  static ThemeData light() => ThemeData(
        useMaterial3: true,
        fontFamily: fontFamily,
        scaffoldBackgroundColor: canvas,
        canvasColor: canvas,
        primaryColor: accent,
        dividerColor: line,
        colorScheme: const ColorScheme.light(
          primary: accent,
          onPrimary: onAccent,
          primaryContainer: accentTint,
          onPrimaryContainer: navy,
          secondary: accentPress,
          onSecondary: onAccent,
          surface: card,
          onSurface: ink,
          surfaceVariant: sunken,
          onSurfaceVariant: muted,
          surfaceTint: Colors.transparent,
          background: canvas,
          onBackground: ink,
          outline: inputBorder,
          outlineVariant: line,
          error: err,
          onError: onAccent,
          errorContainer: errTint,
          onErrorContainer: err,
          scrim: scrim,
        ),
        textTheme: textTheme,
        iconTheme: const IconThemeData(color: ink, size: iconNav),
        appBarTheme: const AppBarTheme(
            backgroundColor: card,
            foregroundColor: ink,
            surfaceTintColor: Colors.transparent,
            elevation: 0,
            scrolledUnderElevation: 0,
            centerTitle: false,
            shape: Border(bottom: BorderSide(color: line)),
            titleTextStyle: heading),
        cardTheme: CardTheme(
            color: card,
            surfaceTintColor: Colors.transparent,
            elevation: 0,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(rLg),
                side: const BorderSide(color: line))),
        dividerTheme:
            const DividerThemeData(color: line, thickness: 1, space: 1),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: card,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: s4, vertical: s4),
          labelStyle: small,
          floatingLabelStyle: const TextStyle(color: accentPress),
          hintStyle: small,
          helperStyle: small,
          errorStyle: small.copyWith(color: err),
          errorMaxLines: 3,
          prefixIconColor: muted,
          suffixIconColor: muted,
          border: _field(inputBorder),
          enabledBorder: _field(inputBorder),
          disabledBorder: _field(line),
          focusedBorder: _field(accent, 2),
          errorBorder: _field(err),
          focusedErrorBorder: _field(err, 2),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
            style: ElevatedButton.styleFrom(
                backgroundColor: accent,
                foregroundColor: onAccent,
                disabledBackgroundColor: sunken,
                disabledForegroundColor: muted,
                surfaceTintColor: Colors.transparent,
                minimumSize: const Size(minTouch, buttonHeight),
                padding: const EdgeInsets.symmetric(horizontal: s5),
                elevation: 0,
                textStyle: button,
                shape: _buttonShape)),
        filledButtonTheme: FilledButtonThemeData(
            style: FilledButton.styleFrom(
                backgroundColor: accent,
                foregroundColor: onAccent,
                disabledBackgroundColor: sunken,
                disabledForegroundColor: muted,
                minimumSize: const Size(minTouch, buttonHeight),
                padding: const EdgeInsets.symmetric(horizontal: s5),
                textStyle: button,
                shape: _buttonShape)),
        outlinedButtonTheme: OutlinedButtonThemeData(
            style: OutlinedButton.styleFrom(
                foregroundColor: ink,
                disabledForegroundColor: muted,
                minimumSize: const Size(minTouch, minTouch),
                padding: const EdgeInsets.symmetric(horizontal: s5),
                side: const BorderSide(color: lineStrong),
                textStyle: button,
                shape: _buttonShape)),
        textButtonTheme: TextButtonThemeData(
            style: TextButton.styleFrom(
                foregroundColor: accentPress,
                minimumSize: const Size(minTouch, minTouch),
                padding: const EdgeInsets.symmetric(horizontal: s3),
                textStyle: button,
                shape: _buttonShape)),
        iconButtonTheme: IconButtonThemeData(
            style: IconButton.styleFrom(
                foregroundColor: ink,
                minimumSize: const Size(minTouch, minTouch))),
        checkboxTheme: CheckboxThemeData(
            materialTapTargetSize: MaterialTapTargetSize.padded,
            side: const BorderSide(color: inputBorder, width: 1.5),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(rBadge)),
            fillColor: MaterialStateProperty.resolveWith((states) =>
                states.contains(MaterialState.selected) ? accent : null),
            checkColor: const MaterialStatePropertyAll(onAccent)),
        radioTheme: RadioThemeData(
            materialTapTargetSize: MaterialTapTargetSize.padded,
            fillColor: MaterialStateProperty.resolveWith((states) =>
                states.contains(MaterialState.selected)
                    ? accent
                    : inputBorder)),
        switchTheme: SwitchThemeData(
            thumbColor: MaterialStateProperty.resolveWith((states) =>
                states.contains(MaterialState.selected) ? onAccent : muted),
            trackColor: MaterialStateProperty.resolveWith((states) =>
                states.contains(MaterialState.selected) ? accent : sunken)),
        chipTheme: ChipThemeData(
            backgroundColor: card,
            selectedColor: sunken,
            checkmarkColor: ink,
            side: const BorderSide(color: lineStrong),
            labelStyle: small.copyWith(color: ink),
            padding: const EdgeInsets.symmetric(horizontal: s3),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(rPill))),
        dialogTheme: DialogTheme(
            backgroundColor: card,
            surfaceTintColor: Colors.transparent,
            elevation: 6,
            titleTextStyle: title,
            contentTextStyle: body,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(rLg),
                side: const BorderSide(color: lineStrong))),
        bottomSheetTheme: const BottomSheetThemeData(
            backgroundColor: card,
            surfaceTintColor: Colors.transparent,
            shape: RoundedRectangleBorder(
                borderRadius:
                    BorderRadius.vertical(top: Radius.circular(rLg)))),
        popupMenuTheme: PopupMenuThemeData(
            color: card,
            surfaceTintColor: Colors.transparent,
            textStyle: body,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(r),
                side: const BorderSide(color: line))),
        listTileTheme: const ListTileThemeData(
            iconColor: muted,
            textColor: ink,
            minVerticalPadding: s3,
            contentPadding: EdgeInsets.symmetric(horizontal: s4)),
        navigationBarTheme: NavigationBarThemeData(
            backgroundColor: card,
            surfaceTintColor: Colors.transparent,
            indicatorColor: sunken,
            height: 64,
            labelTextStyle: MaterialStateProperty.resolveWith((states) =>
                xs.copyWith(
                    color: states.contains(MaterialState.selected)
                        ? ink
                        : muted))),
        bottomNavigationBarTheme: const BottomNavigationBarThemeData(
            backgroundColor: card,
            elevation: 0,
            selectedItemColor: ink,
            unselectedItemColor: muted,
            selectedLabelStyle: xs,
            unselectedLabelStyle: xs,
            type: BottomNavigationBarType.fixed),
        tabBarTheme: const TabBarTheme(
            labelColor: ink,
            unselectedLabelColor: muted,
            indicatorColor: accent,
            labelStyle: bodyStrong,
            unselectedLabelStyle: body),
        progressIndicatorTheme: const ProgressIndicatorThemeData(
            color: accent,
            linearTrackColor: sunken,
            circularTrackColor: sunken),
        snackBarTheme: SnackBarThemeData(
            behavior: SnackBarBehavior.floating,
            backgroundColor: ink,
            contentTextStyle: body.copyWith(color: card),
            actionTextColor: focusOnInk,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(r))),
        tooltipTheme: TooltipThemeData(
            decoration:
                BoxDecoration(color: ink, borderRadius: BorderRadius.circular(r)),
            textStyle: xs.copyWith(color: card)),
      );

  /// Panel/card surface: white, 1px hairline, 8px radius, flat.
  /// [shadow] is only for floating surfaces (dialogs, sheets).
  static BoxDecoration surface({bool shadow = false}) => BoxDecoration(
        color: card,
        borderRadius: BorderRadius.circular(rLg),
        border: Border.all(color: line),
        boxShadow: shadow
            ? const [
                BoxShadow(
                    color: Color(0x140A0A0B),
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
                      height: 36, fit: BoxFit.contain, semanticLabel: 'MOSIP')),
              const SizedBox(height: RegistrationTheme.s8),
              Text(title,
                  style: RegistrationTheme.display
                      .copyWith(fontSize: wide ? 34 : null)),
              const SizedBox(height: RegistrationTheme.s3),
              Text(description,
                  style: RegistrationTheme.body
                      .copyWith(color: RegistrationTheme.muted)),
            ]);
            return SingleChildScrollView(
              padding: EdgeInsets.symmetric(
                  horizontal: wide
                      ? RegistrationTheme.s10
                      : RegistrationTheme.gutterTablet,
                  vertical: RegistrationTheme.s8),
              child: Center(
                  child: ConstrainedBox(
                      constraints: const BoxConstraints(
                          maxWidth: RegistrationTheme.pageMaxWidth),
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (wide)
                              Row(
                                  crossAxisAlignment: CrossAxisAlignment.center,
                                  children: [
                                    Expanded(
                                        child: Padding(
                                            padding:
                                                const EdgeInsetsDirectional.only(
                                                    end: 72),
                                            child: heading)),
                                    Expanded(child: child),
                                  ])
                            else ...[
                              heading,
                              const SizedBox(height: RegistrationTheme.s8),
                              child
                            ],
                            if (footer != null) ...[
                              const SizedBox(height: RegistrationTheme.s6),
                              footer!
                            ],
                          ]))),
            );
          })),
        ),
      );
}

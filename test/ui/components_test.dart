import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:registration_client/ui/theme/components.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

Widget host(Widget child,
        {bool reduceMotion = false,
        double textScale = 1,
        TextDirection dir = TextDirection.ltr}) =>
    MaterialApp(
      theme: RegistrationTheme.light(),
      home: MediaQuery(
        data: MediaQueryData(
            size: const Size(390, 844),
            disableAnimations: reduceMotion,
            textScaleFactor: textScale),
        child: Directionality(
            textDirection: dir,
            child: Scaffold(
                body: SingleChildScrollView(
                    padding: const EdgeInsets.all(16), child: child))),
      ),
    );

void main() {
  testWidgets('status badge pairs an icon with the word', (tester) async {
    await tester.pumpWidget(host(const Column(children: [
      StatusBadge('Synced', tone: StatusTone.ok),
      StatusBadge('Pending', tone: StatusTone.warn),
      StatusBadge('Failed', tone: StatusTone.err),
    ])));
    for (final word in ['Synced', 'Pending', 'Failed']) {
      final badge = find.ancestor(
          of: find.text(word), matching: find.byType(StatusBadge));
      expect(find.descendant(of: badge, matching: find.byType(Icon)),
          findsOneWidget);
    }
  });

  testWidgets('reduced motion: FadeRise and SuccessCheck render final frame',
      (tester) async {
    await tester.pumpWidget(host(
        Column(children: [
          FadeRise(delay: FadeRise.staggerFor(3), child: const Text('card')),
          const SuccessCheck(semanticsLabel: 'done'),
          const SyncSpinner(active: true),
        ]),
        reduceMotion: true));
    await tester.pump();
    final opacity = tester.widget<Opacity>(find
        .ancestor(of: find.text('card'), matching: find.byType(Opacity))
        .first);
    expect(opacity.opacity, 1);
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('motion settles under 600ms when enabled', (tester) async {
    await tester.pumpWidget(host(Column(
        children: List<Widget>.generate(
            12,
            (i) => FadeRise(
                delay: FadeRise.staggerFor(i), child: Text('item $i'))))));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 299));
    final last = tester.widget<Opacity>(find
        .ancestor(of: find.text('item 11'), matching: find.byType(Opacity))
        .first);
    expect(last.opacity, 1);
  });

  testWidgets('step progress exposes position, not colour only',
      (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(host(const StepProgress(
        total: 4, current: 1, semanticsLabel: 'Demographic details')));
    final node = tester.getSemantics(find.byType(StepProgress));
    expect(node.label, 'Demographic details');
    expect(node.value, '2/4');
    handle.dispose();
  });

  testWidgets('Arabic RTL at 200% text: no overflow, icon on the start side',
      (tester) async {
    await tester.pumpWidget(host(
        const Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          SectionLabel('مهام التسجيل'),
          InlineAlert(
              message: 'يرجى مزامنة البيانات قبل بدء تسجيل جديد',
              tone: StatusTone.warn),
          EmptyState(
              icon: Icons.inbox_outlined,
              title: 'لا توجد حزم',
              message: 'ستظهر الحزم المنشأة هنا'),
          StatusBadge('معلق', tone: StatusTone.warn),
        ]),
        textScale: 2,
        dir: TextDirection.rtl));
    expect(tester.takeException(), isNull);
    final alert = find.byType(InlineAlert);
    final icon = find.descendant(of: alert, matching: find.byType(Icon));
    expect(tester.getCenter(icon).dx, greaterThan(tester.getCenter(alert).dx));
  });

  test('theme meets touch and shape tokens', () {
    final t = RegistrationTheme.light();
    final primary = t.elevatedButtonTheme.style!.minimumSize!.resolve({})!;
    expect(primary.height, RegistrationTheme.buttonHeight);
    final outlined = t.outlinedButtonTheme.style!.minimumSize!.resolve({})!;
    expect(outlined.height, greaterThanOrEqualTo(RegistrationTheme.minTouch));
    final text = t.textButtonTheme.style!.minimumSize!.resolve({})!;
    expect(text.height, greaterThanOrEqualTo(RegistrationTheme.minTouch));
    expect(t.colorScheme.primary, RegistrationTheme.accent);
    expect(t.scaffoldBackgroundColor, RegistrationTheme.canvas);
  });
}

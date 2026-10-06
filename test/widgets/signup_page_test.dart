import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:registration_client/platform_android/demo_account_service.dart';
import 'package:registration_client/ui/signup/signup_page.dart';

class FakeDemo implements DemoAccountService {
  FakeDemo({this.available = true});
  final bool available;
  final Map<String, String> created = <String, String>{};

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<void> create({required String username, required String fullName, required String password}) async {
    if (created.containsKey(username)) throw PlatformException(code: 'EXISTS');
    created[username] = password;
  }

  @override
  Future<DemoAccount?> verify(String username, String password) async =>
      created[username] == password ? DemoAccount(username: username, fullName: username) : null;
}

Widget host(Widget page, {void Function(Object?)? onPop}) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () async {
            final r = await Navigator.of(context).push<Object?>(MaterialPageRoute<Object?>(builder: (_) => page));
            onPop?.call(r);
          },
          child: const Text('open'),
        ),
      ),
    );

Future<void> open(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1080, 2600);
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

Future<void> fillRequest(WidgetTester tester) async {
  await tester.enterText(find.widgetWithText(TextFormField, 'Full name'), 'Asha Rao');
  await tester.enterText(find.widgetWithText(TextFormField, 'Email'), 'asha@example.org');
  await tester.enterText(find.widgetWithText(TextFormField, 'Preferred username'), 'asha.rao');
  await tester.enterText(find.widgetWithText(TextFormField, 'Registration centre'), 'Centre 10011');
}

void main() {
  testWidgets('request is validated, then sent by email without any password', (tester) async {
    Uri? sent;
    await tester.pumpWidget(host(SignupPage(
      demoAccounts: FakeDemo(),
      adminEmailLoader: () async => 'admin@mosip.example',
      launcher: (uri) async {
        sent = uri;
        return true;
      },
    )));
    await open(tester);
    expect(find.text('Request an account'), findsOneWidget);

    await tester.tap(find.text('Send request to administrator'));
    await tester.pumpAndSettle();
    expect(find.text('This field is required'), findsWidgets);
    expect(sent, isNull);

    await fillRequest(tester);
    await tester.enterText(find.widgetWithText(TextFormField, 'Email'), 'not-an-email');
    await tester.tap(find.text('Send request to administrator'));
    await tester.pumpAndSettle();
    expect(find.text('Enter a valid email address'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Email'), 'asha@example.org');
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'demo-pass-1');
    await tester.tap(find.text('Send request to administrator'));
    await tester.pumpAndSettle();
    expect(sent, isNotNull);
    expect(sent!.scheme, 'mailto');
    expect(sent!.path, 'admin@mosip.example');
    final body = Uri.decodeComponent(sent!.query);
    expect(body, contains('Asha Rao'));
    expect(body, contains('asha.rao'));
    expect(body, contains('Registration officer'));
    expect(body, isNot(contains('demo-pass-1')));
    expect(find.textContaining('Request opened in your email app'), findsOneWidget);
  });

  testWidgets('without an email app the request is copied to the clipboard', (tester) async {
    String? clip;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') clip = (call.arguments as Map)['text'] as String?;
      return null;
    });
    await tester.pumpWidget(host(SignupPage(demoAccounts: FakeDemo(available: false), launcher: (_) async => false)));
    await open(tester);
    expect(find.byKey(const ValueKey<String>('demo-section')), findsNothing);
    await fillRequest(tester);
    await tester.tap(find.text('Send request to administrator'));
    await tester.pumpAndSettle();
    expect(clip, contains('asha.rao'));
    expect(find.textContaining('The request was copied'), findsOneWidget);
  });

  testWidgets('demo account: password rules, mismatch, then created and returned', (tester) async {
    final demo = FakeDemo();
    Object? popped;
    await tester.pumpWidget(host(SignupPage(demoAccounts: demo, launcher: (_) async => true), onPop: (r) => popped = r));
    await open(tester);
    expect(find.byKey(const ValueKey<String>('demo-section')), findsOneWidget);

    await fillRequest(tester);
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'short');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirm password'), 'short');
    await tester.ensureVisible(find.text('Create demo account'));
    await tester.tap(find.text('Create demo account'));
    await tester.pumpAndSettle();
    expect(find.text('Use at least 8 characters'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'demo-pass-1');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirm password'), 'demo-pass-2');
    await tester.tap(find.text('Create demo account'));
    await tester.pumpAndSettle();
    expect(find.text('Passwords do not match'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Confirm password'), 'demo-pass-1');
    await tester.tap(find.text('Create demo account'));
    await tester.pumpAndSettle();
    expect(demo.created['asha.rao'], 'demo-pass-1');
    expect(popped, 'asha.rao');
  });
}

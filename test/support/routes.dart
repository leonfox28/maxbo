import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// A router showing [home] at `/`, with stand-ins for the pages it can open
/// that just say where they were opened with.
GoRouter stubRouter(Widget home) => GoRouter(
  routes: [
    GoRoute(path: '/', builder: (_, _) => home),
    GoRoute(
      path: '/u/:uid',
      builder: (_, state) => Text('user ${state.pathParameters['uid']}'),
    ),
    GoRoute(
      path: '/n/:name',
      builder: (_, state) => Text('name ${state.pathParameters['name']}'),
    ),
    GoRoute(
      path: '/status/:id',
      builder: (_, state) => Text('status ${state.pathParameters['id']}'),
    ),
    GoRoute(
      path: '/results',
      builder: (_, state) => Scaffold(
        appBar: AppBar(),
        body: Text('results ${state.uri.queryParameters['q']}'),
      ),
    ),
    GoRoute(
      path: '/web',
      builder: (context, state) => Scaffold(
        appBar: AppBar(),
        body: Text('web ${state.uri.queryParameters['url']}'),
      ),
    ),
  ],
);

/// The URL a stand-in `/web` page was opened with.
Uri openedWebUrl(WidgetTester tester) {
  final text = tester.widget<Text>(find.textContaining('web ')).data!;
  return Uri.parse(text.substring('web '.length));
}

/// The query a stand-in `/results` page was opened with.
String openedSearch(WidgetTester tester) {
  final text = tester.widget<Text>(find.textContaining('results ')).data!;
  return text.substring('results '.length);
}

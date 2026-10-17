import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/widgets/adaptive_layout.dart';
import 'package:maxbo/widgets/floating_pill.dart';

void main() {
  void setWidth(WidgetTester tester, double width) {
    tester.view.physicalSize = Size(width, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  test('window sizes follow Material 3', () {
    expect(WindowSize.fromWidth(599), WindowSize.compact);
    expect(WindowSize.fromWidth(600), WindowSize.medium);
    expect(WindowSize.fromWidth(839), WindowSize.medium);
    expect(WindowSize.fromWidth(840), WindowSize.expanded);
  });

  test('reading insets centre the column, never below zero', () {
    expect(readingInsets(400), EdgeInsets.zero);
    expect(readingInsets(readingWidth), EdgeInsets.zero);
    expect(readingInsets(1000), const EdgeInsets.symmetric(horizontal: 180));
  });

  testWidgets('ReadingList keeps items to the column, with media padding', (
    tester,
  ) async {
    setWidth(tester, 1000);
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(1000, 800),
            padding: EdgeInsets.only(top: 20, bottom: 30),
          ),
          child: ReadingList(
            padding: const EdgeInsets.only(top: 5),
            builder: (context, padding) => ListView(
              padding: padding,
              children: const [SizedBox(height: 50, child: Text('条目'))],
            ),
          ),
        ),
      ),
    );

    final item = tester.getRect(find.text('条目'));
    expect(item.left, 180);
    expect(item.width, readingWidth);
    expect(item.top, 25);
    final list = tester.widget<ListView>(find.byType(ListView));
    expect(list.padding, const EdgeInsets.fromLTRB(180, 25, 180, 30));
  });

  testWidgets('ReadingSlivers centre their slivers in a wide scroll view', (
    tester,
  ) async {
    setWidth(tester, 1000);
    await tester.pumpWidget(
      MaterialApp(
        home: CustomScrollView(
          slivers: [
            ReadingSlivers(
              slivers: [
                const SliverToBoxAdapter(child: SizedBox(height: 50)),
                SliverList.list(
                  children: const [SizedBox(height: 50, child: Text('条目'))],
                ),
              ],
            ),
          ],
        ),
      ),
    );

    final item = tester.getRect(find.text('条目'));
    expect(item.left, 180);
    expect(item.width, readingWidth);
    expect(item.top, 50);
  });

  testWidgets('ReadingSlivers leave a narrow window as it is', (tester) async {
    setWidth(tester, 400);
    await tester.pumpWidget(
      const MaterialApp(
        home: CustomScrollView(
          slivers: [
            ReadingSlivers(
              slivers: [
                SliverToBoxAdapter(
                  child: SizedBox(height: 50, child: Text('条目')),
                ),
              ],
            ),
          ],
        ),
      ),
    );

    expect(tester.getRect(find.text('条目')), const Rect.fromLTWH(0, 0, 400, 50));
  });

  testWidgets('ReadingPanes are reading columns, centred together', (
    tester,
  ) async {
    Future<(Rect, Rect)> panes(double window) async {
      setWidth(tester, window);
      await tester.pumpWidget(
        const MaterialApp(
          home: ReadingPanes(start: Text('左'), end: Text('右')),
        ),
      );
      return (tester.getRect(find.text('左')), tester.getRect(find.text('右')));
    }

    var (start, end) = await panes(1001);
    expect(start, const Rect.fromLTWH(0, 0, 500, 800));
    expect(end, const Rect.fromLTWH(501, 0, 500, 800));

    (start, end) = await panes(1481);
    expect(start.left, 100);
    expect(start.width, readingWidth);
    expect(end.left, 100 + readingWidth + 1);
    expect(end.width, readingWidth);
  });

  testWidgets('a stretched pill spans the column, not a wide window', (
    tester,
  ) async {
    final pill = find.descendant(
      of: find.byType(FloatingPill),
      matching: find.byType(Material),
    );
    Future<double> pillWidth(double window) async {
      setWidth(tester, window);
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            bottomNavigationBar: FloatingPill(
              stretch: true,
              child: Row(children: [Expanded(child: Text('写评论…'))]),
            ),
          ),
        ),
      );
      return tester.getSize(pill).width;
    }

    expect(await pillWidth(400), 400 - 32);
    expect(await pillWidth(1000), readingWidth - 32);
    expect(tester.getCenter(pill).dx, 500);
  });
}

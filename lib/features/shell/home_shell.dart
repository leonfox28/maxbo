import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../widgets/bars_on_scroll.dart';
import '../../widgets/floating_pill.dart';
import '../../widgets/tab_reselect.dart';

/// Scaffold hosting the top-level tabs, switched by a floating pill that
/// hides while the user scrolls down and comes back when they scroll up.
/// Tapping the open tab again tells its pages through [TabReselect].
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.shell, required this.branches});

  final StatefulNavigationShell shell;

  /// The tabs' navigators, in the order of the tab bar.
  final List<Widget> branches;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  static const _destinations = [
    _Destination(Icons.home_outlined, Icons.home, '首页'),
    _Destination(
      Icons.local_fire_department_outlined,
      Icons.local_fire_department,
      '热搜',
    ),
    _Destination(Icons.notifications_outlined, Icons.notifications, '通知'),
    _Destination(Icons.person_outline, Icons.person, '我'),
  ];

  final _bars = BarsOnScroll();
  late final _reselected = [for (final _ in widget.branches) _Taps()];

  @override
  void didUpdateWidget(HomeShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    // A tab opened from elsewhere, e.g. after logging in, shows the bar.
    if (widget.shell.currentIndex != oldWidget.shell.currentIndex) {
      _bars.show();
    }
  }

  @override
  void dispose() {
    for (final taps in _reselected) {
      taps.dispose();
    }
    super.dispose();
  }

  bool _onScroll(ScrollUpdateNotification n) {
    if (_bars.onScroll(n)) setState(() {});
    return false;
  }

  void _select(int index) {
    final shell = widget.shell;
    setState(_bars.show);
    if (index == shell.currentIndex) {
      _reselected[index].tap();
    } else {
      shell.goBranch(index);
    }
  }

  @override
  Widget build(BuildContext context) {
    final current = widget.shell.currentIndex;
    return Scaffold(
      // Lists scroll on behind the pill; the scaffold pads them for it.
      extendBody: true,
      body: NotificationListener<ScrollUpdateNotification>(
        onNotification: _onScroll,
        child: IndexedStack(
          index: current,
          children: [
            for (final (i, branch) in widget.branches.indexed)
              // As StatefulShellRoute.indexedStack has it.
              Offstage(
                offstage: i != current,
                child: TickerMode(
                  enabled: i == current,
                  child: TabReselect(taps: _reselected[i], child: branch),
                ),
              ),
          ],
        ),
      ),
      bottomNavigationBar: AnimatedSlide(
        offset: _bars.visible ? Offset.zero : const Offset(0, 1.5),
        duration: BarsOnScroll.duration,
        curve: BarsOnScroll.curve,
        child: _FloatingNavBar(
          destinations: _destinations,
          selectedIndex: current,
          onSelected: _select,
        ),
      ),
    );
  }
}

class _Taps extends ChangeNotifier {
  void tap() => notifyListeners();
}

class _Destination {
  const _Destination(this.icon, this.selectedIcon, this.label);

  final IconData icon;
  final IconData selectedIcon;
  final String label;
}

class _FloatingNavBar extends StatelessWidget {
  const _FloatingNavBar({
    required this.destinations,
    required this.selectedIndex,
    required this.onSelected,
  });

  final List<_Destination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    // As wide as its items, not the screen.
    return FloatingPill(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 4,
        children: [
          for (final (i, destination) in destinations.indexed)
            _NavItem(
              destination,
              selected: i == selectedIndex,
              onTap: () => onSelected(i),
            ),
        ],
      ),
    );
  }
}

/// An icon, which when selected widens into a chip with its label.
class _NavItem extends StatelessWidget {
  const _NavItem(this.destination, {required this.selected, this.onTap});

  final _Destination destination;
  final bool selected;
  final VoidCallback? onTap;

  static const _duration = Duration(milliseconds: 250);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final foreground = selected ? colors.onPrimary : colors.onSurfaceVariant;
    return Semantics(
      button: true,
      selected: selected,
      label: destination.label,
      child: Tooltip(
        message: destination.label,
        excludeFromSemantics: true,
        child: InkWell(
          onTap: onTap,
          customBorder: const StadiumBorder(),
          child: AnimatedContainer(
            duration: _duration,
            curve: Curves.easeOutCubic,
            height: 48,
            padding: EdgeInsets.symmetric(horizontal: selected ? 20 : 16),
            decoration: ShapeDecoration(
              color: selected ? colors.primary : Colors.transparent,
              shape: const StadiumBorder(),
            ),
            child: ExcludeSemantics(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    selected ? destination.selectedIcon : destination.icon,
                    color: foreground,
                  ),
                  AnimatedSize(
                    duration: _duration,
                    curve: Curves.easeOutCubic,
                    child: selected
                        ? Padding(
                            padding: const EdgeInsets.only(left: 8),
                            child: Text(
                              destination.label,
                              style: theme.textTheme.labelLarge?.copyWith(
                                color: foreground,
                              ),
                            ),
                          )
                        : const SizedBox.shrink(),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

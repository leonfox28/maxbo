import 'package:flutter/material.dart';

import '../../data/models/status.dart';

/// Picks who can see a new post or repost, from [StatusVisibility.choices].
class VisibilityButton extends StatelessWidget {
  const VisibilityButton({
    super.key,
    required this.value,
    required this.onChanged,
  });

  final StatusVisibility value;
  final ValueChanged<StatusVisibility>? onChanged;

  static IconData _iconOf(StatusVisibility v) => switch (v) {
    StatusVisibility.public => Icons.public,
    StatusVisibility.friends => Icons.group_outlined,
    StatusVisibility.onlyMe => Icons.lock_outline,
    StatusVisibility.fans => Icons.people_outline,
  };

  @override
  Widget build(BuildContext context) {
    final onChanged = this.onChanged;
    return MenuAnchor(
      menuChildren: [
        for (final v in StatusVisibility.choices)
          MenuItemButton(
            leadingIcon: Icon(_iconOf(v)),
            trailingIcon: v == value ? const Icon(Icons.check) : null,
            onPressed: () => onChanged?.call(v),
            child: Text(v.label),
          ),
      ],
      builder: (context, menu, _) => OutlinedButton.icon(
        onPressed: onChanged == null
            ? null
            : () => menu.isOpen ? menu.close() : menu.open(),
        icon: Icon(_iconOf(value), size: 18),
        label: Text(value.label),
      ),
    );
  }
}

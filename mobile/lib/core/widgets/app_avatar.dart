import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// User avatar rendering initials or an icon on a circular neutral grey background
/// matching the target home layout.
class AppAvatar extends StatelessWidget {
  final String? name;
  final String? imageUrl;
  final double size;
  final Color? backgroundColor;
  final Color? textColor;
  final VoidCallback? onTap;

  const AppAvatar({
    super.key = const Key('app_avatar'),
    this.name,
    this.imageUrl,
    this.size = 40.0,
    this.backgroundColor,
    this.textColor,
    this.onTap,
  });

  static String extractInitials(String rawName) {
    final trimmed = rawName.trim();
    if (trimmed.isEmpty) return 'M';
    final parts = trimmed.split(RegExp(r'\s+'));
    if (parts.length == 1) {
      return parts[0].substring(0, 1).toUpperCase();
    }
    final first = parts.first.isNotEmpty ? parts.first[0] : '';
    final last = parts.last.isNotEmpty ? parts.last[0] : '';
    return '$first$last'.toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = backgroundColor ?? (isDark ? AppTokens.avatarGreyDark : AppTokens.avatarGreyLight);
    final fg = textColor ?? (isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight);
    final initials = extractInitials(name ?? 'M');

    final child = Container(
      key: const Key('app_avatar_container'),
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: bg,
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Text(
        initials,
        style: TextStyle(
          fontFamily: AppTokens.fontFamily,
          fontSize: size * 0.42,
          fontWeight: FontWeight.w700,
          color: fg,
        ),
      ),
    );

    if (onTap != null) {
      return Semantics(
        button: true,
        label: name ?? 'User Avatar',
        child: InkWell(
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: child,
        ),
      );
    }
    return child;
  }
}

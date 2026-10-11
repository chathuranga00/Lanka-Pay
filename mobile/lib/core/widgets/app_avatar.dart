import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// User avatar rendering initials on a deterministic, accessible pastel background.
class AppAvatar extends StatelessWidget {
  final String name;
  final double size;
  final VoidCallback? onTap;

  const AppAvatar({
    super.key,
    required this.name,
    this.size = 44.0,
    this.onTap,
  });

  static const List<_PastelPair> _palette = [
    _PastelPair(bg: Color(0xFFDDD6FE), fg: Color(0xFF5B21B6)), // Lavender
    _PastelPair(bg: Color(0xFFBAE6FD), fg: Color(0xFF0369A1)), // Sky
    _PastelPair(bg: Color(0xFFA7F3D0), fg: Color(0xFF047857)), // Mint
    _PastelPair(bg: Color(0xFFFECDD3), fg: Color(0xFFBE123C)), // Rose
    _PastelPair(bg: Color(0xFFFDE68A), fg: Color(0xFF92400E)), // Amber
    _PastelPair(bg: Color(0xFFFED7AA), fg: Color(0xFF9A3412)), // Peach
    _PastelPair(bg: Color(0xFFE9D5FF), fg: Color(0xFF6B21A8)), // Purple
  ];

  static String extractInitials(String rawName) {
    final trimmed = rawName.trim();
    if (trimmed.isEmpty) return '?';
    final parts = trimmed.split(RegExp(r'\s+'));
    if (parts.length == 1) {
      return parts[0].substring(0, parts[0].length >= 2 ? 2 : 1).toUpperCase();
    }
    final first = parts.first.isNotEmpty ? parts.first[0] : '';
    final last = parts.last.isNotEmpty ? parts.last[0] : '';
    return '$first$last'.toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final initials = extractInitials(name);
    final index = name.trim().isEmpty ? 0 : (name.hashCode.abs() % _palette.length);
    final colorPair = _palette[index];

    final avatar = Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: colorPair.bg,
        shape: BoxShape.circle,
        border: Border.all(
          color: Colors.white.withOpacity(0.25),
          width: 1.5,
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        initials,
        style: TextStyle(
          fontFamily: AppTokens.fontFamily,
          fontSize: size * 0.40,
          fontWeight: FontWeight.w800,
          color: colorPair.fg,
          letterSpacing: 0.5,
        ),
      ),
    );

    if (onTap != null) {
      return InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: avatar,
      );
    }
    return avatar;
  }
}

class _PastelPair {
  final Color bg;
  final Color fg;
  const _PastelPair({required this.bg, required this.fg});
}

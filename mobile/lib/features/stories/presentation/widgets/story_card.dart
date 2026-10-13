import 'package:flutter/material.dart';
import '../../../../core/theme/app_tokens.dart';
import '../../domain/story.dart';
import '../story_viewer_screen.dart';

/// Individual Story thumbnail card rendered inside the horizontal Stories row.
/// Displays a vibrant gradient ring when unseen, and a subtle border when seen.
class StoryCard extends StatelessWidget {
  final Story story;
  final List<Story> allStories;
  final int index;

  const StoryCard({
    super.key,
    required this.story,
    required this.allStories,
    required this.index,
  });

  LinearGradient _parseGradient(String val) {
    if (val.contains('#10B981')) {
      return const LinearGradient(
        colors: [Color(0xFF10B981), Color(0xFF06B6D4)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (val.contains('#EF4444')) {
      return const LinearGradient(
        colors: [Color(0xFFEF4444), Color(0xFFF59E0B)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (val.contains('#8B5CF6') || val.contains('#EC4899')) {
      return const LinearGradient(
        colors: [Color(0xFF8B5CF6), Color(0xFFEC4899)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (val.contains('#F97316')) {
      return const LinearGradient(
        colors: [Color(0xFFF97316), Color(0xFFEAB308)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (val.contains('#0EA5E9')) {
      return const LinearGradient(
        colors: [Color(0xFF0EA5E9), Color(0xFF22C55E)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    // Default indigo-blue
    return const LinearGradient(
      colors: [Color(0xFF4F46E5), Color(0xFF3B82F6)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    );
  }

  IconData _getStoryIcon(String id) {
    if (id.contains('link') || id.contains('account')) return Icons.account_balance_outlined;
    if (id.contains('privacy')) return Icons.shield_outlined;
    if (id.contains('otp') || id.contains('security')) return Icons.lock_outline;
    if (id.contains('qr')) return Icons.qr_code_scanner;
    if (id.contains('budget')) return Icons.savings_outlined;
    if (id.contains('rewards')) return Icons.card_giftcard_outlined;
    return Icons.auto_awesome_outlined;
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final gradient = _parseGradient(story.imageOrGradient);

    return InkWell(
      key: Key('story_${story.id}'),
      onTap: () {
        Navigator.of(context).push(
          PageRouteBuilder(
            opaque: false,
            pageBuilder: (ctx, anim, secAnim) {
              return StoryViewerScreen(
                initialStoryIndex: index,
                stories: allStories,
              );
            },
            transitionsBuilder: (ctx, anim, secAnim, child) {
              return FadeTransition(opacity: anim, child: child);
            },
          ),
        );
      },
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: 76,
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // Outer Ring Circle
            Container(
              width: 62,
              height: 62,
              padding: const EdgeInsets.all(2.5),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: !story.seen
                    ? const LinearGradient(
                        colors: [
                          Color(0xFF8B5CF6),
                          Color(0xFFEC4899),
                          Color(0xFFF59E0B),
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      )
                    : null,
                border: story.seen
                    ? Border.all(
                        color: isDark ? Colors.white24 : Colors.black12,
                        width: 1.5,
                      )
                    : null,
              ),
              child: Container(
                padding: const EdgeInsets.all(2),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isDark ? AppTokens.cardDark : Colors.white,
                ),
                child: Container(
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: gradient,
                  ),
                  child: Center(
                    child: Icon(
                      _getStoryIcon(story.id),
                      color: Colors.white,
                      size: 24,
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 6),

            // Caption / Title label
            Text(
              story.title,
              maxLines: 2,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 10.5,
                fontWeight: !story.seen ? FontWeight.w600 : FontWeight.w500,
                color: isDark
                    ? (!story.seen ? AppTokens.textPrimaryDark : AppTokens.textSecondaryDark)
                    : (!story.seen ? AppTokens.textPrimaryLight : AppTokens.textSecondaryLight),
                height: 1.15,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

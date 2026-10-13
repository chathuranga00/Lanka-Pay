import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Story card widget matching the target home layout.
/// Features rounded card (radius ~24), gradient/graphic background, caption,
/// and an outer primary blue ring indicator when [isUnseen] is true.
class StoryCard extends StatelessWidget {
  final String caption;
  final Widget? graphic;
  final Gradient? gradient;
  final bool isUnseen;
  final VoidCallback? onTap;
  final double width;
  final double height;

  const StoryCard({
    super.key = const Key('story_card'),
    required this.caption,
    this.graphic,
    this.gradient,
    this.isUnseen = false,
    this.onTap,
    this.width = 96.0,
    this.height = 110.0,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final defaultGradient = gradient ??
        (isDark
            ? const LinearGradient(
                colors: [Color(0xFF243B6A), Color(0xFF141F36)],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              )
            : const LinearGradient(
                colors: [Color(0xFF4C84F0), Color(0xFF386DD6)],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ));

    final innerRadius = isUnseen ? 20.0 : AppTokens.radiusStory;

    Widget cardContent = ClipRRect(
      borderRadius: BorderRadius.circular(innerRadius),
      child: Container(
        decoration: BoxDecoration(
          gradient: defaultGradient,
        ),
        child: Stack(
          children: [
            // Graphic / Illustration Slot
            if (graphic != null)
              Positioned.fill(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 30),
                  child: Center(child: graphic!),
                ),
              )
            else
              Positioned(
                top: 10,
                right: 10,
                child: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.18),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.auto_awesome,
                    size: 16,
                    color: Colors.white,
                  ),
                ),
              ),

            // Bottom gradient scrim for legibility
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                padding: const EdgeInsets.fromLTRB(8, 16, 8, 8),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      Colors.transparent,
                      Colors.black.withOpacity(0.75),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                  ),
                ),
                child: Text(
                  caption,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                    height: 1.2,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );

    Widget wrappedCard;
    if (isUnseen) {
      wrappedCard = Container(
        key: const Key('story_card_unseen_ring'),
        width: width,
        height: height,
        padding: const EdgeInsets.all(2.5),
        decoration: BoxDecoration(
          borderRadius: AppTokens.borderRadiusStory,
          border: Border.all(
            color: AppTokens.primaryBlue,
            width: 2.5,
          ),
        ),
        child: cardContent,
      );
    } else {
      wrappedCard = SizedBox(
        width: width,
        height: height,
        child: cardContent,
      );
    }

    if (onTap != null) {
      return Semantics(
        button: true,
        label: 'Story: $caption',
        child: GestureDetector(
          onTap: onTap,
          child: wrappedCard,
        ),
      );
    }

    return wrappedCard;
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_tokens.dart';
import '../domain/story.dart';
import 'stories_provider.dart';
import 'story_deep_link_validator.dart';

/// Full-screen Story Viewer with segmented progress bars, gesture navigation,
/// auto-advance, pause-on-hold, swipe-down dismiss, reduce-motion respect,
/// and safe whitelisted deep-link actions.
class StoryViewerScreen extends ConsumerStatefulWidget {
  final int initialStoryIndex;
  final List<Story> stories;

  const StoryViewerScreen({
    super.key,
    required this.initialStoryIndex,
    required this.stories,
  });

  @override
  ConsumerState<StoryViewerScreen> createState() => _StoryViewerScreenState();
}

class _StoryViewerScreenState extends ConsumerState<StoryViewerScreen>
    with SingleTickerProviderStateMixin {
  late int _storyIndex;
  late int _pageIndex;
  late AnimationController _progressController;
  bool _isPaused = false;
  double _dragOffsetY = 0.0;

  static const Duration _pageDuration = Duration(seconds: 5);

  Story get _currentStory => widget.stories[_storyIndex];
  List<StoryPage> get _currentPages => _currentStory.contentPages.isNotEmpty
      ? _currentStory.contentPages
      : [
          StoryPage(
            text: _currentStory.caption,
            actionLabel: 'Learn More',
            deepLink: '/explore',
          )
        ];
  StoryPage get _currentPage => _currentPages[_pageIndex];

  @override
  void initState() {
    super.initState();
    _storyIndex = widget.initialStoryIndex.clamp(0, widget.stories.length - 1);
    _pageIndex = 0;

    _progressController = AnimationController(
      vsync: this,
      duration: _pageDuration,
    )..addStatusListener(_onProgressStatus);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _markCurrentStorySeen();
      _startPageTimer();
    });
  }

  void _onProgressStatus(AnimationStatus status) {
    if (status == AnimationStatus.completed) {
      _advanceNextPage();
    }
  }

  void _startPageTimer() {
    final disableAnimations = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    _progressController.reset();

    // Respect reduce-motion: if reduce-motion is enabled, don't auto-advance with continuous timer
    if (!disableAnimations) {
      _progressController.forward();
    }
  }

  void _markCurrentStorySeen() {
    if (_storyIndex >= 0 && _storyIndex < widget.stories.length) {
      final story = widget.stories[_storyIndex];
      ref.read(storiesNotifierProvider.notifier).markStorySeen(story.id);
    }
  }

  void _advanceNextPage() {
    if (_pageIndex < _currentPages.length - 1) {
      setState(() {
        _pageIndex++;
      });
      _startPageTimer();
    } else {
      // Advance to next story
      if (_storyIndex < widget.stories.length - 1) {
        setState(() {
          _storyIndex++;
          _pageIndex = 0;
        });
        _markCurrentStorySeen();
        _startPageTimer();
      } else {
        // Last story, close viewer
        _closeViewer();
      }
    }
  }

  void _advancePreviousPage() {
    if (_pageIndex > 0) {
      setState(() {
        _pageIndex--;
      });
      _startPageTimer();
    } else {
      // Go to previous story
      if (_storyIndex > 0) {
        setState(() {
          _storyIndex--;
          _pageIndex = widget.stories[_storyIndex].contentPages.isNotEmpty
              ? widget.stories[_storyIndex].contentPages.length - 1
              : 0;
        });
        _markCurrentStorySeen();
        _startPageTimer();
      } else {
        // First page of first story, restart current page
        _startPageTimer();
      }
    }
  }

  void _pause() {
    if (!_isPaused) {
      _isPaused = true;
      _progressController.stop();
    }
  }

  void _resume() {
    if (_isPaused) {
      _isPaused = false;
      final disableAnimations = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
      if (!disableAnimations) {
        _progressController.forward();
      }
    }
  }

  void _closeViewer() {
    _progressController.stop();
    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  @override
  void dispose() {
    _progressController.dispose();
    super.dispose();
  }

  LinearGradient _parseGradient(String val) {
    if (val.contains('#10B981')) {
      return const LinearGradient(
        colors: [Color(0xFF065F46), Color(0xFF0F766E), Color(0xFF0F172A)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      );
    }
    if (val.contains('#EF4444')) {
      return const LinearGradient(
        colors: [Color(0xFF991B1B), Color(0xFFB45309), Color(0xFF0F172A)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      );
    }
    if (val.contains('#8B5CF6') || val.contains('#EC4899')) {
      return const LinearGradient(
        colors: [Color(0xFF5B21B6), Color(0xFF9D174D), Color(0xFF0F172A)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      );
    }
    if (val.contains('#F97316')) {
      return const LinearGradient(
        colors: [Color(0xFF9A3412), Color(0xFF854D0E), Color(0xFF0F172A)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      );
    }
    if (val.contains('#0EA5E9')) {
      return const LinearGradient(
        colors: [Color(0xFF0369A1), Color(0xFF15803D), Color(0xFF0F172A)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      );
    }
    return const LinearGradient(
      colors: [Color(0xFF312E81), Color(0xFF1E3A8A), Color(0xFF0F172A)],
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
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
    final story = _currentStory;
    final pages = _currentPages;
    final page = _currentPage;
    final disableAnimations = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    final gradient = _parseGradient(story.imageOrGradient);

    return Scaffold(
      key: const Key('story_viewer'),
      backgroundColor: Colors.black,
      body: GestureDetector(
        onLongPressStart: (_) => _pause(),
        onLongPressEnd: (_) => _resume(),
        onVerticalDragUpdate: (details) {
          if (details.primaryDelta != null && details.primaryDelta! > 0) {
            setState(() {
              _dragOffsetY += details.primaryDelta!;
            });
          }
        },
        onVerticalDragEnd: (details) {
          if (_dragOffsetY > 80 || (details.primaryVelocity ?? 0) > 300) {
            _closeViewer();
          } else {
            setState(() {
              _dragOffsetY = 0.0;
            });
          }
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          transform: Matrix4.translationValues(0, _dragOffsetY, 0),
          decoration: BoxDecoration(
            gradient: gradient,
          ),
          child: SafeArea(
            child: Stack(
              children: [
                // 1. Middle Tap Areas for Navigation (Left = previous, Right = next)
                Positioned.fill(
                  child: Row(
                    children: [
                      // Left tap zone (rewind)
                      Expanded(
                        flex: 1,
                        child: GestureDetector(
                          behavior: HitTestBehavior.translucent,
                          onTap: _advancePreviousPage,
                        ),
                      ),
                      // Right tap zone (forward)
                      Expanded(
                        flex: 2,
                        child: GestureDetector(
                          behavior: HitTestBehavior.translucent,
                          onTap: _advanceNextPage,
                        ),
                      ),
                    ],
                  ),
                ),

                // 2. Top Section: Segmented Progress Bars & Story Header
                Positioned(
                  top: 8,
                  left: 16,
                  right: 16,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Segmented Progress Bars
                      Row(
                        children: List.generate(pages.length, (idx) {
                          return Expanded(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 2.5),
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(2),
                                child: Container(
                                  height: 3,
                                  color: Colors.white.withOpacity(0.3),
                                  child: AnimatedBuilder(
                                    animation: _progressController,
                                    builder: (ctx, child) {
                                      double progress;
                                      if (idx < _pageIndex) {
                                        progress = 1.0;
                                      } else if (idx == _pageIndex) {
                                        progress = disableAnimations
                                            ? 1.0
                                            : _progressController.value;
                                      } else {
                                        progress = 0.0;
                                      }
                                      return FractionallySizedBox(
                                        alignment: Alignment.centerLeft,
                                        widthFactor: progress.clamp(0.0, 1.0),
                                        child: Container(
                                          color: Colors.white,
                                        ),
                                      );
                                    },
                                  ),
                                ),
                              ),
                            ),
                          );
                        }),
                      ),
                      const SizedBox(height: 12),

                      // Story Header Info (Icon, Title, Caption, Close Button)
                      Row(
                        children: [
                          Container(
                            width: 36,
                            height: 36,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: Colors.white.withOpacity(0.2),
                            ),
                            child: Center(
                              child: Icon(
                                _getStoryIcon(story.id),
                                color: Colors.white,
                                size: 20,
                              ),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  story.title,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w700,
                                    letterSpacing: 0.1,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                Text(
                                  story.caption,
                                  style: TextStyle(
                                    color: Colors.white.withOpacity(0.8),
                                    fontSize: 11,
                                    fontWeight: FontWeight.w400,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            key: const Key('story_close'),
                            icon: const Icon(Icons.close, color: Colors.white, size: 24),
                            onPressed: _closeViewer,
                            tooltip: 'Close story',
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                // 3. Central Content Display (Text + Graphic)
                Positioned(
                  top: 100,
                  bottom: 120,
                  left: 28,
                  right: 28,
                  child: IgnorePointer(
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: Colors.white.withOpacity(0.12),
                              border: Border.all(
                                color: Colors.white.withOpacity(0.2),
                                width: 2,
                              ),
                            ),
                            child: Center(
                              child: Icon(
                                _getStoryIcon(story.id),
                                color: Colors.white,
                                size: 44,
                              ),
                            ),
                          ),
                          const SizedBox(height: 32),
                          Text(
                            page.text,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 22,
                              fontWeight: FontWeight.w700,
                              height: 1.45,
                              letterSpacing: 0.1,
                              shadows: [
                                Shadow(
                                  color: Colors.black54,
                                  offset: Offset(0, 2),
                                  blurRadius: 10,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),

                // 4. Bottom Action Button (using whitelisted deep-links)
                if (page.actionLabel != null && page.actionLabel!.isNotEmpty)
                  Positioned(
                    bottom: 24,
                    left: 20,
                    right: 20,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: double.infinity,
                          height: 52,
                          child: ElevatedButton(
                            key: const Key('story_action'),
                            onPressed: () {
                              ref
                                  .read(storiesNotifierProvider.notifier)
                                  .recordActionClick(story.id);

                              _closeViewer();

                              if (page.deepLink != null) {
                                StoryDeepLinkValidator.navigate(
                                  context,
                                  page.deepLink,
                                );
                              }
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.white,
                              foregroundColor: AppTokens.textPrimaryDark,
                              elevation: 4,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(20),
                              ),
                            ),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(
                                  page.actionLabel!,
                                  style: const TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.w800,
                                    color: Color(0xFF0F172A),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                const Icon(
                                  Icons.arrow_forward_rounded,
                                  size: 18,
                                  color: Color(0xFF0F172A),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

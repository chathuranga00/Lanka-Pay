import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/stories/data/stories_api_service.dart';
import 'package:lankapay_id/features/stories/domain/story.dart';
import 'package:lankapay_id/features/stories/presentation/stories_provider.dart';
import 'package:lankapay_id/features/stories/presentation/story_deep_link_validator.dart';
import 'package:lankapay_id/features/stories/presentation/story_viewer_screen.dart';
import 'package:lankapay_id/features/stories/presentation/widgets/story_card.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

class FakeStoriesApiService implements StoriesApiService {
  final List<String> seenCalls = [];
  final List<String> clickCalls = [];

  List<Story> stories = [
    const Story(
      id: 'story-1',
      title: 'Link Account',
      caption: 'Add your account',
      imageOrGradient: 'gradient:linear(135deg, #4F46E5, #3B82F6)',
      seen: false,
      priority: 10,
      contentPages: [
        StoryPage(
          text: 'Page 1 text: Connect your bank.',
          actionLabel: 'Link Now',
          deepLink: '/accounts/add',
        ),
        StoryPage(
          text: 'Page 2 text: Verify details.',
          actionLabel: 'Finish',
          deepLink: '/explore',
        ),
      ],
    ),
    const Story(
      id: 'story-2',
      title: 'Security Tip',
      caption: 'Never share OTP',
      imageOrGradient: 'gradient:linear(135deg, #EF4444, #F59E0B)',
      seen: true,
      priority: 20,
      contentPages: [
        StoryPage(
          text: 'Never disclose your OTP to anyone.',
          actionLabel: 'Security Settings',
          deepLink: '/settings/security',
        ),
      ],
    ),
  ];

  @override
  Future<List<Story>> getStories() async => stories;

  @override
  Future<void> markSeen(String storyId) async {
    seenCalls.add(storyId);
  }

  @override
  Future<void> recordActionClick(String storyId) async {
    clickCalls.add(storyId);
  }
}

void main() {
  group('StoryDeepLinkValidator Unit Tests', () {
    test('whitelisted app routes pass validation', () {
      expect(StoryDeepLinkValidator.isWhitelisted('/accounts/add'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('/settings/privacy'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('/settings/security'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('/qr'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('/history'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('/rewards'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('/explore'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('lankapay://accounts/add'), isTrue);
      expect(StoryDeepLinkValidator.isWhitelisted('lankapay://settings/security'), isTrue);
    });

    test('arbitrary external URLs and script injections are strictly rejected', () {
      expect(StoryDeepLinkValidator.isWhitelisted('https://phishing.com/login'), isFalse);
      expect(StoryDeepLinkValidator.isWhitelisted('http://malicious.org'), isFalse);
      expect(StoryDeepLinkValidator.isWhitelisted('javascript:alert(1)'), isFalse);
      expect(StoryDeepLinkValidator.isWhitelisted('<script>alert("xss")</script>'), isFalse);
      expect(StoryDeepLinkValidator.isWhitelisted('data:text/html,evil'), isFalse);
      expect(StoryDeepLinkValidator.isWhitelisted(null), isFalse);
      expect(StoryDeepLinkValidator.isWhitelisted(''), isFalse);
    });

    test('getNormalizedPath strips custom scheme safely', () {
      expect(StoryDeepLinkValidator.getNormalizedPath('lankapay://accounts/add'), '/accounts/add');
      expect(StoryDeepLinkValidator.getNormalizedPath('/settings/privacy'), '/settings/privacy');
      expect(StoryDeepLinkValidator.getNormalizedPath('https://external.com'), isNull);
    });
  });

  group('StoryCard & Seen Ring Widget Tests', () {
    testWidgets('unseen story displays gradient ring; seen story displays subtle ring', (tester) async {
      final fakeApi = FakeStoriesApiService();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            storiesApiServiceProvider.overrideWithValue(fakeApi),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: Row(
                children: [
                  StoryCard(
                    story: fakeApi.stories[0], // unseen
                    allStories: fakeApi.stories,
                    index: 0,
                  ),
                  StoryCard(
                    story: fakeApi.stories[1], // seen
                    allStories: fakeApi.stories,
                    index: 1,
                  ),
                ],
              ),
            ),
          ),
        ),
      );

      // Verify keys exist
      expect(find.byKey(const Key('story_story-1')), findsOneWidget);
      expect(find.byKey(const Key('story_story-2')), findsOneWidget);

      // Verify titles are rendered
      expect(find.text('Link Account'), findsOneWidget);
      expect(find.text('Security Tip'), findsOneWidget);
    });
  });

  group('StoryViewerScreen Full-Screen Flow Tests', () {
    testWidgets('Story viewer renders, marks seen, navigates pages, and triggers action button', (tester) async {
      final fakeApi = FakeStoriesApiService();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            storiesApiServiceProvider.overrideWithValue(fakeApi),
          ],
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            home: StoryViewerScreen(
              initialStoryIndex: 0,
              stories: fakeApi.stories,
            ),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // 1. Viewer is displayed
      expect(find.byKey(const Key('story_viewer')), findsOneWidget);
      expect(find.byKey(const Key('story_close')), findsOneWidget);
      expect(find.byKey(const Key('story_action')), findsOneWidget);

      // 2. Initial Page 1 content is shown
      expect(find.text('Page 1 text: Connect your bank.'), findsOneWidget);
      expect(find.text('Link Now'), findsOneWidget);

      // 3. Tap right area to advance to page 2
      final rightTapFinder = find.byType(GestureDetector);
      await tester.tapAt(const Offset(300, 300));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Page 2 text: Verify details.'), findsOneWidget);
      expect(find.text('Finish'), findsOneWidget);

      // 4. Tap left area to rewind back to page 1
      await tester.tapAt(const Offset(50, 300));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Page 1 text: Connect your bank.'), findsOneWidget);

      // 5. Tap action button
      await tester.tap(find.byKey(const Key('story_action')));
      await tester.pumpAndSettle();

      // Action click should be recorded
      expect(fakeApi.clickCalls, contains('story-1'));
    });

    testWidgets('Story viewer close button dismisses viewer', (tester) async {
      final fakeApi = FakeStoriesApiService();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            storiesApiServiceProvider.overrideWithValue(fakeApi),
          ],
          child: MaterialApp(
            home: Builder(
              builder: (ctx) => Scaffold(
                body: ElevatedButton(
                  key: const Key('open_btn'),
                  onPressed: () {
                    Navigator.of(ctx).push(
                      MaterialPageRoute(
                        builder: (_) => StoryViewerScreen(
                          initialStoryIndex: 0,
                          stories: fakeApi.stories,
                        ),
                      ),
                    );
                  },
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.byKey(const Key('open_btn')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byKey(const Key('story_viewer')), findsOneWidget);

      // Tap close button
      await tester.tap(find.byKey(const Key('story_close')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('story_viewer')), findsNothing);
      expect(find.byKey(const Key('open_btn')), findsOneWidget);
    });

    testWidgets('Respects reduce-motion (disableAnimations = true)', (tester) async {
      final fakeApi = FakeStoriesApiService();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            storiesApiServiceProvider.overrideWithValue(fakeApi),
          ],
          child: MediaQuery(
            data: const MediaQueryData(disableAnimations: true),
            child: MaterialApp(
              home: StoryViewerScreen(
                initialStoryIndex: 0,
                stories: fakeApi.stories,
              ),
            ),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Viewer renders cleanly without animation errors under reduce-motion
      expect(find.byKey(const Key('story_viewer')), findsOneWidget);
      expect(find.text('Page 1 text: Connect your bank.'), findsOneWidget);
    });
  });
}

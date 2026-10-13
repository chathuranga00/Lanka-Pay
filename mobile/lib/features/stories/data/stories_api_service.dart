import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/story.dart';

final storiesApiServiceProvider = Provider<StoriesApiService>((ref) {
  final dio = ref.watch(dioProvider);
  return StoriesApiService(dio);
});

class StoriesApiService {
  final Dio _dio;

  StoriesApiService(this._dio);

  Future<List<Story>> getStories() async {
    try {
      final response = await _dio.get('/api/v1/stories');
      if (response.data is List) {
        return (response.data as List)
            .map((item) => Story.fromJson(item as Map<String, dynamic>))
            .toList();
      }
      return [];
    } on DioException catch (_) {
      // Return safe fallback educational stories if API is unreachable in offline/sandbox demo
      return _fallbackStories;
    } catch (_) {
      return _fallbackStories;
    }
  }

  Future<void> markSeen(String storyId) async {
    try {
      await _dio.post('/api/v1/stories/$storyId/seen');
    } catch (_) {
      // Best-effort analytics call
    }
  }

  Future<void> recordActionClick(String storyId) async {
    try {
      await _dio.post('/api/v1/stories/$storyId/action-click');
    } catch (_) {
      // Best-effort analytics call
    }
  }

  static const List<Story> _fallbackStories = [
    Story(
      id: 'story-link-account',
      title: 'Link your first account',
      caption: 'Get started with LankaPay',
      imageOrGradient: 'gradient:linear(135deg, #4F46E5, #3B82F6)',
      priority: 10,
      contentPages: [
        StoryPage(
          text: 'Connect your bank account to enable instant, zero-fee payments across Sri Lanka.',
          imageRef: 'asset:account_link_guide',
          actionLabel: 'Link Account',
          deepLink: '/accounts/add',
        ),
      ],
    ),
    Story(
      id: 'story-privacy-lookup',
      title: 'How lookup protects your privacy',
      caption: 'Your data is secure',
      imageOrGradient: 'gradient:linear(135deg, #10B981, #06B6D4)',
      priority: 20,
      contentPages: [
        StoryPage(
          text: 'We never expose your full bank account numbers to senders. Lookups use encrypted tokens only.',
          imageRef: 'asset:privacy_shield',
          actionLabel: 'Privacy Settings',
          deepLink: '/settings/privacy',
        ),
      ],
    ),
    Story(
      id: 'story-security-otp',
      title: 'Security tip: never share your OTP',
      caption: 'Stay safe from scams',
      imageOrGradient: 'gradient:linear(135deg, #EF4444, #F59E0B)',
      priority: 30,
      contentPages: [
        StoryPage(
          text: 'LankaPay staff or bank agents will never ask for your 6-digit OTP or login PIN over phone or SMS.',
          imageRef: 'asset:security_lock',
          actionLabel: 'Security Center',
          deepLink: '/settings/security',
        ),
      ],
    ),
    Story(
      id: 'story-qr-payments',
      title: 'Try QR payments',
      caption: 'LankaQR nationwide standard',
      imageOrGradient: 'gradient:linear(135deg, #8B5CF6, #EC4899)',
      priority: 40,
      contentPages: [
        StoryPage(
          text: 'Scan any LankaQR at supermarkets, restaurants, and fuel stations for contactless settlement.',
          imageRef: 'asset:qr_scan_demo',
          actionLabel: 'Open QR Scanner',
          deepLink: '/qr',
        ),
      ],
    ),
    Story(
      id: 'story-monthly-budget',
      title: 'Set a monthly budget',
      caption: 'Smart spending insights',
      imageOrGradient: 'gradient:linear(135deg, #F97316, #EAB308)',
      priority: 50,
      contentPages: [
        StoryPage(
          text: 'Monitor categorized daily expenses and receive real-time notifications before exceeding your monthly cap.',
          imageRef: 'asset:budget_chart',
          actionLabel: 'View Activity',
          deepLink: '/history',
        ),
      ],
    ),
    Story(
      id: 'story-simulated-rewards',
      title: 'Rewards are simulated',
      caption: 'Sandbox test environment',
      imageOrGradient: 'gradient:linear(135deg, #0EA5E9, #22C55E)',
      priority: 60,
      contentPages: [
        StoryPage(
          text: 'All loyalty points and balances are part of the CBSL CEFTS testing sandbox with simulated test money.',
          imageRef: 'asset:rewards_badge',
          actionLabel: 'Explore Rewards',
          deepLink: '/rewards',
        ),
      ],
    ),
  ];
}

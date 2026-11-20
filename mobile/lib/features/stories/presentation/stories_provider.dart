import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/stories_api_service.dart';
import '../domain/story.dart';

final storiesNotifierProvider =
    AsyncNotifierProvider<StoriesNotifier, List<Story>>(() {
  return StoriesNotifier();
});

class StoriesNotifier extends AsyncNotifier<List<Story>> {
  @override
  Future<List<Story>> build() async {
    final service = ref.watch(storiesApiServiceProvider);
    return await service.getStories();
  }

  Future<void> markStorySeen(String storyId) async {
    final current = state.value;
    if (current == null) return;

    // Optimistically update local state so the ring becomes seen immediately
    state = AsyncData(
      current.map((s) {
        if (s.id == storyId) {
          return s.copyWith(seen: true);
        }
        return s;
      }).toList(),
    );

    // Persist to backend asynchronously
    final service = ref.read(storiesApiServiceProvider);
    await service.markSeen(storyId);
  }

  Future<void> recordActionClick(String storyId) async {
    final current = state.value;
    if (current == null) return;

    state = AsyncData(
      current.map((s) {
        if (s.id == storyId) {
          return s.copyWith(actionClickCount: s.actionClickCount + 1);
        }
        return s;
      }).toList(),
    );

    final service = ref.read(storiesApiServiceProvider);
    await service.recordActionClick(storyId);
  }
}

import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Feature flag keys for the Super App navigation and module system.
class FeatureFlagKeys {
  static const String home = 'home';
  static const String pay = 'pay';
  static const String city = 'city';
  static const String chat = 'support'; // or chat
  static const String explore = 'explore';
  static const String cards = 'cards';
  static const String bills = 'bills';
}

/// Notifier managing dynamic feature flags in Riverpod.
class FeatureFlagsNotifier extends Notifier<Map<String, bool>> {
  final Map<String, bool>? _initial;

  FeatureFlagsNotifier([this._initial]);

  @override
  Map<String, bool> build() {
    return _initial ??
        {
          FeatureFlagKeys.home: true,
          FeatureFlagKeys.pay: true,
          FeatureFlagKeys.city: true,
          FeatureFlagKeys.chat: true,
          FeatureFlagKeys.explore: true,
          FeatureFlagKeys.cards: true,
          FeatureFlagKeys.bills: true,
        };
  }

  bool isEnabled(String key) {
    return state[key] ?? false;
  }

  void setFlag(String key, bool enabled) {
    state = {...state, key: enabled};
  }

  void toggleFlag(String key) {
    final current = state[key] ?? false;
    setFlag(key, !current);
  }

  void updateAll(Map<String, bool> newFlags) {
    state = {...state, ...newFlags};
  }
}

/// Global provider for feature flags.
final featureFlagsProvider =
    NotifierProvider<FeatureFlagsNotifier, Map<String, bool>>(() {
  return FeatureFlagsNotifier();
});

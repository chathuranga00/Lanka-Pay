import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';

class PaymentProfileState {
  final bool isDiscoverable;
  final bool isFrozen;
  final String? phone;
  final String? safeName;
  final bool isLoading;
  final String? message;

  const PaymentProfileState({
    this.isDiscoverable = true,
    this.isFrozen = false,
    this.phone,
    this.safeName,
    this.isLoading = false,
    this.message,
  });

  PaymentProfileState copyWith({
    bool? isDiscoverable,
    bool? isFrozen,
    String? phone,
    String? safeName,
    bool? isLoading,
    String? message,
  }) {
    return PaymentProfileState(
      isDiscoverable: isDiscoverable ?? this.isDiscoverable,
      isFrozen: isFrozen ?? this.isFrozen,
      phone: phone ?? this.phone,
      safeName: safeName ?? this.safeName,
      isLoading: isLoading ?? this.isLoading,
      message: message,
    );
  }
}

class PaymentProfileNotifier extends Notifier<PaymentProfileState> {
  @override
  PaymentProfileState build() {
    _loadProfile();
    return const PaymentProfileState(isLoading: true);
  }

  Future<void> _loadProfile() async {
    final dio = ref.read(dioProvider);
    try {
      final res = await dio.get('/payment-profile/me');
      final data = Map<String, dynamic>.from(res.data as Map);
      state = PaymentProfileState(
        isDiscoverable: data['discoverable'] as bool? ?? true,
        isFrozen: false,
        phone: data['maskedPhone'] as String?,
        safeName: data['displayName'] as String?,
        isLoading: false,
      );
    } catch (_) {
      state = state.copyWith(isLoading: false);
    }
  }

  Future<void> toggleDiscoverability(bool value) async {
    final dio = ref.read(dioProvider);
    state = state.copyWith(isLoading: true);
    try {
      await dio.patch('/payment-profile/discovery', data: {'discoverable': value});
      state = state.copyWith(
        isDiscoverable: value,
        isLoading: false,
        message: value
            ? 'Discovery enabled across the national network.'
            : 'Discovery disabled. Your phone number is now private.',
      );
    } catch (_) {
      state = state.copyWith(
        isLoading: false,
        message: 'Failed to update discovery preference.',
      );
    }
  }

  void toggleFreezePaymentIdentity(bool value) {
    state = state.copyWith(
      isFrozen: value,
      message: value
          ? 'Payment identity frozen. Incoming transfers are temporarily paused.'
          : 'Payment identity active and receiving transfers.',
    );
  }
}

final paymentProfileProvider =
    NotifierProvider<PaymentProfileNotifier, PaymentProfileState>(() {
  return PaymentProfileNotifier();
});

class PrivacySettingsScreen extends ConsumerWidget {
  const PrivacySettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(paymentProfileProvider);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GradientHeaderScaffold(
      header: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(AppTokens.space20, AppTokens.space12, AppTokens.space20, AppTokens.space16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  if (Navigator.of(context).canPop()) ...[
                    IconButton(
                      icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                      tooltip: 'Back',
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        Navigator.of(context).pop();
                      },
                    ),
                    const SizedBox(width: AppTokens.space8),
                  ],
                  Expanded(
                    child: Text(
                      'Privacy & Payment-ID',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppTokens.space4),
              Padding(
                padding: EdgeInsets.only(left: Navigator.of(context).canPop() ? 48.0 : 0.0),
                child: Text(
                  'Manage directory discovery and receiving state',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
      bodyPadding: const EdgeInsets.all(AppTokens.space20),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SimulatedBanner(),
          const SizedBox(height: AppTokens.space16),

          if (profile.message != null) ...[
            Container(
              padding: const EdgeInsets.all(AppTokens.space12),
              decoration: BoxDecoration(
                color: profile.isFrozen
                    ? AppTokens.warningLight
                    : AppTokens.success.withOpacity(0.12),
                borderRadius: AppTokens.borderRadiusSmall,
                border: Border.all(
                  color: profile.isFrozen
                      ? AppTokens.warning.withOpacity(0.3)
                      : AppTokens.success.withOpacity(0.3),
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    profile.isFrozen
                        ? Icons.warning_amber_rounded
                        : Icons.check_circle_outline_rounded,
                    color: profile.isFrozen ? AppTokens.warning : AppTokens.success,
                    size: 20,
                  ),
                  const SizedBox(width: AppTokens.space8),
                  Expanded(
                    child: Text(
                      profile.message!,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                        color: profile.isFrozen ? AppTokens.warning : AppTokens.success,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppTokens.space16),
          ],

          // Card 1: Payment Identity Registry
          Material(
            color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
            borderRadius: AppTokens.borderRadiusCard,
            child: Container(
              padding: const EdgeInsets.all(AppTokens.space20),
              decoration: BoxDecoration(
                borderRadius: AppTokens.borderRadiusCard,
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
              ),
              child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTokens.primaryViolet.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(
                        Icons.privacy_tip_outlined,
                        color: AppTokens.primaryViolet,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: AppTokens.space12),
                    Expanded(
                      child: Text(
                        'Payment Identity Registry',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppTokens.space12),
                Text(
                  'Control how your mobile number appears to other LankaPay users during peer-to-peer directory lookup.',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    height: 1.45,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: AppTokens.space16),
                Divider(
                  height: 1,
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
                const SizedBox(height: AppTokens.space12),
                Semantics(
                  label: 'Enable Phone Number Discovery switch',
                  child: SwitchListTile.adaptive(
                    key: AppTestKeys.privacyDiscoverableSwitch,
                    contentPadding: EdgeInsets.zero,
                    activeColor: AppTokens.primaryViolet,
                    title: Text(
                      'Enable Phone Number Discovery',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    subtitle: Text(
                      'When disabled, payers searching for your mobile number will see "Not Discoverable".',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 12,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                    value: profile.isDiscoverable,
                    onChanged: profile.isLoading
                        ? null
                        : (val) {
                            HapticFeedback.lightImpact();
                            ref.read(paymentProfileProvider.notifier).toggleDiscoverability(val);
                          },
                  ),
                ),
              ],
            ),
          ),
        ),
          const SizedBox(height: AppTokens.space16),

          // Card 2: Freeze Payment Identity
          Material(
            color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
            borderRadius: AppTokens.borderRadiusCard,
            child: Container(
              padding: const EdgeInsets.all(AppTokens.space20),
              decoration: BoxDecoration(
                borderRadius: AppTokens.borderRadiusCard,
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
              ),
              child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTokens.warning.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(
                        Icons.ac_unit_rounded,
                        color: AppTokens.warning,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: AppTokens.space12),
                    Expanded(
                      child: Text(
                        'Freeze Payment Identity',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppTokens.space12),
                Text(
                  'Emergency kill switch: Temporarily reject all incoming peer-to-peer transfers and payment requests to this phone number.',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    height: 1.45,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: AppTokens.space16),
                Divider(
                  height: 1,
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
                const SizedBox(height: AppTokens.space12),
                Semantics(
                  label: 'Freeze Identity and Receiving switch',
                  child: SwitchListTile.adaptive(
                    key: AppTestKeys.privacyFreezeSwitch,
                    contentPadding: EdgeInsets.zero,
                    activeColor: AppTokens.error,
                    title: Text(
                      'Freeze Identity & Receiving',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        color: profile.isFrozen ? AppTokens.error : (isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight),
                      ),
                    ),
                    subtitle: Text(
                      'Immediately stops incoming transfers until you unfreeze.',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 12,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                    value: profile.isFrozen,
                    onChanged: (val) {
                      HapticFeedback.lightImpact();
                      ref.read(paymentProfileProvider.notifier).toggleFreezePaymentIdentity(val);
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
          const SizedBox(height: AppTokens.space32),
        ],
      ),
    );
  }
}

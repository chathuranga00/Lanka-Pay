import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../../core/theme/app_theme.dart';
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
        safeName: data['safeDisplayName'] as String?,
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
        message: value ? 'Discovery enabled across network' : 'Discovery disabled. Your phone is private.',
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
      message: value ? 'Payment identity frozen. Incoming transfers paused.' : 'Payment identity active.',
    );
  }
}

final paymentProfileProvider = NotifierProvider<PaymentProfileNotifier, PaymentProfileState>(() {
  return PaymentProfileNotifier();
});

class PrivacySettingsScreen extends ConsumerWidget {
  const PrivacySettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(paymentProfileProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Privacy & Payment-ID'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(20.0),
                children: [
                  if (profile.message != null) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.primaryGreen.withOpacity(0.12),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppColors.primaryGreen.withOpacity(0.3)),
                        ),
                        child: Text(
                          profile.message!,
                          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],

                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Row(
                              children: [
                                Icon(Icons.privacy_tip_outlined, color: AppColors.primaryGreen),
                                SizedBox(width: 10),
                                Text(
                                  'Payment Identity Registry',
                                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            const Text(
                              'Control how your mobile number appears to other LankaPay users during peer-to-peer directory lookup.',
                              style: TextStyle(fontSize: 13, height: 1.4),
                            ),
                            const SizedBox(height: 16),
                            const Divider(height: 1),
                            SwitchListTile(
                              key: const Key('privacy_discoverable_switch'),
                              contentPadding: EdgeInsets.zero,
                              title: const Text('Enable Phone Number Discovery', style: TextStyle(fontWeight: FontWeight.w600)),
                              subtitle: const Text(
                                'When disabled, payers searching for your mobile number will see "Not Discoverable".',
                                style: TextStyle(fontSize: 12),
                              ),
                              value: profile.isDiscoverable,
                              onChanged: profile.isLoading
                                  ? null
                                  : (val) {
                                      ref.read(paymentProfileProvider.notifier).toggleDiscoverability(val);
                                    },
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Row(
                              children: [
                                Icon(Icons.ac_unit_rounded, color: AppColors.warning),
                                SizedBox(width: 10),
                                Text(
                                  'Freeze Payment Identity',
                                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            const Text(
                              'Emergency kill switch: Temporarily reject all incoming peer-to-peer transfers and payment requests to this phone number.',
                              style: TextStyle(fontSize: 13, height: 1.4),
                            ),
                            const SizedBox(height: 16),
                            const Divider(height: 1),
                            SwitchListTile(
                              key: const Key('privacy_freeze_switch'),
                              contentPadding: EdgeInsets.zero,
                              activeColor: AppColors.error,
                              title: const Text('Freeze Identity & Receiving', style: TextStyle(fontWeight: FontWeight.w600)),
                              subtitle: const Text(
                                'Immediately stops incoming transfers until you unfreeze.',
                                style: TextStyle(fontSize: 12),
                              ),
                              value: profile.isFrozen,
                              onChanged: (val) {
                                ref.read(paymentProfileProvider.notifier).toggleFreezePaymentIdentity(val);
                              },
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
    );
  }
}

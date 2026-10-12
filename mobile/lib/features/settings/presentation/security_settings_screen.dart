import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';

class DeviceInfo {
  final String id;
  final String deviceName;
  final String platform;
  final String lastActive;
  final bool isCurrent;

  const DeviceInfo({
    required this.id,
    required this.deviceName,
    required this.platform,
    required this.lastActive,
    required this.isCurrent,
  });
}

class SecuritySettingsScreen extends StatefulWidget {
  const SecuritySettingsScreen({super.key});

  @override
  State<SecuritySettingsScreen> createState() => _SecuritySettingsScreenState();
}

class _SecuritySettingsScreenState extends State<SecuritySettingsScreen> {
  bool _biometricsEnabled = true;
  bool _stepUpPinOnTransfers = true;

  final List<DeviceInfo> _devices = [
    const DeviceInfo(
      id: 'dev_1',
      deviceName: 'Pixel 8 Pro (Simulated)',
      platform: 'Android 14 • LankaPay Mobile',
      lastActive: 'Active now',
      isCurrent: true,
    ),
    const DeviceInfo(
      id: 'dev_2',
      deviceName: 'iPhone 15 Pro',
      platform: 'iOS 17.5 • LankaPay Mobile',
      lastActive: '3 days ago',
      isCurrent: false,
    ),
  ];

  void _revokeDevice(DeviceInfo dev) {
    HapticFeedback.lightImpact();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(
          'Revoke Device Access?',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontWeight: FontWeight.w700,
          ),
        ),
        content: Text(
          'Are you sure you want to log out ${dev.deviceName}? Refresh tokens on this device will be permanently revoked.',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 13,
            height: 1.4,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTokens.error,
              foregroundColor: Colors.white,
            ),
            onPressed: () {
              Navigator.pop(ctx);
              setState(() {
                _devices.removeWhere((d) => d.id == dev.id);
              });
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Revoked session for ${dev.deviceName}'),
                  backgroundColor: AppTokens.cardDark,
                ),
              );
            },
            child: const Text('Revoke'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
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
                      'Security & Devices',
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
                  'Biometrics, PIN policies, and trusted devices',
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

          // Authentication & Protection Card
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
                        Icons.shield_outlined,
                        color: AppTokens.primaryViolet,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: AppTokens.space12),
                    Expanded(
                      child: Text(
                        'Authentication & Protection',
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
                const SizedBox(height: AppTokens.space16),
                Semantics(
                  label: 'Biometric Authentication toggle',
                  child: SwitchListTile.adaptive(
                    key: AppTestKeys.securityBiometricsSwitch,
                    contentPadding: EdgeInsets.zero,
                    activeColor: AppTokens.primaryViolet,
                    title: Text(
                      'Biometric Authentication',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    subtitle: Text(
                      'Use Fingerprint or Face Unlock to sign in rapidly.',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 12,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                    value: _biometricsEnabled,
                    onChanged: (val) {
                      HapticFeedback.lightImpact();
                      setState(() {
                        _biometricsEnabled = val;
                      });
                    },
                  ),
                ),
                Divider(
                  height: 1,
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
                const SizedBox(height: AppTokens.space8),
                Semantics(
                  label: 'Require PIN on Transfers toggle',
                  child: SwitchListTile.adaptive(
                    key: AppTestKeys.securityStepUpSwitch,
                    contentPadding: EdgeInsets.zero,
                    activeColor: AppTokens.primaryViolet,
                    title: Text(
                      'Require 6-Digit PIN on Transfers',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    subtitle: Text(
                      'Step-up authentication required for each outbound payment.',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 12,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                    value: _stepUpPinOnTransfers,
                    onChanged: (val) {
                      HapticFeedback.lightImpact();
                      setState(() {
                        _stepUpPinOnTransfers = val;
                      });
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
          const SizedBox(height: AppTokens.space20),

          // Trusted Device Sessions Card
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
                        Icons.devices_rounded,
                        color: AppTokens.primaryViolet,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: AppTokens.space12),
                    Expanded(
                      child: Text(
                        'Trusted Device Sessions',
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
                const SizedBox(height: AppTokens.space8),
                Text(
                  'Manage devices currently authorized to access your LankaPay account.',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: AppTokens.space16),
                ..._devices.map((dev) {
                  return Column(
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: CircleAvatar(
                          radius: 20,
                          backgroundColor: dev.isCurrent
                              ? AppTokens.primaryViolet.withOpacity(0.12)
                              : (isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight),
                          child: Icon(
                            Icons.phone_android_rounded,
                            size: 20,
                            color: dev.isCurrent
                                ? AppTokens.primaryViolet
                                : (isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight),
                          ),
                        ),
                        title: Row(
                          children: [
                            Flexible(
                              child: Text(
                                dev.deviceName,
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontWeight: FontWeight.w700,
                                  fontSize: 14,
                                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            if (dev.isCurrent) ...[
                              const SizedBox(width: AppTokens.space8),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppTokens.primaryViolet,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: const Text(
                                  'THIS DEVICE',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 9,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                        subtitle: Text(
                          '${dev.platform}\n${dev.lastActive}',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 11,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                        ),
                        trailing: dev.isCurrent
                            ? null
                            : Semantics(
                                label: 'Revoke device ${dev.deviceName}',
                                child: IconButton(
                                  key: Key('revoke_device_btn_${dev.id}'),
                                  icon: const Icon(Icons.logout_rounded, color: AppTokens.error, size: 20),
                                  onPressed: () => _revokeDevice(dev),
                                  tooltip: 'Revoke',
                                ),
                              ),
                      ),
                      Divider(
                        height: 1,
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                      ),
                    ],
                  );
                }),
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

import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';
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
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Revoke Device Access?'),
        content: Text('Are you sure you want to log out ${dev.deviceName}? Refresh tokens on this device will be revoked.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.error),
            onPressed: () {
              Navigator.pop(ctx);
              setState(() {
                _devices.removeWhere((d) => d.id == dev.id);
              });
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Revoked session for ${dev.deviceName}')),
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Security & Devices'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(20.0),
                children: [
                  // Biometric & Transfer Controls
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.shield_outlined, color: AppColors.primaryGreen),
                              SizedBox(width: 10),
                              Text(
                                'Authentication & Protection',
                                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          SwitchListTile(
                            key: const Key('security_biometrics_switch'),
                            contentPadding: EdgeInsets.zero,
                            title: const Text('Biometric Authentication', style: TextStyle(fontWeight: FontWeight.w600)),
                            subtitle: const Text('Use Fingerprint or Face Unlock to sign in rapidly.', style: TextStyle(fontSize: 12)),
                            value: _biometricsEnabled,
                            onChanged: (val) {
                              setState(() {
                                _biometricsEnabled = val;
                              });
                            },
                          ),
                          const Divider(height: 1),
                          SwitchListTile(
                            key: const Key('security_step_up_switch'),
                            contentPadding: EdgeInsets.zero,
                            title: const Text('Require 6-Digit PIN on Transfers', style: TextStyle(fontWeight: FontWeight.w600)),
                            subtitle: const Text('Step-up authentication required for each outbound payment.', style: TextStyle(fontSize: 12)),
                            value: _stepUpPinOnTransfers,
                            onChanged: (val) {
                              setState(() {
                                _stepUpPinOnTransfers = val;
                              });
                            },
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Active Device Sessions
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.devices_rounded, color: AppColors.primaryGreen),
                              SizedBox(width: 10),
                              Text(
                                'Trusted Device Sessions',
                                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'Manage devices currently authorized to access your LankaPay account.',
                            style: TextStyle(fontSize: 12, color: Colors.grey),
                          ),
                          const SizedBox(height: 16),
                          ..._devices.map((dev) {
                            return Column(
                              children: [
                                ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  leading: CircleAvatar(
                                    backgroundColor: dev.isCurrent
                                        ? AppColors.primaryGreen.withOpacity(0.12)
                                        : Colors.grey.withOpacity(0.15),
                                    child: Icon(
                                      Icons.phone_android_rounded,
                                      color: dev.isCurrent ? AppColors.primaryGreen : Colors.grey,
                                    ),
                                  ),
                                  title: Row(
                                    children: [
                                      Text(dev.deviceName, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                                      if (dev.isCurrent) ...[
                                        const SizedBox(width: 8),
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                          decoration: BoxDecoration(
                                            color: AppColors.primaryGreen,
                                            borderRadius: BorderRadius.circular(4),
                                          ),
                                          child: const Text('THIS DEVICE', style: TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.w700)),
                                        ),
                                      ],
                                    ],
                                  ),
                                  subtitle: Text('${dev.platform}\n${dev.lastActive}', style: const TextStyle(fontSize: 11)),
                                  trailing: dev.isCurrent
                                      ? null
                                      : IconButton(
                                          key: Key('revoke_device_btn_${dev.id}'),
                                          icon: const Icon(Icons.logout_rounded, color: AppColors.error, size: 20),
                                          onPressed: () => _revokeDevice(dev),
                                          tooltip: 'Revoke',
                                        ),
                                ),
                                const Divider(height: 1),
                              ],
                            );
                          }),
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

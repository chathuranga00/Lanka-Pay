import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.navHistory ?? 'Transaction History'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(16.0),
                children: [
                  Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppColors.error.withOpacity(0.12),
                        child: const Icon(Icons.arrow_upward_rounded, color: AppColors.error),
                      ),
                      title: const Text(
                        'Transfer to +9477***4567',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: const Text('Yesterday, 14:32 • SIMULATED'),
                      trailing: const Text(
                        '- LKR 2,500.00',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 15,
                          color: AppColors.error,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppColors.success.withOpacity(0.12),
                        child: const Icon(Icons.arrow_downward_rounded, color: AppColors.success),
                      ),
                      title: const Text(
                        'Received from +9471***8891',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: const Text('10 Oct 2026, 09:15 • SIMULATED'),
                      trailing: const Text(
                        '+ LKR 5,000.00',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 15,
                          color: AppColors.success,
                        ),
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

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';

class WelcomeScreen extends StatefulWidget {
  const WelcomeScreen({super.key});

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  final List<OnboardingItem> _pages = const [
    OnboardingItem(
      icon: Icons.flash_on_rounded,
      title: 'Instant Fund Transfers',
      subtitle: 'Transfer funds 24/7 between any Sri Lankan commercial bank with near-zero latency.',
    ),
    OnboardingItem(
      icon: Icons.qr_code_scanner_rounded,
      title: 'Universal LANKAQR & Pay-ID',
      subtitle: 'Pay at merchants, scan dynamic QR codes, or send money directly to verified mobile numbers.',
    ),
    OnboardingItem(
      icon: Icons.security_rounded,
      title: 'Bank-Grade Security',
      subtitle: 'Multi-factor authentication, step-up PIN verification, and end-to-end encrypted account references.',
    ),
  ];

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // Persistent warning banner
            const SimulatedBanner(),

            Expanded(
              child: PageView.builder(
                controller: _pageController,
                itemCount: _pages.length,
                onPageChanged: (index) {
                  setState(() {
                    _currentPage = index;
                  });
                },
                itemBuilder: (context, index) {
                  final item = _pages[index];
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32.0, vertical: 24.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 120,
                          height: 120,
                          decoration: BoxDecoration(
                            color: isDark
                                ? AppColors.primaryGreenLight.withOpacity(0.15)
                                : AppColors.primaryGreen.withOpacity(0.08),
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            item.icon,
                            size: 64,
                            color: isDark ? AppColors.accentGoldLight : AppColors.primaryGreen,
                          ),
                        ),
                        const SizedBox(height: 36),
                        Text(
                          item.title,
                          style: TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.w700,
                            color: isDark ? Colors.white : AppColors.textPrimaryLight,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          item.subtitle,
                          style: TextStyle(
                            fontSize: 15,
                            height: 1.5,
                            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),

            // Page Indicator Dots
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                _pages.length,
                (index) => AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  width: _currentPage == index ? 24 : 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _currentPage == index
                        ? AppColors.primaryGreen
                        : Colors.grey.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 32),

            // Action Buttons
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ElevatedButton(
                    key: const Key('get_started_btn'),
                    onPressed: () {
                      context.push('/login');
                    },
                    child: Text(l10n?.getStarted ?? 'Get Started'),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton(
                    key: const Key('sign_in_btn'),
                    onPressed: () {
                      context.push('/login');
                    },
                    child: Text(l10n?.signIn ?? 'Sign In'),
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

class OnboardingItem {
  final IconData icon;
  final String title;
  final String subtitle;

  const OnboardingItem({
    required this.icon,
    required this.title,
    required this.subtitle,
  });
}

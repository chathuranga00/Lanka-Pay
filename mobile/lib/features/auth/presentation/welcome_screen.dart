import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_tokens.dart';
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
      gradient: AppTokens.violetTileGradient,
    ),
    OnboardingItem(
      icon: Icons.qr_code_scanner_rounded,
      title: 'Universal LANKAQR & Pay-ID',
      subtitle: 'Pay at merchants, scan dynamic QR codes, or send money directly to verified mobile numbers.',
      gradient: AppTokens.orangeTileGradient,
    ),
    OnboardingItem(
      icon: Icons.security_rounded,
      title: 'Bank-Grade Security',
      subtitle: 'Multi-factor authentication, step-up PIN verification, and end-to-end encrypted account references.',
      gradient: AppTokens.pinkTileGradient,
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
    final reduceMotion = MediaQuery.of(context).disableAnimations;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.sheetLight,
      body: SafeArea(
        child: Column(
          children: [
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
                    padding: const EdgeInsets.symmetric(horizontal: 28.0, vertical: 20.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 110,
                          height: 110,
                          decoration: BoxDecoration(
                            gradient: item.gradient,
                            borderRadius: BorderRadius.circular(32),
                            boxShadow: [
                              BoxShadow(
                                color: item.gradient.colors.first.withOpacity(0.35),
                                blurRadius: 20,
                                offset: const Offset(0, 8),
                              ),
                            ],
                          ),
                          child: Icon(
                            item.icon,
                            size: 52,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 36),
                        Text(
                          item.title,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 24,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.4,
                            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 14),
                        Text(
                          item.subtitle,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 14,
                            height: 1.5,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
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
                  duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 250),
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  width: _currentPage == index ? 24 : 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _currentPage == index
                        ? AppTokens.primaryViolet
                        : (isDark ? Colors.white24 : Colors.black12),
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
                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 52),
                    child: ElevatedButton(
                      key: const Key('get_started_btn'),
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        context.push('/login');
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTokens.primaryViolet,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                      child: Text(
                        l10n?.getStarted ?? 'Get Started',
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 52),
                    child: OutlinedButton(
                      key: const Key('sign_in_btn'),
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        context.push('/login');
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: isDark ? AppTokens.lightViolet : AppTokens.primaryViolet,
                        side: BorderSide(
                          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                          width: 1.2,
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                      child: Text(
                        l10n?.signIn ?? 'Sign In',
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
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

class OnboardingItem {
  final IconData icon;
  final String title;
  final String subtitle;
  final LinearGradient gradient;

  const OnboardingItem({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.gradient,
  });
}

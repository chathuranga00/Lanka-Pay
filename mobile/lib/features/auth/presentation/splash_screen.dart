import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/storage/token_storage.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../l10n/app_localizations.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> with SingleTickerProviderStateMixin {
  late AnimationController _animController;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );
    _fadeAnimation = CurvedAnimation(
      parent: _animController,
      curve: Curves.easeIn,
    );
    _animController.forward();
    _checkAuth();
  }

  Future<void> _checkAuth() async {
    await Future.delayed(const Duration(milliseconds: 1200));
    if (!mounted) return;

    String? token;
    try {
      final tokenStorage = ref.read(tokenStorageProvider);
      token = await tokenStorage.getAccessToken();
    } catch (e) {
      // flutter_secure_storage may not be fully supported on web;
      // treat any error as "no token" so the app still navigates.
      debugPrint('Token read failed (expected on web): $e');
      token = null;
    }

    if (mounted) {
      if (token != null && token.isNotEmpty) {
        context.go('/home');
      } else {
        context.go('/welcome');
      }
    }
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final appTitle = l10n?.appTitle ?? 'Lanka Pay';
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final reduceMotion = MediaQuery.of(context).disableAnimations;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : const Color(0xFF14142B),
      body: Stack(
        children: [
          // Ambient Glow Background
          Positioned(
            top: -120,
            right: -100,
            child: Container(
              width: 320,
              height: 320,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    AppTokens.primaryViolet.withOpacity(0.35),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),
          Center(
            child: reduceMotion
                ? _buildContent(appTitle)
                : FadeTransition(
                    opacity: _fadeAnimation,
                    child: _buildContent(appTitle),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildContent(String appTitle) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 96,
          height: 96,
          decoration: BoxDecoration(
            gradient: AppTokens.violetTileGradient,
            borderRadius: BorderRadius.circular(28),
            boxShadow: [
              BoxShadow(
                color: AppTokens.primaryViolet.withOpacity(0.4),
                blurRadius: 24,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: const Center(
            child: Icon(
              Icons.account_balance_wallet_rounded,
              size: 48,
              color: Colors.white,
            ),
          ),
        ),
        const SizedBox(height: 24),
        Text(
          appTitle,
          style: const TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 32,
            fontWeight: FontWeight.w800,
            color: Colors.white,
            letterSpacing: -0.5,
          ),
        ),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
          decoration: BoxDecoration(
            color: AppTokens.primaryViolet.withOpacity(0.18),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: AppTokens.lightViolet.withOpacity(0.4),
              width: 1,
            ),
          ),
          child: const Text(
            'LANKAPAY-ID • INSTANT CLEARING',
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.1,
              color: AppTokens.lightViolet,
            ),
          ),
        ),
        const SizedBox(height: 48),
        const SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(
            strokeWidth: 2.2,
            valueColor: AlwaysStoppedAnimation<Color>(AppTokens.lightViolet),
          ),
        ),
      ],
    );
  }
}

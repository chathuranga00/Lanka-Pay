import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// User-friendly error message card with an explicit retry action button.
class ErrorRetry extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  final Key? retryKey;
  final String retryLabel;
  final bool compact;

  const ErrorRetry({
    super.key,
    required this.message,
    required this.onRetry,
    this.retryKey,
    this.retryLabel = 'Try Again',
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (compact) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                message,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
                textAlign: TextAlign.center,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 6),
              InkWell(
                key: retryKey ?? const Key('error_retry_btn'),
                onTap: onRetry,
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.refresh_rounded, size: 14, color: AppTokens.primaryBlue),
                      const SizedBox(width: 4),
                      Text(
                        retryLabel,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: AppTokens.primaryBlue,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppTokens.space24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: AppTokens.errorLight,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: const Icon(
                Icons.error_outline_rounded,
                size: 36,
                color: AppTokens.error,
              ),
            ),
            const SizedBox(height: 18),
            Text(
              'Something went wrong',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 13,
                fontWeight: FontWeight.w400,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                height: 1.4,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              key: retryKey ?? const Key('error_retry_btn'),
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: Text(retryLabel),
            ),
          ],
        ),
      ),
    );
  }
}

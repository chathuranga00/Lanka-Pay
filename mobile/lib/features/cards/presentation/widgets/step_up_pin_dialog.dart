import 'package:flutter/material.dart';
import '../../../../core/test_keys.dart';
import '../../../../core/theme/app_tokens.dart';

/// Step-up authentication dialog prompting for 6-digit PIN.
Future<String?> showStepUpPinDialog(
  BuildContext context, {
  String title = 'Security Verification',
  String subtitle = 'Enter your 6-digit PIN to proceed.',
}) {
  final controller = TextEditingController();
  final isDark = Theme.of(context).brightness == Brightness.dark;

  return showDialog<String>(
    context: context,
    barrierDismissible: true,
    builder: (ctx) => StatefulBuilder(
      builder: (context, setState) {
        final pin = controller.text.trim();
        final isValid = pin.length == 6 && RegExp(r'^\d{6}$').hasMatch(pin);

        return AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
          backgroundColor: isDark ? AppTokens.cardDark : Colors.white,
          title: Text(
            title,
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontWeight: FontWeight.w700,
              fontSize: 18,
              color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
            ),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                subtitle,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 18),
              TextField(
                key: AppTestKeys.stepUpPinInput,
                controller: controller,
                keyboardType: TextInputType.number,
                maxLength: 6,
                obscureText: true,
                autofocus: true,
                autocorrect: false,
                enableSuggestions: false,
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  counterText: '',
                  hintText: '••••••',
                  prefixIcon: const Icon(Icons.lock_outline, size: 20),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(null),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              key: AppTestKeys.stepUpPinConfirmBtn,
              onPressed: isValid
                  ? () => Navigator.of(ctx).pop(controller.text.trim())
                  : null,
              child: const Text('Confirm'),
            ),
          ],
        );
      },
    ),
  );
}

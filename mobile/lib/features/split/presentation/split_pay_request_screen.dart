import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/split_service.dart';
import '../domain/split_models.dart';

class SplitPayRequestScreen extends ConsumerStatefulWidget {
  final String splitId;
  final SplitModel? initialSplit;

  const SplitPayRequestScreen({
    super.key,
    required this.splitId,
    this.initialSplit,
  });

  @override
  ConsumerState<SplitPayRequestScreen> createState() => _SplitPayRequestScreenState();
}

class _SplitPayRequestScreenState extends ConsumerState<SplitPayRequestScreen> {
  final _pinController = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  SplitModel? _split;
  bool _isLoading = true;
  bool _isPaying = false;
  String? _errorMessage;
  bool _isSuccess = false;

  @override
  void initState() {
    super.initState();
    _split = widget.initialSplit;
    _loadSplit();
  }

  @override
  void dispose() {
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _loadSplit() async {
    setState(() {
      _isLoading = _split == null;
      _errorMessage = null;
    });

    try {
      final service = ref.read(splitServiceProvider);
      final data = await service.getSplit(widget.splitId);
      if (mounted) {
        setState(() {
          _split = data;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceAll('Exception: ', '');
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _onPay() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isPaying = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(splitServiceProvider);
      final updated = await service.payShare(
        widget.splitId,
        pin: _pinController.text.trim(),
      );

      if (mounted) {
        setState(() {
          _split = updated;
          _isSuccess = true;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceAll('Exception: ', '');
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _isPaying = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text('Split Bill Request'),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null && _split == null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(AppTokens.spacing24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline, size: 48, color: Colors.red),
                        const SizedBox(height: AppTokens.spacing16),
                        Text(_errorMessage!, textAlign: TextAlign.center),
                        const SizedBox(height: AppTokens.spacing16),
                        FilledButton(onPressed: _loadSplit, child: const Text('Retry')),
                      ],
                    ),
                  ),
                )
              : _isSuccess
                  ? _buildSuccessView(context, isDark)
                  : _buildPayForm(context, isDark),
    );
  }

  Widget _buildSuccessView(BuildContext context, bool isDark) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(AppTokens.spacing24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            const CircleAvatar(
              radius: 40,
              backgroundColor: Colors.green,
              child: Icon(Icons.check, size: 48, color: Colors.white),
            ),
            const SizedBox(height: AppTokens.spacing24),
            Text(
              'Payment Successful!',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: AppTokens.spacing8),
            Text(
              'You have settled your share for "${_split?.title}".',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: isDark ? Colors.grey[300] : Colors.grey[700],
              ),
            ),
            const SizedBox(height: AppTokens.spacing32),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: AppTokens.primaryBlue,
                ),
                onPressed: () {
                  if (context.canPop()) {
                    context.pop();
                  } else {
                    context.go('/pay');
                  }
                },
                child: const Text('Done'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPayForm(BuildContext context, bool isDark) {
    final split = _split!;

    // Current participant share
    final participant = split.participants.isNotEmpty ? split.participants.first : null;
    final shareAmount = participant?.shareAmount ?? 0.0;

    return SafeArea(
      child: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(AppTokens.spacing16),
          children: [
            if (_errorMessage != null)
              Container(
                margin: const EdgeInsets.only(bottom: AppTokens.spacing16),
                padding: const EdgeInsets.all(AppTokens.spacing12),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                  border: Border.all(color: Colors.red.withOpacity(0.4)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline, color: Colors.red, size: 20),
                    const SizedBox(width: AppTokens.spacing8),
                    Expanded(
                      child: Text(
                        _errorMessage!,
                        style: const TextStyle(color: Colors.red, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),

            // Summary Card
            Card(
              elevation: 0,
              color: isDark ? AppTokens.cardDark : AppTokens.tileTintLight,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(AppTokens.radiusLg),
              ),
              child: Padding(
                padding: const EdgeInsets.all(AppTokens.spacing20),
                child: Column(
                  children: [
                    Text(
                      'Payment Requested By',
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? Colors.grey[400] : Colors.grey[600],
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      split.ownerName,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: AppTokens.spacing16),
                    Text(
                      split.title,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                    ),
                    const SizedBox(height: AppTokens.spacing12),
                    Text(
                      'LKR ${shareAmount > 0 ? shareAmount.toStringAsFixed(2) : split.totalAmount.toStringAsFixed(2)}',
                      style: const TextStyle(
                        fontSize: 32,
                        fontWeight: FontWeight.bold,
                        color: AppTokens.primaryBlueDark,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Total bill: LKR ${split.totalAmount.toStringAsFixed(2)}',
                      style: TextStyle(
                        fontSize: 13,
                        color: isDark ? Colors.grey[400] : Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: AppTokens.spacing24),

            // Step-up Auth: PIN
            Text(
              'Confirm with your 4-digit PIN',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: AppTokens.spacing8),
            TextFormField(
              key: TestKeys.splitPinField,
              controller: _pinController,
              obscureText: true,
              keyboardType: TextInputType.number,
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(4),
              ],
              decoration: InputDecoration(
                hintText: '••••',
                prefixIcon: const Icon(Icons.lock_outline),
                filled: true,
                fillColor: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                  borderSide: BorderSide.none,
                ),
              ),
              validator: (val) {
                if (val == null || val.length != 4) {
                  return 'Enter your 4-digit PIN';
                }
                return null;
              },
            ),
            const SizedBox(height: AppTokens.spacing32),

            // Pay Button
            SizedBox(
              width: double.infinity,
              height: 52,
              child: FilledButton(
                key: TestKeys.splitPay,
                onPressed: _isPaying ? null : _onPay,
                style: FilledButton.styleFrom(
                  backgroundColor: AppTokens.primaryBlue,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                  ),
                ),
                child: _isPaying
                    ? const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                      )
                    : const Text(
                        'Pay Share Now',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

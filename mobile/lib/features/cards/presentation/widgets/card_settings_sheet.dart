import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/test_keys.dart';
import '../../../../core/theme/app_tokens.dart';
import '../../../accounts/data/bank_account_service.dart';
import '../../data/card_service.dart';
import '../../domain/card_model.dart';
import 'step_up_pin_dialog.dart';

/// Card settings modal bottom sheet.
/// Manages:
/// - Nickname
/// - Per-payment limit & daily limit (raising limits requires step-up PIN)
/// - Online payments switch
/// - Backing bank account selector
/// - Remove card with confirmation dialog
class CardSettingsSheet extends ConsumerStatefulWidget {
  final CardModel card;

  const CardSettingsSheet({
    super.key,
    required this.card,
  });

  static Future<void> show(BuildContext context, {required CardModel card}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => CardSettingsSheet(card: card),
    );
  }

  @override
  ConsumerState<CardSettingsSheet> createState() => _CardSettingsSheetState();
}

class _CardSettingsSheetState extends ConsumerState<CardSettingsSheet> {
  late TextEditingController _nicknameController;
  late TextEditingController _perPaymentLimitController;
  late TextEditingController _dailyLimitController;
  late bool _onlineEnabled;
  int? _selectedBankAccountId;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _nicknameController = TextEditingController(text: widget.card.nickname ?? '');
    _perPaymentLimitController = TextEditingController(
      text: (widget.card.perPaymentLimitMinor / 100).toStringAsFixed(0),
    );
    _dailyLimitController = TextEditingController(
      text: (widget.card.dailyLimitMinor / 100).toStringAsFixed(0),
    );
    _onlineEnabled = widget.card.onlinePaymentsEnabled;
    _selectedBankAccountId = widget.card.linkedBankAccountId;
  }

  @override
  void dispose() {
    _nicknameController.dispose();
    _perPaymentLimitController.dispose();
    _dailyLimitController.dispose();
    super.dispose();
  }

  Future<void> _saveNickname() async {
    final newNickname = _nicknameController.text.trim();
    setState(() => _saving = true);
    try {
      await ref.read(cardsProvider.notifier).patchCard(
        widget.card.id,
        nickname: newNickname,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Card nickname updated')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to update nickname: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _saveLimits() async {
    final perPayLkr = double.tryParse(_perPaymentLimitController.text.trim());
    final dailyLkr = double.tryParse(_dailyLimitController.text.trim());
    if (perPayLkr == null || dailyLkr == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter valid numeric limits')),
      );
      return;
    }

    final newPerPayMinor = (perPayLkr * 100).toInt();
    final newDailyMinor = (dailyLkr * 100).toInt();

    // Check if limits are being raised — if so, prompt for step-up PIN
    final isRaising = newPerPayMinor > widget.card.perPaymentLimitMinor ||
        newDailyMinor > widget.card.dailyLimitMinor;

    String? stepUpPin;
    if (isRaising) {
      stepUpPin = await showStepUpPinDialog(
        context,
        title: 'Raise Spending Limits',
        subtitle: 'Enter your 6-digit PIN to increase card spending limits.',
      );
      if (stepUpPin == null) return; // User cancelled
    }

    setState(() => _saving = true);
    try {
      await ref.read(cardsProvider.notifier).patchCard(
        widget.card.id,
        perPaymentLimitMinor: newPerPayMinor,
        dailyLimitMinor: newDailyMinor,
        pin: stepUpPin,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Spending limits updated')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to update limits: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _toggleOnlinePayments(bool enabled) async {
    setState(() {
      _onlineEnabled = enabled;
      _saving = true;
    });
    try {
      await ref.read(cardsProvider.notifier).patchCard(
        widget.card.id,
        onlinePaymentsEnabled: enabled,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(enabled
                ? 'Online payments enabled'
                : 'Online payments disabled'),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _onlineEnabled = !enabled);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to update online payments: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _confirmRemoveCard() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Remove Card'),
        content: const Text(
          'Are you sure you want to remove this card? Future payments with this card will be blocked.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            key: AppTestKeys.confirmRemoveCardBtn,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTokens.error,
              foregroundColor: Colors.white,
            ),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Remove'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        await ref.read(cardsProvider.notifier).removeCard(widget.card.id);
        if (mounted) {
          Navigator.of(context).pop();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Card removed successfully')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to remove card: $e')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accountsAsync = ref.watch(bankAccountsProvider);
    final bankAccounts = accountsAsync.value ?? [];

    return Container(
      key: AppTestKeys.cardSettingsSheet,
      padding: EdgeInsets.only(
        top: 12,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Material(
        color: Colors.transparent,
        child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 12.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Handle bar
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              Text(
                'Card Settings',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
              ),
              const SizedBox(height: 20),

              // 1. Nickname
              Text(
                'Card Nickname',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: AppTestKeys.cardSettingsNicknameField,
                      controller: _nicknameController,
                      decoration: InputDecoration(
                        hintText: 'e.g. My Shopping Card',
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  ElevatedButton(
                    key: AppTestKeys.cardSettingsSaveNicknameBtn,
                    onPressed: _saving ? null : _saveNickname,
                    child: const Text('Save'),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // 2. Spending Limits
              Text(
                'Spending Limits (LKR)',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Per-Payment Limit', style: TextStyle(fontSize: 12, color: Colors.grey)),
                        const SizedBox(height: 4),
                        TextField(
                          key: AppTestKeys.cardSettingsPerPaymentLimitField,
                          controller: _perPaymentLimitController,
                          keyboardType: TextInputType.number,
                          decoration: InputDecoration(
                            prefixText: 'LKR ',
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Daily Limit', style: TextStyle(fontSize: 12, color: Colors.grey)),
                        const SizedBox(height: 4),
                        TextField(
                          key: AppTestKeys.cardSettingsDailyLimitField,
                          controller: _dailyLimitController,
                          keyboardType: TextInputType.number,
                          decoration: InputDecoration(
                            prefixText: 'LKR ',
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Align(
                alignment: Alignment.centerRight,
                child: ElevatedButton(
                  key: AppTestKeys.cardSettingsSaveLimitsBtn,
                  onPressed: _saving ? null : _saveLimits,
                  child: const Text('Update Limits'),
                ),
              ),
              const SizedBox(height: 20),

              // 3. Online Payments Toggle
              SwitchListTile(
                key: AppTestKeys.cardSettingsOnlineSwitch,
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Online Payments',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                subtitle: const Text(
                  'Allow online e-commerce transactions',
                  style: TextStyle(fontSize: 12),
                ),
                value: _onlineEnabled,
                onChanged: _saving ? null : _toggleOnlinePayments,
              ),
              const Divider(height: 24),

              // 4. Backing Bank Account Dropdown
              Text(
                'Backing Bank Account',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<int>(
                key: AppTestKeys.cardSettingsBackingAccountDropdown,
                value: _selectedBankAccountId != null &&
                        bankAccounts.any((a) => a.id == _selectedBankAccountId)
                    ? _selectedBankAccountId
                    : (bankAccounts.isNotEmpty ? bankAccounts.first.id : null),
                decoration: InputDecoration(
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                ),
                items: bankAccounts.map((a) {
                  return DropdownMenuItem<int>(
                    value: a.id,
                    child: Text('${a.institutionCode} (${a.maskedNumber})'),
                  );
                }).toList(),
                onChanged: (val) {
                  setState(() => _selectedBankAccountId = val);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Backing account updated')),
                  );
                },
              ),
              const SizedBox(height: 32),

              // 5. Remove Card Button
              ListTile(
                key: AppTestKeys.cardSettingsRemoveBtn,
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: AppTokens.error.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.delete_outline_rounded,
                    color: AppTokens.error,
                    size: 20,
                  ),
                ),
                title: const Text(
                  'Remove Card',
                  style: TextStyle(
                    color: AppTokens.error,
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
                subtitle: const Text(
                  'Unlink this card from LankaPay ID',
                  style: TextStyle(fontSize: 12),
                ),
                trailing: const Icon(Icons.chevron_right_rounded, color: AppTokens.error, size: 20),
                onTap: _confirmRemoveCard,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
}

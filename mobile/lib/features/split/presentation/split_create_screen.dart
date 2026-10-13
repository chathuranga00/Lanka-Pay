import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/split_service.dart';

class SplitParticipantDraft {
  String phone;
  String name;
  double customShare;

  SplitParticipantDraft({
    required this.phone,
    this.name = '',
    this.customShare = 0.0,
  });
}

class SplitCreateScreen extends ConsumerStatefulWidget {
  const SplitCreateScreen({super.key});

  @override
  ConsumerState<SplitCreateScreen> createState() => _SplitCreateScreenState();
}

class _SplitCreateScreenState extends ConsumerState<SplitCreateScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _amountController = TextEditingController();
  final _phoneController = TextEditingController();
  final _customShareController = TextEditingController();

  bool _isEqualMode = true;
  bool _isLoading = false;
  String? _errorMessage;

  final List<SplitParticipantDraft> _participants = [];

  // Recent recipients shortcut
  final List<Map<String, String>> _recentRecipients = [
    {'name': 'Kamal Perera', 'phone': '+94771234567'},
    {'name': 'Nimal Silva', 'phone': '+94772345678'},
    {'name': 'Sanduni Jayawardena', 'phone': '+94773456789'},
    {'name': 'Dinesh Fernando', 'phone': '+94774567890'},
  ];

  @override
  void dispose() {
    _titleController.dispose();
    _amountController.dispose();
    _phoneController.dispose();
    _customShareController.dispose();
    super.dispose();
  }

  void _addParticipant() {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a phone number')),
      );
      return;
    }

    if (_participants.length >= 10) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Maximum 10 participants allowed')),
      );
      return;
    }

    if (_participants.any((p) => p.phone == phone)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Participant already added')),
      );
      return;
    }

    double customVal = 0.0;
    if (!_isEqualMode) {
      final parsed = double.tryParse(_customShareController.text.trim());
      if (parsed == null || parsed <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please enter a valid share amount')),
        );
        return;
      }
      customVal = parsed;
    }

    setState(() {
      _participants.add(SplitParticipantDraft(
        phone: phone,
        name: phone,
        customShare: customVal,
      ));
      _phoneController.clear();
      _customShareController.clear();
    });
  }

  void _addRecentRecipient(Map<String, String> recipient) {
    if (_participants.length >= 10) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Maximum 10 participants allowed')),
      );
      return;
    }
    if (_participants.any((p) => p.phone == recipient['phone'])) {
      return;
    }
    setState(() {
      _participants.add(SplitParticipantDraft(
        phone: recipient['phone']!,
        name: recipient['name']!,
      ));
    });
  }

  void _removeParticipant(int index) {
    setState(() {
      _participants.removeAt(index);
    });
  }

  Future<void> _submitSplit() async {
    if (!_formKey.currentState!.validate()) return;

    if (_participants.isEmpty) {
      setState(() {
        _errorMessage = 'Add at least 1 participant to split with';
      });
      return;
    }

    final totalAmount = double.tryParse(_amountController.text.trim()) ?? 0.0;
    if (totalAmount <= 0) {
      setState(() {
        _errorMessage = 'Enter a valid total amount';
      });
      return;
    }

    final totalMinor = (totalAmount * 100).round();

    List<Map<String, dynamic>> participantPayload = [];

    if (_isEqualMode) {
      for (final p in _participants) {
        participantPayload.add({'phone': p.phone});
      }
    } else {
      int sumShares = 0;
      for (final p in _participants) {
        final shareMinor = (p.customShare * 100).round();
        sumShares += shareMinor;
        participantPayload.add({
          'phone': p.phone,
          'shareMinor': shareMinor,
        });
      }
      if (sumShares >= totalMinor) {
        setState(() {
          _errorMessage =
              'Participants custom shares ($sumShares minor) must be less than total ($totalMinor minor) to leave owner share';
        });
        return;
      }
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(splitServiceProvider);
      final created = await service.createSplit(
        title: _titleController.text.trim(),
        totalMinor: totalMinor,
        currency: 'LKR',
        mode: _isEqualMode ? 'EQUAL' : 'CUSTOM',
        participants: participantPayload,
        validityHours: 48,
      );

      if (mounted) {
        context.pushReplacement('/split/details/${created.id}', extra: created);
      }
    } catch (e) {
      setState(() {
        _errorMessage = e.toString().replaceAll('Exception: ', '');
      });
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final totalParsed = double.tryParse(_amountController.text.trim()) ?? 0.0;
    final totalPeople = _participants.length + 1;
    final equalSharePreview = totalParsed > 0 ? (totalParsed / totalPeople) : 0.0;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text('Split a Bill'),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
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

              // Title Field
              TextFormField(
                key: TestKeys.splitTitleField,
                controller: _titleController,
                decoration: InputDecoration(
                  labelText: 'What is this for?',
                  hintText: 'e.g. Dinner, Rent, Dutch Hospital Lunch',
                  filled: true,
                  fillColor: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                    borderSide: BorderSide.none,
                  ),
                ),
                validator: (val) =>
                    (val == null || val.trim().isEmpty) ? 'Please enter a title' : null,
              ),
              const SizedBox(height: AppTokens.spacing16),

              // Amount Field
              TextFormField(
                key: TestKeys.splitAmountField,
                controller: _amountController,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'^\d+\.?\d{0,2}')),
                ],
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  labelText: 'Total Amount (LKR)',
                  hintText: '0.00',
                  prefixText: 'LKR ',
                  filled: true,
                  fillColor: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                    borderSide: BorderSide.none,
                  ),
                ),
                validator: (val) {
                  final parsed = double.tryParse(val ?? '');
                  if (parsed == null || parsed <= 0) {
                    return 'Enter a valid amount';
                  }
                  return null;
                },
              ),
              const SizedBox(height: AppTokens.spacing20),

              // Mode selector (Equal / Custom)
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      key: TestKeys.splitEqualMode,
                      style: OutlinedButton.styleFrom(
                        backgroundColor: _isEqualMode
                            ? AppTokens.primaryBlue
                            : Colors.transparent,
                        foregroundColor: _isEqualMode ? Colors.white : null,
                        side: BorderSide(
                          color: _isEqualMode
                              ? AppTokens.primaryBlue
                              : Colors.grey.withOpacity(0.3),
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                        ),
                        padding: const EdgeInsets.symmetric(vertical: AppTokens.spacing12),
                      ),
                      onPressed: () => setState(() => _isEqualMode = true),
                      child: const Text('Split Equally'),
                    ),
                  ),
                  const SizedBox(width: AppTokens.spacing12),
                  Expanded(
                    child: OutlinedButton(
                      key: TestKeys.splitCustomMode,
                      style: OutlinedButton.styleFrom(
                        backgroundColor: !_isEqualMode
                            ? AppTokens.primaryBlue
                            : Colors.transparent,
                        foregroundColor: !_isEqualMode ? Colors.white : null,
                        side: BorderSide(
                          color: !_isEqualMode
                              ? AppTokens.primaryBlue
                              : Colors.grey.withOpacity(0.3),
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                        ),
                        padding: const EdgeInsets.symmetric(vertical: AppTokens.spacing12),
                      ),
                      onPressed: () => setState(() => _isEqualMode = false),
                      child: const Text('Custom Shares'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppTokens.spacing16),

              if (_isEqualMode && totalParsed > 0)
                Container(
                  padding: const EdgeInsets.all(AppTokens.spacing12),
                  decoration: BoxDecoration(
                    color: AppTokens.tileTintLight,
                    borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline, color: AppTokens.primaryBlue, size: 20),
                      const SizedBox(width: AppTokens.spacing8),
                      Expanded(
                        child: Text(
                          'Each pays ~LKR ${equalSharePreview.toStringAsFixed(2)} ($totalPeople people including you)',
                          style: const TextStyle(
                            color: AppTokens.primaryBlueDark,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: AppTokens.spacing20),

              // Quick Add Recent Recipients
              Text(
                'Recent Recipients',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: AppTokens.spacing8),
              SizedBox(
                height: 40,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: _recentRecipients.length,
                  separatorBuilder: (_, _) => const SizedBox(width: AppTokens.spacing8),
                  itemBuilder: (context, idx) {
                    final r = _recentRecipients[idx];
                    final isAdded = _participants.any((p) => p.phone == r['phone']);
                    return ActionChip(
                      avatar: CircleAvatar(
                        backgroundColor: isAdded ? Colors.green : AppTokens.primaryBlue,
                        child: Icon(
                          isAdded ? Icons.check : Icons.add,
                          size: 14,
                          color: Colors.white,
                        ),
                      ),
                      label: Text(r['name']!),
                      onPressed: () => _addRecentRecipient(r),
                    );
                  },
                ),
              ),
              const SizedBox(height: AppTokens.spacing20),

              // Add Participant Input
              Text(
                'Add Participant (${_participants.length}/10)',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: AppTokens.spacing8),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 3,
                    child: TextField(
                      key: TestKeys.splitPersonPhoneField,
                      controller: _phoneController,
                      keyboardType: TextInputType.phone,
                      decoration: InputDecoration(
                        hintText: 'Mobile: +9477xxxxxxx',
                        filled: true,
                        fillColor: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                  if (!_isEqualMode) ...[
                    const SizedBox(width: AppTokens.spacing8),
                    Expanded(
                      flex: 2,
                      child: TextField(
                        key: TestKeys.splitPersonShareField,
                        controller: _customShareController,
                        keyboardType:
                            const TextInputType.numberWithOptions(decimal: true),
                        decoration: InputDecoration(
                          hintText: 'Share LKR',
                          filled: true,
                          fillColor:
                              isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                            borderSide: BorderSide.none,
                          ),
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(width: AppTokens.spacing8),
                  IconButton.filled(
                    key: TestKeys.splitAddPerson,
                    onPressed: _addParticipant,
                    icon: const Icon(Icons.add),
                    tooltip: 'Add Person',
                  ),
                ],
              ),
              const SizedBox(height: AppTokens.spacing16),

              // Participant list
              if (_participants.isNotEmpty)
                Card(
                  elevation: 0,
                  color: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppTokens.radiusLg),
                  ),
                  child: ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: _participants.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, idx) {
                      final p = _participants[idx];
                      return ListTile(
                        leading: CircleAvatar(
                          backgroundColor: AppTokens.primaryBlue.withOpacity(0.2),
                          child: Text(
                            p.name.isNotEmpty ? p.name[0].toUpperCase() : 'P',
                            style: const TextStyle(
                              color: AppTokens.primaryBlue,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        title: Text(p.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text(p.phone),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (!_isEqualMode)
                              Text(
                                'LKR ${p.customShare.toStringAsFixed(2)}',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: AppTokens.primaryBlue,
                                ),
                              ),
                            IconButton(
                              icon: const Icon(Icons.close, size: 18, color: Colors.grey),
                              onPressed: () => _removeParticipant(idx),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),

              const SizedBox(height: AppTokens.spacing32),

              // Submit / Send Button
              SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton(
                  key: TestKeys.splitSend,
                  onPressed: _isLoading ? null : _submitSplit,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppTokens.primaryBlue,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                    ),
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                        )
                      : Text(
                          'Create Split (${_participants.length} Participant${_participants.length == 1 ? '' : 's'})',
                          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

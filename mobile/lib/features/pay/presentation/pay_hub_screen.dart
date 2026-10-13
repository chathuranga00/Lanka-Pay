import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../core/config/feature_flags.dart';
import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/app_avatar.dart';
import '../../../core/widgets/product_tile.dart';
import '../../../core/widgets/section_header.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../accounts/domain/bank_account_model.dart';
import '../../auth/data/auth_service.dart';
import '../../cards/data/card_service.dart';
import '../../cards/domain/card_model.dart';
import '../../home/data/home_providers.dart';
import '../../send/data/transfer_service.dart';
import '../../send/domain/transfer_models.dart';

/// Consolidated Payments Hub (Tab 1: Pay).
/// Serves as the central hub for everything that moves money in LankaPay.
class PayHubScreen extends ConsumerStatefulWidget {
  const PayHubScreen({super.key = const Key('pay_hub_screen')});

  @override
  ConsumerState<PayHubScreen> createState() => _PayHubScreenState();
}

class _PayHubScreenState extends ConsumerState<PayHubScreen> {
  // Search & Lookup State
  final _searchController = TextEditingController();
  final _searchFocusNode = FocusNode();
  bool _isSearching = false;
  String? _searchError;
  Timer? _debounceTimer;

  // Rate Limiting Cooldown State
  int _cooldownSeconds = 0;
  Timer? _cooldownTimer;

  // Funding Source Choice State
  String? _selectedFundingType;
  int? _selectedFundingId;

  @override
  void initState() {
    super.initState();
    _loadFundingPreference();
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _cooldownTimer?.cancel();
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  Future<void> _loadFundingPreference() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final type = prefs.getString('last_used_funding_type');
      final id = prefs.getInt('last_used_funding_id');
      if (mounted && type != null && id != null) {
        setState(() {
          _selectedFundingType = type;
          _selectedFundingId = id;
        });
      }
    } catch (_) {}
  }

  Future<void> _setFundingPreference(String type, int id) async {
    setState(() {
      _selectedFundingType = type;
      _selectedFundingId = id;
    });
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('last_used_funding_type', type);
      await prefs.setInt('last_used_funding_id', id);
    } catch (_) {}
  }

  void _startCooldown([int seconds = 30]) {
    _cooldownTimer?.cancel();
    setState(() => _cooldownSeconds = seconds);
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      setState(() {
        if (_cooldownSeconds > 1) {
          _cooldownSeconds--;
        } else {
          _cooldownSeconds = 0;
          timer.cancel();
        }
      });
    });
  }

  bool _isValidSriLankanMobile(String raw) {
    final cleaned = raw.replaceAll(RegExp(r'[\s\-]'), '');
    if (RegExp(r'^(07[0-9]{8}|\+947[0-9]{8})$').hasMatch(cleaned)) {
      return true;
    }
    final normalized = AuthService.normalizePhone(raw);
    return RegExp(r'^\+947[0-9]{8}$').hasMatch(normalized);
  }

  bool _isValidAccountNumber(String raw) {
    final cleaned = raw.replaceAll(RegExp(r'[\s\-]'), '');
    return RegExp(r'^[0-9]{6,18}$').hasMatch(cleaned) && !_isValidSriLankanMobile(raw);
  }

  void _onSearchChanged(String value) {
    _debounceTimer?.cancel();
    if (_searchError != null) {
      setState(() => _searchError = null);
    }
    if (value.trim().isEmpty) return;

    final cleaned = value.replaceAll(RegExp(r'[\s\-]'), '');
    if ((cleaned.startsWith('07') && cleaned.length == 10) ||
        (cleaned.startsWith('+947') && cleaned.length == 12) ||
        (_isValidAccountNumber(value) && (cleaned.length >= 6 && cleaned.length <= 16))) {
      _debounceTimer = Timer(const Duration(milliseconds: 900), () {
        if (mounted) {
          _performRecipientLookup();
        }
      });
    }
  }

  Future<void> _performRecipientLookup() async {
    _debounceTimer?.cancel();
    final rawInput = _searchController.text.trim();
    if (rawInput.isEmpty) {
      setState(() => _searchError = 'Please enter a mobile or account number');
      return;
    }

    if (_cooldownSeconds > 0) {
      setState(() {
        _searchError = 'Too many lookup attempts. Please wait a moment before trying again.';
      });
      return;
    }

    if (_isValidSriLankanMobile(rawInput)) {
      final normalizedPhone = AuthService.normalizePhone(rawInput);
      setState(() {
        _isSearching = true;
        _searchError = null;
      });

      try {
        final result = await ref.read(transferServiceProvider).lookupRecipient(normalizedPhone);
        if (!mounted) return;

        setState(() => _isSearching = false);
        if (result.isFound) {
          context.push('/send', extra: result);
        } else {
          setState(() {
            _searchError = _getFriendlyLookupError(result.resultType);
          });
        }
      } on DioException catch (e) {
        if (!mounted) return;
        setState(() => _isSearching = false);
        if (e.response?.statusCode == 429) {
          _startCooldown();
          setState(() {
            _searchError = 'Too many lookup attempts. Please wait a moment before trying again.';
          });
        } else if (e.response?.data is Map) {
          final data = Map<String, dynamic>.from(e.response!.data as Map);
          final resultType = data['resultType'] as String?;
          setState(() {
            _searchError = _getFriendlyLookupError(resultType ?? 'NOT_FOUND');
          });
        } else {
          setState(() {
            _searchError = 'Payment registry service is temporarily unavailable. Please try again.';
          });
        }
      } catch (e) {
        if (!mounted) return;
        final errStr = e.toString().toLowerCase();
        setState(() => _isSearching = false);
        if (errStr.contains('429') || errStr.contains('too many attempts') || errStr.contains('rate limit')) {
          _startCooldown();
          setState(() {
            _searchError = 'Too many lookup attempts. Please wait a moment before trying again.';
          });
        } else {
          setState(() {
            _searchError = 'Could not reach recipient registry. Please check your connection.';
          });
        }
      }
    } else if (_isValidAccountNumber(rawInput)) {
      // Account number input routes to transfer flow
      context.push('/send', extra: rawInput);
    } else {
      setState(() {
        _searchError = 'Please enter a valid mobile number (e.g. 0771234567) or account number.';
      });
    }
  }

  String _getFriendlyLookupError(String resultType) {
    switch (resultType) {
      case 'DISCOVERY_DISABLED':
        return 'The recipient has disabled discovery for privacy under PDPA.';
      case 'NO_ELIGIBLE_ACCOUNT':
        return 'The recipient has no linked accounts configured to receive payments.';
      case 'PRIVACY_RESTRICTED':
      case 'UNAVAILABLE':
        return 'Unable to route transfer to this user due to privacy preferences.';
      case 'VERIFICATION_INCOMPLETE':
        return 'Recipient verification is incomplete or pending.';
      case 'NOT_FOUND':
      default:
        return 'No LankaPay-ID account found for this mobile number.';
    }
  }

  void _showComingSoon(BuildContext context, String featureTitle) {
    HapticFeedback.lightImpact();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? AppTokens.cardDark : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        key: AppTestKeys.comingSoonSheet,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: AppTokens.primaryViolet.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.rocket_launch_rounded,
                color: AppTokens.primaryViolet,
                size: 28,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              '$featureTitle - Coming Soon',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'This feature is currently under active development and will be available in an upcoming update.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 14,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                key: AppTestKeys.comingSoonClose,
                onPressed: () => Navigator.of(ctx).pop(),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTokens.primaryViolet,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                  elevation: 0,
                ),
                child: const Text(
                  'Got it',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showBetweenAccountsNotAvailable(BuildContext context) {
    HapticFeedback.lightImpact();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        backgroundColor: isDark ? AppTokens.cardDark : Colors.white,
        title: Row(
          children: [
            Icon(Icons.account_balance_rounded, color: AppTokens.primaryBlue),
            const SizedBox(width: 10),
            Text(
              'Between Accounts',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
          ],
        ),
        content: Text(
          'Transfers between your own accounts are not available in this version.',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 14,
            height: 1.4,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text(
              'OK',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showFundingSourceSheet(
    BuildContext context,
    List<BankAccountModel> accounts,
    List<CardModel> cards,
  ) {
    HapticFeedback.lightImpact();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? AppTokens.cardDark : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Select Payment Source',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close_rounded),
                    onPressed: () => Navigator.of(ctx).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (accounts.isNotEmpty) ...[
                Text(
                  'BANK ACCOUNTS',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 8),
                ...accounts.map((acc) {
                  final isSelected =
                      _selectedFundingType == 'ACCOUNT' && _selectedFundingId == acc.id;
                  return Material(
                    color: Colors.transparent,
                    child: ListTile(
                      key: Key('pay_from_account_${acc.id}'),
                      leading: CircleAvatar(
                        backgroundColor: AppTokens.primaryBlue.withOpacity(0.12),
                        child: const Icon(Icons.account_balance_rounded, color: AppTokens.primaryBlue, size: 20),
                      ),
                      title: Text(
                        acc.institutionCode,
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontWeight: FontWeight.w600,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                      subtitle: Text(
                        acc.maskedNumber,
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 13,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                      ),
                      trailing: isSelected
                          ? const Icon(Icons.check_circle_rounded, color: AppTokens.primaryBlue)
                          : null,
                      onTap: () {
                        _setFundingPreference('ACCOUNT', acc.id);
                        Navigator.of(ctx).pop();
                      },
                    ),
                  );
                }),
                const SizedBox(height: 16),
              ],
              if (cards.isNotEmpty) ...[
                Text(
                  'LINKED CARDS',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 8),
                ...cards.map((c) {
                  final isSelected =
                      _selectedFundingType == 'CARD' && _selectedFundingId == c.id;
                  final isUsable = !c.isFrozen && !c.isExpired;
                  return Material(
                    color: Colors.transparent,
                    child: ListTile(
                      key: Key('pay_from_card_${c.id}'),
                      leading: CircleAvatar(
                        backgroundColor: AppTokens.primaryViolet.withOpacity(0.12),
                        child: const Icon(Icons.credit_card_rounded, color: AppTokens.primaryViolet, size: 20),
                      ),
                      title: Text(
                        '•••• ${c.last4}',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontWeight: FontWeight.w600,
                          color: isUsable
                              ? (isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight)
                              : Colors.grey,
                        ),
                      ),
                      subtitle: Text(
                        isUsable ? 'Daily limit: LKR ${c.dailyLimit.toStringAsFixed(2)}' : (c.isFrozen ? 'Card Frozen' : 'Expired'),
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 13,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                      ),
                      trailing: isSelected
                          ? const Icon(Icons.check_circle_rounded, color: AppTokens.primaryViolet)
                          : null,
                      onTap: isUsable
                          ? () {
                              _setFundingPreference('CARD', c.id);
                              Navigator.of(ctx).pop();
                            }
                          : null,
                    ),
                  );
                }),
              ],
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final flags = ref.watch(featureFlagsProvider);
    final billsEnabled = flags[FeatureFlagKeys.bills] ?? false;
    final scheduledEnabled = flags[FeatureFlagKeys.scheduled] ?? false;
    final splitEnabled = flags[FeatureFlagKeys.split] ?? false;

    // Accounts & Cards for funding source choice
    final accounts = ref.watch(bankAccountsProvider).asData?.value ?? [];
    final cards = ref.watch(cardsProvider).asData?.value ?? [];

    // Derive active funding source label
    String fundingLabel = 'Select payment source';
    IconData fundingIcon = Icons.account_balance_wallet_rounded;
    if (_selectedFundingType == 'CARD' && _selectedFundingId != null && cards.any((c) => c.id == _selectedFundingId)) {
      final activeCard = cards.firstWhere((c) => c.id == _selectedFundingId);
      fundingLabel = 'Test Card •••• ${activeCard.last4}';
      fundingIcon = Icons.credit_card_rounded;
    } else if (_selectedFundingType == 'ACCOUNT' && _selectedFundingId != null && accounts.any((a) => a.id == _selectedFundingId)) {
      final activeAcc = accounts.firstWhere((a) => a.id == _selectedFundingId);
      fundingLabel = '${activeAcc.institutionCode} (${activeAcc.maskedNumber})';
      fundingIcon = Icons.account_balance_rounded;
    } else if (accounts.isNotEmpty) {
      final defaultAcc = accounts.firstWhere((a) => a.isDefault, orElse: () => accounts.first);
      fundingLabel = '${defaultAcc.institutionCode} (${defaultAcc.maskedNumber})';
      fundingIcon = Icons.account_balance_rounded;
    } else if (cards.isNotEmpty) {
      final defaultCard = cards.firstWhere((c) => !c.isFrozen, orElse: () => cards.first);
      fundingLabel = 'Test Card •••• ${defaultCard.last4}';
      fundingIcon = Icons.credit_card_rounded;
    }

    final recentTransfersAsync = ref.watch(recentTransactionsProvider);

    return Scaffold(
      key: const Key('pay_hub_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Pay',
          key: Key('pay_title'),
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontWeight: FontWeight.w800,
            fontSize: 26,
            letterSpacing: -0.5,
          ),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(recentTransactionsProvider);
          ref.invalidate(bankAccountsProvider);
          ref.invalidate(cardsProvider);
          await ref.read(recentTransactionsProvider.future);
        },
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          children: [
            // 1. Funding Source Quick Selector
            if (accounts.isNotEmpty || cards.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: InkWell(
                  key: AppTestKeys.payFundingSourceSelector,
                  onTap: () => _showFundingSourceSheet(context, accounts, cards),
                  borderRadius: BorderRadius.circular(16),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: isDark ? AppTokens.cardDark : Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                        width: 0.8,
                      ),
                      boxShadow: isDark ? [] : AppTokens.softShadowLight,
                    ),
                    child: Row(
                      children: [
                        Icon(fundingIcon, size: 20, color: AppTokens.primaryViolet),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Paying from',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 11,
                                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                              Text(
                                fundingLabel,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTokens.primaryViolet.withOpacity(0.08),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Text(
                            'Change',
                            style: TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: AppTokens.primaryViolet,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),

            // 2. Search & Recipient Lookup Field [pay_search]
            Container(
              decoration: BoxDecoration(
                color: isDark ? AppTokens.cardDark : Colors.white,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                  width: 0.8,
                ),
                boxShadow: isDark ? [] : AppTokens.softShadowLight,
              ),
              child: TextField(
                key: AppTestKeys.paySearch,
                controller: _searchController,
                focusNode: _searchFocusNode,
                textInputAction: TextInputAction.search,
                onChanged: _onSearchChanged,
                onSubmitted: (_) => _performRecipientLookup(),
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
                decoration: InputDecoration(
                  hintText: 'Enter mobile (07...) or account number',
                  hintStyle: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                  prefixIcon: const Icon(Icons.search_rounded, size: 22, color: AppTokens.primaryViolet),
                  suffixIcon: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (_isSearching)
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 8.0),
                          child: SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        )
                      else if (_searchController.text.isNotEmpty)
                        IconButton(
                          icon: const Icon(Icons.close_rounded, size: 18),
                          tooltip: 'Clear',
                          onPressed: () {
                            _searchController.clear();
                            setState(() => _searchError = null);
                          },
                        ),
                      IconButton(
                        key: const Key('pay_lookup_btn'),
                        icon: const Icon(Icons.arrow_forward_rounded, color: AppTokens.primaryViolet),
                        tooltip: 'Lookup',
                        onPressed: _performRecipientLookup,
                      ),
                    ],
                  ),
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                ),
              ),
            ),

            // Search Error / Cooldown Feedback
            if (_cooldownSeconds > 0)
              Padding(
                padding: const EdgeInsets.only(top: 8, left: 4),
                child: Text(
                  'Too many lookup attempts. Cooldown: ${_cooldownSeconds}s',
                  style: const TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    color: Color(0xFFF59E0B),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              )
            else if (_searchError != null)
              Padding(
                padding: const EdgeInsets.only(top: 8, left: 4),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline_rounded, size: 16, color: Colors.redAccent),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        _searchError!,
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 12,
                          color: Colors.redAccent,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

            const SizedBox(height: 18),

            // 3. Row of Recent Recipients (initials avatars)
            _buildRecentRecipientsSection(recentTransfersAsync, isDark),

            const SizedBox(height: 20),

            // 4. 2-Column Grid of ProductTiles
            Text(
              'PAYMENT METHODS & TOOLS',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                letterSpacing: 0.5,
              ),
            ),
            const SizedBox(height: 10),
            _buildProductTilesGrid(
              context: context,
              isDark: isDark,
              billsEnabled: billsEnabled,
              scheduledEnabled: scheduledEnabled,
              splitEnabled: splitEnabled,
            ),

            const SizedBox(height: 24),

            // 5. Recent Payments List
            SectionHeader(
              title: 'RECENT PAYMENTS',
              actionLabel: 'See all',
              actionKey: AppTestKeys.paySeeAll,
              onActionTap: () => context.push('/history'),
            ),
            const SizedBox(height: 8),
            _buildRecentPaymentsList(recentTransfersAsync, isDark),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Recent Recipients Row
  // ---------------------------------------------------------------------------
  Widget _buildRecentRecipientsSection(
    AsyncValue<List<TransferModel>> recentTransfersAsync,
    bool isDark,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'RECENT RECIPIENTS',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
            letterSpacing: 0.5,
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 86,
          child: recentTransfersAsync.hasError
              ? ListView(
                  scrollDirection: Axis.horizontal,
                  children: [
                    _buildNewRecipientButton(isDark),
                  ],
                )
              : recentTransfersAsync.when(
            loading: () => ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: 5,
              separatorBuilder: (context, index) => const SizedBox(width: 16),
              itemBuilder: (context, index) => const ShimmerWrapper(
                child: SkeletonBox(
                  width: 50,
                  height: 50,
                  borderRadius: BorderRadius.all(Radius.circular(25)),
                ),
              ),
            ),
            error: (error, stack) => ListView(
              scrollDirection: Axis.horizontal,
              children: [
                _buildNewRecipientButton(isDark),
              ],
            ),
            data: (transfers) {
              final seen = <String>{};
              final distinctRecipients = <TransferModel>[];
              for (final t in transfers) {
                final name = t.receiverDisplayName?.trim();
                final phone = t.receiverPhone?.trim();
                final id = t.receiverId?.toString() ?? phone ?? name ?? '';
                if (id.isNotEmpty && !seen.contains(id)) {
                  seen.add(id);
                  distinctRecipients.add(t);
                }
              }

              if (distinctRecipients.isEmpty) {
                return ListView(
                  scrollDirection: Axis.horizontal,
                  children: [
                    _buildNewRecipientButton(isDark),
                  ],
                );
              }

              return ListView.builder(
                scrollDirection: Axis.horizontal,
                itemCount: 1 + distinctRecipients.length,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return Padding(
                      padding: const EdgeInsets.only(right: 14.0),
                      child: _buildNewRecipientButton(isDark),
                    );
                  }

                  final recipient = distinctRecipients[index - 1];
                  final rawName = recipient.receiverDisplayName ?? 'Recipient';
                  final firstName = rawName.trim().split(RegExp(r'\s+')).first;
                  final recipientKey = Key('recipient_${recipient.receiverId ?? recipient.transferId}');

                  return Padding(
                    padding: const EdgeInsets.only(right: 14.0),
                    child: InkWell(
                      key: recipientKey,
                      onTap: () {
                        HapticFeedback.lightImpact();
                        context.push('/send', extra: recipient);
                      },
                      borderRadius: BorderRadius.circular(28),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          AppAvatar(name: rawName, size: 50),
                          const SizedBox(height: 6),
                          SizedBox(
                            width: 58,
                            child: Text(
                              firstName,
                              textAlign: TextAlign.center,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildNewRecipientButton(bool isDark) {
    return InkWell(
      key: const Key('pay_new_recipient'),
      onTap: () => _searchFocusNode.requestFocus(),
      borderRadius: BorderRadius.circular(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppTokens.primaryViolet.withOpacity(0.12),
              border: Border.all(
                color: AppTokens.primaryViolet.withOpacity(0.3),
                width: 1.5,
              ),
            ),
            child: const Icon(Icons.add, color: AppTokens.primaryViolet, size: 24),
          ),
          const SizedBox(height: 6),
          Text(
            'New',
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
            ),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 2-Column ProductTiles Grid
  // ---------------------------------------------------------------------------
  Widget _buildProductTilesGrid({
    required BuildContext context,
    required bool isDark,
    required bool billsEnabled,
    required bool scheduledEnabled,
    required bool splitEnabled,
  }) {
    final tiles = [
      // 1. Send by phone number [pay_send]
      ProductTile(
        key: AppTestKeys.paySend,
        title: 'Send by phone',
        subtitle: 'Instant P2P',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AppTokens.primaryBlue.withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.swap_horiz_rounded, color: AppTokens.primaryBlue, size: 22),
        ),
        onTap: () => context.push('/send'),
      ),

      // 2. Scan or show QR [pay_qr]
      ProductTile(
        key: AppTestKeys.payQr,
        title: 'Scan or show QR',
        subtitle: 'LankaQR pay & receive',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AppTokens.primaryViolet.withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.qr_code_scanner_rounded, color: AppTokens.primaryViolet, size: 22),
        ),
        onTap: () => context.push('/qr'),
      ),

      // 3. Pay bills [pay_bills] (flagged)
      ProductTile(
        key: AppTestKeys.payBills,
        title: 'Pay bills',
        subtitle: 'Utilities & Telco',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xFFF59E0B).withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.receipt_long_rounded, color: Color(0xFFF59E0B), size: 22),
        ),
        onTap: () {
          if (billsEnabled) {
            context.push('/bills');
          } else {
            _showComingSoon(context, 'Pay Bills');
          }
        },
      ),

      // 4. Scheduled payments [pay_scheduled] (flagged)
      ProductTile(
        key: AppTestKeys.payScheduled,
        title: 'Scheduled payments',
        subtitle: 'Standing orders',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xFF6366F1).withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.schedule_rounded, color: Color(0xFF6366F1), size: 22),
        ),
        onTap: () => context.push('/scheduled'),
      ),

      // 5. Split a bill [pay_split] (flagged)
      ProductTile(
        key: AppTestKeys.paySplit,
        title: 'Split a bill',
        subtitle: 'Share with friends',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xFF10B981).withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.call_split_rounded, color: Color(0xFF10B981), size: 22),
        ),
        onTap: () {
          if (splitEnabled) {
            context.push('/split/create');
          } else {
            _showComingSoon(context, 'Split a Bill');
          }
        },
      ),

      // 6. Request money [pay_request]
      ProductTile(
        key: AppTestKeys.payRequest,
        title: 'Request money',
        subtitle: 'Payment request QR',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xFFEC4899).withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.request_page_rounded, color: Color(0xFFEC4899), size: 22),
        ),
        onTap: () => context.push('/qr'),
      ),

      // 7. Between my accounts [pay_between]
      ProductTile(
        key: AppTestKeys.payBetween,
        title: 'Between accounts',
        subtitle: 'Own accounts',
        height: 124,
        illustration: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xFF3B82F6).withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.account_balance_rounded, color: Color(0xFF3B82F6), size: 22),
        ),
        onTap: () => _showBetweenAccountsNotAvailable(context),
      ),
    ];

    // Render as 2-column grid pairs
    final rows = <Widget>[];
    for (int i = 0; i < tiles.length; i += 2) {
      final first = tiles[i];
      final second = (i + 1 < tiles.length) ? tiles[i + 1] : null;

      rows.add(
        Row(
          children: [
            Expanded(child: first),
            const SizedBox(width: 12),
            if (second != null)
              Expanded(child: second)
            else
              const Spacer(),
          ],
        ),
      );
      if (i + 2 < tiles.length || second == null) {
        rows.add(const SizedBox(height: 12));
      }
    }

    return Column(children: rows);
  }

  // ---------------------------------------------------------------------------
  // Recent Payments List
  // ---------------------------------------------------------------------------
  Widget _buildRecentPaymentsList(
    AsyncValue<List<TransferModel>> recentTransfersAsync,
    bool isDark,
  ) {
    if (recentTransfersAsync.hasError) {
      return Container(
        key: AppTestKeys.payRecentError,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isDark ? AppTokens.cardDark : Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.redAccent.withOpacity(0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline_rounded, color: Colors.redAccent),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Could not load recent payments.',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
              ),
            ),
            TextButton(
              onPressed: () => ref.refresh(recentTransactionsProvider),
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    return recentTransfersAsync.when(
      loading: () => Column(
        children: List.generate(
          3,
          (index) => const Padding(
            padding: EdgeInsets.only(bottom: 8.0),
            child: SkeletonTransactionTile(),
          ),
        ),
      ),
      error: (err, _) => const SizedBox.shrink(),
      data: (transfers) {
        if (transfers.isEmpty) {
          return Container(
            key: AppTestKeys.payRecentEmpty,
            padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                width: 0.8,
              ),
            ),
            child: Column(
              children: [
                Icon(
                  Icons.receipt_long_outlined,
                  size: 36,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
                const SizedBox(height: 8),
                Text(
                  'No recent payments',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Your recent payment transactions will appear here.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
              ],
            ),
          );
        }

        return Container(
          key: AppTestKeys.payRecentPayments,
          decoration: BoxDecoration(
            color: isDark ? AppTokens.cardDark : Colors.white,
            borderRadius: BorderRadius.circular(20),
            boxShadow: isDark ? [] : AppTokens.softShadowLight,
            border: Border.all(
              color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
              width: 0.8,
            ),
          ),
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: transfers.length,
            separatorBuilder: (context, index) => Divider(
              height: 1,
              color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
            ),
            itemBuilder: (context, index) {
              final tx = transfers[index];
              return _buildPaymentTile(context, tx, isDark);
            },
          ),
        );
      },
    );
  }

  Widget _buildPaymentTile(BuildContext context, TransferModel tx, bool isDark) {
    final statusBadge = _getStatusBadge(tx.status);
    final receiverName = tx.receiverDisplayName ?? 'LankaPay Transfer';

    return Material(
      color: Colors.transparent,
      child: ListTile(
        key: Key('pay_tx_${tx.transferId}'),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        leading: CircleAvatar(
          backgroundColor: AppTokens.primaryViolet.withOpacity(0.12),
          child: const Icon(Icons.arrow_upward_rounded, color: AppTokens.primaryViolet, size: 20),
        ),
        title: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Expanded(
              child: Text(
                receiverName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'LKR ${tx.amount.toStringAsFixed(2)}',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
          ],
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                tx.createdAt != null && tx.createdAt!.length >= 10
                    ? tx.createdAt!.substring(0, 10)
                    : 'Recent',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 12,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              statusBadge,
            ],
          ),
        ),
        onTap: () => context.push('/history'),
      ),
    );
  }

  Widget _getStatusBadge(TransferStatusType status) {
    final String label;
    final Color color;

    switch (status) {
      case TransferStatusType.completed:
        label = 'Completed';
        color = const Color(0xFF10B981);
        break;
      case TransferStatusType.pending:
      case TransferStatusType.processing:
      case TransferStatusType.requiresReconciliation:
      case TransferStatusType.reversalPending:
        label = 'Pending';
        color = const Color(0xFFF59E0B);
        break;
      case TransferStatusType.failed:
        label = 'Failed';
        color = const Color(0xFFEF4444);
        break;
      case TransferStatusType.reversed:
        label = 'Reversed';
        color = const Color(0xFF8B5CF6);
        break;
      default:
        label = 'Completed';
        color = const Color(0xFF10B981);
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontFamily: AppTokens.fontFamily,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: color,
        ),
      ),
    );
  }
}

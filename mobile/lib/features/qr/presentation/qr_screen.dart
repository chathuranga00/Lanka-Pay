import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../data/qr_service.dart';
import '../domain/qr_models.dart';
import 'qr_scanner_screen.dart';

class QrScreen extends ConsumerStatefulWidget {
  const QrScreen({super.key});

  @override
  ConsumerState<QrScreen> createState() => _QrScreenState();
}

class _QrScreenState extends ConsumerState<QrScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Static QR state
  QrReferenceModel? _staticQr;
  bool _isLoadingStatic = false;
  String? _staticError;

  // Dynamic Payment Request state
  final _amountController = TextEditingController();
  final _noteController = TextEditingController();
  final int _validityMinutes = 15;
  QrReferenceModel? _dynamicQr;
  bool _isGeneratingDynamic = false;
  String? _dynamicError;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadStaticQr();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _amountController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  Future<void> _loadStaticQr() async {
    setState(() {
      _isLoadingStatic = true;
      _staticError = null;
    });

    try {
      final qr = await ref.read(qrServiceProvider).generateStaticQr();
      if (mounted) {
        setState(() {
          _staticQr = qr;
          _isLoadingStatic = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoadingStatic = false;
          _staticError = 'Could not generate personal QR. Please check your connection.';
        });
      }
    }
  }

  Future<void> _generateDynamicQr() async {
    final amountText = _amountController.text.trim();
    final amount = double.tryParse(amountText);
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter an amount greater than 0')),
      );
      return;
    }
    HapticFeedback.lightImpact();

    setState(() {
      _isGeneratingDynamic = true;
      _dynamicError = null;
    });

    try {
      final qr = await ref.read(qrServiceProvider).generateDynamicQr(
            amount: amount,
            validityMinutes: _validityMinutes,
            note: _noteController.text.trim(),
          );
      if (mounted) {
        setState(() {
          _dynamicQr = qr;
          _isGeneratingDynamic = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isGeneratingDynamic = false;
          _dynamicError = 'Failed to generate dynamic QR. Please try again.';
        });
      }
    }
  }

  void _openScanner() async {
    HapticFeedback.lightImpact();
    final result = await Navigator.of(context).push<ResolvedQrModel>(
      MaterialPageRoute(builder: (_) => const QrScannerScreen()),
    );

    if (result != null && mounted) {
      context.push('/send', extra: result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GradientHeaderScaffold(
      scrollable: false,
      bodyPadding: EdgeInsets.zero,
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('qr_scan_fab'),
        onPressed: _openScanner,
        icon: const Icon(Icons.qr_code_scanner_rounded),
        label: const Text(
          'Scan & Pay',
          style: TextStyle(fontFamily: AppTokens.fontFamily, fontWeight: FontWeight.w700),
        ),
        backgroundColor: AppTokens.primaryViolet,
        foregroundColor: Colors.white,
      ),
      header: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SimulatedBanner(),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  if (Navigator.of(context).canPop())
                    IconButton(
                      icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                      tooltip: 'Back',
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        Navigator.of(context).pop();
                      },
                    ),
                  Text(
                    'LANKAQR',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                ],
              ),
              IconButton(
                key: const Key('qr_open_scanner_btn'),
                icon: const Icon(Icons.qr_code_scanner_rounded),
                tooltip: 'Scan QR',
                onPressed: _openScanner,
              ),
            ],
          ),
          const SizedBox(height: 12),
          // Segmented Tabs Pill
          Container(
            height: 44,
            decoration: BoxDecoration(
              color: isDark
                  ? AppTokens.mutedSurfaceDark.withOpacity(0.6)
                  : Colors.white.withOpacity(0.5),
              borderRadius: BorderRadius.circular(14),
            ),
            padding: const EdgeInsets.all(4),
            child: TabBar(
              controller: _tabController,
              indicator: BoxDecoration(
                color: isDark ? AppTokens.cardDark : Colors.white,
                borderRadius: BorderRadius.circular(10),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.08),
                    blurRadius: 4,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              labelColor: isDark ? Colors.white : AppTokens.textPrimaryLight,
              unselectedLabelColor:
                  isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              labelStyle: const TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 13,
                fontWeight: FontWeight.w700,
              ),
              tabs: const [
                Tab(text: 'My QR (Static)'),
                Tab(text: 'Request (Dynamic)'),
              ],
            ),
          ),
        ],
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildStaticQrTab(isDark),
          _buildDynamicRequestTab(isDark),
        ],
      ),
    );
  }

  Widget _buildStaticQrTab(bool isDark) {
    if (_isLoadingStatic) {
      return const Center(
        child: CircularProgressIndicator(color: AppTokens.primaryViolet),
      );
    }

    if (_staticError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline_rounded, color: AppTokens.error, size: 48),
              const SizedBox(height: 12),
              Text(_staticError!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _loadStaticQr,
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    final qrData = _staticQr?.qrPayload ?? 'LANKAPAY:QR:STATIC_PLACEHOLDER';

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 16,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: QrImageView(
                    data: qrData,
                    version: QrVersions.auto,
                    size: 220,
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  _staticQr?.recipientDisplayName ?? 'LankaPay Verified Payee',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                    color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Scan to transfer funds instantly',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Privacy safe banner
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
              ),
            ),
            child: Row(
              children: [
                const Icon(Icons.shield_outlined, color: AppTokens.primaryViolet, size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Opaque Privacy Reference (No PII)',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Your bank account number is encrypted inside this QR and never shown in plain text.',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 12,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          height: 1.3,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 80),
        ],
      ),
    );
  }

  Widget _buildDynamicRequestTab(bool isDark) {
    if (_dynamicQr != null) {
      return SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: isDark ? AppTokens.cardDark : Colors.white,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.04),
                    blurRadius: 16,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Text(
                    'Payment Request',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'LKR ${_amountController.text.trim()}',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                    ),
                  ),
                  if (_dynamicQr!.note != null && _dynamicQr!.note!.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      _dynamicQr!.note!,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 13,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                  ],
                  const SizedBox(height: 20),
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: QrImageView(
                      data: _dynamicQr!.qrPayload,
                      version: QrVersions.auto,
                      size: 220.0,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.timer_outlined, size: 16, color: AppTokens.warning),
                      const SizedBox(width: 6),
                      Text(
                        'Expires in $_validityMinutes minutes',
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: AppTokens.warning,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
            OutlinedButton.icon(
              onPressed: () {
                setState(() {
                  _dynamicQr = null;
                  _amountController.clear();
                  _noteController.clear();
                });
              },
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Create New Request'),
            ),
            const SizedBox(height: 80),
          ],
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Create Dynamic Payment QR',
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 17,
              fontWeight: FontWeight.w800,
              color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Generate a one-time dynamic QR code with a pre-set amount.',
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 13,
              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
            ),
          ),
          const SizedBox(height: 20),

          TextField(
            key: const Key('qr_amount_field'),
            controller: _amountController,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Requested Amount (LKR)',
              hintText: 'e.g. 2500.00',
              prefixIcon: Icon(Icons.currency_exchange_rounded),
            ),
          ),
          const SizedBox(height: 16),

          TextField(
            key: const Key('qr_note_field'),
            controller: _noteController,
            decoration: const InputDecoration(
              labelText: 'Note / Reason (Optional)',
              hintText: 'e.g. Dinner bill, Grocery share',
              prefixIcon: Icon(Icons.notes_rounded),
            ),
          ),
          const SizedBox(height: 24),

          ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 52),
            child: ElevatedButton.icon(
              key: const Key('qr_generate_dynamic_btn'),
              onPressed: _isGeneratingDynamic ? null : _generateDynamicQr,
              icon: _isGeneratingDynamic
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.qr_code_2_rounded),
              label: const Text(
                'Generate Dynamic QR',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),

          if (_dynamicError != null) ...[
            const SizedBox(height: 16),
            Text(
              _dynamicError!,
              style: const TextStyle(
                fontFamily: AppTokens.fontFamily,
                color: AppTokens.error,
                fontSize: 13,
              ),
              textAlign: TextAlign.center,
            ),
          ],
          const SizedBox(height: 80),
        ],
      ),
    );
  }
}

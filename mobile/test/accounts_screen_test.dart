import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/widgets/account_card.dart';
import 'package:lankapay_id/core/widgets/simulated_badge.dart';
import 'package:lankapay_id/core/widgets/transaction_tile.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/accounts/presentation/accounts_screen.dart';
import 'package:lankapay_id/features/history/data/history_service.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

class FakeBankAccountService implements BankAccountService {
  List<BankAccountModel> accounts;
  bool shouldThrowOnPatch;
  bool shouldThrowOnList;
  int? lastPatchedId;
  String? lastPatchedLabel;
  bool? lastPatchedDiscoverable;
  bool? lastPatchedReceiving;
  int? lastSetDefaultId;
  int? lastRemovedId;

  FakeBankAccountService({
    List<BankAccountModel>? initialAccounts,
    this.shouldThrowOnPatch = false,
    this.shouldThrowOnList = false,
  }) : accounts = initialAccounts ?? [
          const BankAccountModel(
            id: 1,
            institutionCode: 'BOC',
            accountName: 'Kamal Perera',
            maskedNumber: '******7890',
            label: 'Salary Account',
            isDefault: true,
            discoverable: true,
            receivingEnabled: true,
            status: 'ACTIVE',
          ),
          const BankAccountModel(
            id: 2,
            institutionCode: 'COMB',
            accountName: 'Kamal Perera',
            maskedNumber: '******4321',
            label: 'Savings Account',
            isDefault: false,
            discoverable: false,
            receivingEnabled: false,
            status: 'ACTIVE',
          ),
        ];

  @override
  Future<List<BankAccountModel>> listAccounts() async {
    if (shouldThrowOnList) {
      throw Exception('Network error loading accounts');
    }
    return List.from(accounts);
  }

  @override
  Future<List<BankInstitutionModel>> listInstitutions() async => [];

  @override
  Future<BankAccountModel> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
    bool consentGiven = true,
  }) async {
    final newAccount = BankAccountModel(
      id: accounts.length + 1,
      institutionCode: institutionCode,
      accountName: accountName,
      maskedNumber: '******${accountNumber.substring(accountNumber.length - 4)}',
      label: label,
      status: 'ACTIVE',
      isDefault: false,
      discoverable: true,
      receivingEnabled: true,
    );
    accounts.add(newAccount);
    return newAccount;
  }

  @override
  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async {
    if (shouldThrowOnPatch) {
      throw Exception('Server rejected patch');
    }
    lastPatchedId = id;
    lastPatchedLabel = label;
    lastPatchedDiscoverable = discoverable;
    lastPatchedReceiving = receivingEnabled;

    final index = accounts.indexWhere((a) => a.id == id);
    if (index == -1) throw Exception('Account not found');

    final updated = accounts[index].copyWith(
      label: label ?? accounts[index].label,
      discoverable: discoverable ?? accounts[index].discoverable,
      receivingEnabled: receivingEnabled ?? accounts[index].receivingEnabled,
    );
    accounts[index] = updated;
    return updated;
  }

  @override
  Future<BankAccountModel> setDefault(int id) async {
    lastSetDefaultId = id;
    for (int i = 0; i < accounts.length; i++) {
      accounts[i] = accounts[i].copyWith(isDefault: accounts[i].id == id);
    }
    return accounts.firstWhere((a) => a.id == id);
  }

  @override
  Future<void> removeAccount(int id, {bool consentGiven = true}) async {
    lastRemovedId = id;
    accounts.removeWhere((a) => a.id == id);
  }
}

class FakeHistoryService implements HistoryService {
  final List<TransferModel> transfers;

  FakeHistoryService({this.transfers = const []});

  @override
  Future<List<TransferModel>> listHistory({
    HistoryFilter? filter,
    int page = 0,
    int size = 20,
  }) async {
    return transfers;
  }

  @override
  Future<ReceiptModel> getReceipt(int transferId) async {
    return ReceiptModel(
      receiptNumber: 'REC-001',
      transferId: transferId,
      reference: 'TX-REF-001',
      status: 'COMPLETED',
      amount: 1500.0,
      totalDebited: 1500.0,
    );
  }
}

Widget createAccountsScreenTestWidget({
  required FakeBankAccountService bankService,
  FakeHistoryService? historyService,
  double? simulatedBalance,
  ThemeData? theme,
}) {
  return ProviderScope(
    overrides: [
      bankAccountServiceProvider.overrideWithValue(bankService),
      historyServiceProvider.overrideWithValue(historyService ?? FakeHistoryService()),
      simulatedBalanceProvider.overrideWithValue(simulatedBalance),
    ],
    child: MaterialApp(
      theme: theme ?? AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: const AccountsScreen(),
    ),
  );
}

void main() {
  group('AccountsScreen Header & Carousel Visuals', () {
    testWidgets('renders Accounts header, + button [accounts_add], Receiving count and SimulatedBadge', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      expect(find.text('Accounts'), findsOneWidget);
      expect(find.byKey(const Key('accounts_add')), findsOneWidget);
      expect(find.text('Receiving'), findsOneWidget);
      expect(find.byType(SimulatedBadge), findsWidgets);
      // Active receiving count is 1 (Account 1 has receivingEnabled: true, Account 2 has receivingEnabled: false)
      expect(find.text('1'), findsOneWidget);

      // AccountCard checks
      expect(find.byType(AccountCard), findsWidgets);
      expect(find.text('BOC (SIMULATED)'), findsOneWidget);
      expect(find.text('Salary Account'), findsOneWidget);
      expect(find.text('DEFAULT'), findsOneWidget);
      expect(find.text('DISCOVERABLE'), findsOneWidget);
    });

    testWidgets('renders simulated balance when simulatedBalanceProvider is present', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(
        createAccountsScreenTestWidget(
          bankService: bankService,
          simulatedBalance: 2230.0,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('LKR 2230.00'), findsOneWidget);
    });
  });

  group('AccountsScreen Carousel Selection & Action Buttons', () {
    testWidgets('carousel selection updates ActionButtons and pause status between accounts', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      // Page 0: Account 1 (receivingEnabled: true)
      expect(find.byKey(const Key('btn_add_account')), findsOneWidget);
      expect(find.byKey(const Key('btn_freeze')), findsOneWidget);
      expect(find.text('Pause receiving'), findsOneWidget);
      expect(find.byKey(const Key('btn_account_settings')), findsOneWidget);

      // Swipe PageView to Account 2 (receivingEnabled: false)
      await tester.drag(find.byType(PageView), const Offset(-400, 0));
      await tester.pumpAndSettle();

      // Page 1: Account 2 is paused, ActionButton changes to Resume
      expect(find.text('Resume'), findsOneWidget);
      // Frosted overlay shows "Receiving paused" with snowflake icon
      expect(find.text('Receiving paused'), findsOneWidget);
      expect(find.byIcon(Icons.ac_unit_rounded), findsWidgets);
    });
  });

  group('AccountsScreen Eye Toggle Digit Masking & Lifecycle', () {
    testWidgets('masks last 4 digits by default, reveals on eye tap, and auto-resets on background', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      // Masked number of account 1 is '******7890'.
      // By default, digits are masked with bullets: '******••••'
      expect(find.text('******••••'), findsWidgets);
      expect(find.text('******7890'), findsNothing);

      // Tap eye toggle icon [account_toggle_visibility]
      await tester.tap(find.byKey(const Key('account_toggle_visibility')).first);
      await tester.pumpAndSettle();

      // Now revealed:
      expect(find.text('******7890'), findsOneWidget);
      expect(find.text('******4321'), findsOneWidget);
      expect(find.text('******••••'), findsNothing);

      // Trigger app goes to background and comes back to foreground
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      // Must be automatically reset back to hidden/masked:
      expect(find.text('******••••'), findsWidgets);
      expect(find.text('******7890'), findsNothing);
      expect(find.text('******4321'), findsNothing);
    });
  });

  group('AccountsScreen Pause/Resume API & Optimistic Rollback', () {
    testWidgets('pausing calls API with receivingEnabled: false after confirmation', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      // Tap Freeze / Pause receiving button
      await tester.tap(find.byKey(const Key('btn_freeze')));
      await tester.pumpAndSettle();

      // Confirm dialog appears
      expect(find.text('Pause Receiving?'), findsOneWidget);
      expect(find.byKey(const Key('confirm_pause_btn')), findsOneWidget);

      // Confirm pause
      await tester.tap(find.byKey(const Key('confirm_pause_btn')));
      await tester.pumpAndSettle();

      expect(bankService.lastPatchedId, 1);
      expect(bankService.lastPatchedReceiving, false);
      expect(find.text('Receiving paused for this account'), findsOneWidget);
      expect(find.text('Resume'), findsOneWidget);
    });

    testWidgets('reverts optimistic update and displays error message if patch call fails', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService(shouldThrowOnPatch: true);
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      expect(find.text('Pause receiving'), findsOneWidget);

      await tester.tap(find.byKey(const Key('btn_freeze')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('confirm_pause_btn')));
      await tester.pumpAndSettle();

      // Reverts back to Pause receiving (rolled back) and shows error SnackBar
      expect(find.text('Pause receiving'), findsOneWidget);
      expect(find.textContaining('Failed to update receiving'), findsOneWidget);
    });
  });

  group('AccountsScreen Settings Bottom Sheet Features', () {
    testWidgets('Settings sheet allows rename, set default, reverify, and remove with confirmation', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      // Swipe to Account 2
      await tester.drag(find.byType(PageView), const Offset(-400, 0));
      await tester.pumpAndSettle();

      // Open Settings
      await tester.tap(find.byKey(const Key('btn_account_settings')));
      await tester.pumpAndSettle();

      // Check sheet content
      expect(find.text('Verification Status'), findsOneWidget);
      expect(find.byKey(const Key('reverify_account_btn')), findsOneWidget);
      expect(find.byKey(const Key('settings_rename_btn')), findsOneWidget);
      expect(find.byKey(const Key('settings_set_default_btn')), findsOneWidget);
      expect(find.byKey(const Key('settings_toggle_discoverable')), findsOneWidget);
      expect(find.byKey(const Key('settings_toggle_receiving')), findsOneWidget);
      expect(find.byKey(const Key('settings_remove_btn')), findsOneWidget);

      // 1. Re-verify
      await tester.tap(find.byKey(const Key('reverify_account_btn')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Verification status refreshed: ACTIVE'), findsOneWidget);

      // 2. Set Default
      await tester.tap(find.byKey(const Key('settings_set_default_btn')));
      await tester.pumpAndSettle();
      expect(bankService.lastSetDefaultId, 2);
      expect(find.text('Account set as default'), findsOneWidget);

      // Re-open Settings to test Rename
      await tester.tap(find.byKey(const Key('btn_account_settings')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('settings_rename_btn')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('rename_account_field')), findsOneWidget);
      await tester.enterText(find.byKey(const Key('rename_account_field')), 'My Rainy Day Fund');
      await tester.tap(find.byKey(const Key('rename_account_save_btn')));
      await tester.pumpAndSettle();

      expect(bankService.lastPatchedId, 2);
      expect(bankService.lastPatchedLabel, 'My Rainy Day Fund');
      expect(find.text('Account renamed successfully'), findsOneWidget);

      // Re-open Settings to test Remove with confirmation dialog
      await tester.tap(find.byKey(const Key('btn_account_settings')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('settings_remove_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Remove Bank Account?'), findsOneWidget);
      expect(find.byKey(const Key('confirm_remove_account_btn')), findsOneWidget);

      await tester.tap(find.byKey(const Key('confirm_remove_account_btn')));
      await tester.pumpAndSettle();

      expect(bankService.lastRemovedId, 2);
      expect(find.text('Account removed successfully'), findsOneWidget);
    });

    testWidgets('toggles discoverable and receiving switches in settings sheet', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService();
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('btn_account_settings')));
      await tester.pumpAndSettle();

      // Toggle Discoverable
      await tester.tap(find.byKey(const Key('settings_toggle_discoverable')));
      await tester.pumpAndSettle();
      expect(bankService.lastPatchedDiscoverable, false);

      // Toggle Receiving
      await tester.tap(find.byKey(const Key('settings_toggle_receiving')));
      await tester.pumpAndSettle();
      expect(bankService.lastPatchedReceiving, false);
    });
  });

  group('AccountsScreen Recent Activity & Empty States', () {
    testWidgets('renders filtered TransactionTiles for selected account and empty state when none', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final testTransfers = [
        const TransferModel(
          transferId: 101,
          reference: 'TX-101',
          status: TransferStatusType.completed,
          amount: 5000.0,
          receiverDisplayName: 'Kamal Perera',
          senderAccountId: 1,
          createdAt: 'Today, 10:30',
        ),
      ];
      final bankService = FakeBankAccountService();
      final historyService = FakeHistoryService(transfers: testTransfers);

      await tester.pumpWidget(
        createAccountsScreenTestWidget(
          bankService: bankService,
          historyService: historyService,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Recent activity'), findsOneWidget);
      expect(find.byType(TransactionTile), findsOneWidget);
      expect(find.text('+LKR 5000.00'), findsOneWidget);

      // Swipe to Account 2 (has no transactions)
      await tester.drag(find.byType(PageView), const Offset(-400, 0));
      await tester.pumpAndSettle();

      // No transaction yet EmptyState matching video screenshot
      expect(find.byKey(const Key('accounts_no_transactions')), findsOneWidget);
      expect(find.text('No transaction yet'), findsOneWidget);
    });

    testWidgets('renders illustration-free empty state with Link your first account button when 0 accounts', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService(initialAccounts: []);
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pumpAndSettle();

      expect(find.text('No Bank Accounts Linked'), findsOneWidget);
      expect(find.byKey(const Key('empty_add_account_btn')), findsOneWidget);
      expect(find.text('Link your first account'), findsOneWidget);
      expect(find.text('0'), findsOneWidget);
    });

    testWidgets('renders ErrorRetry on network failure with functional retry', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeBankAccountService(shouldThrowOnList: true);
      await tester.pumpWidget(createAccountsScreenTestWidget(bankService: bankService));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      expect(find.text('Failed to load linked accounts'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);

      // Fix error and tap Retry
      bankService.shouldThrowOnList = false;
      await tester.tap(find.text('Retry'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      expect(find.text('BOC (SIMULATED)'), findsOneWidget);
    });
  });
}

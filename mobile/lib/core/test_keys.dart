import 'package:flutter/material.dart';

/// Centralized test keys registry for LankaPay ID Mobile Application.
/// Categorized by prefixes: nav_*, home_*, qa_*, btn_*, account_*, send_*,
/// tile_*, more_*, theme_*, and associated screen flows.
abstract final class AppTestKeys {
  AppTestKeys._();

  // ---------------------------------------------------------------------------
  // Navigation (nav_*)
  // ---------------------------------------------------------------------------
  static const Key navHome = Key('nav_home');
  static const Key navAccounts = Key('nav_accounts');
  static const Key navSend = Key('nav_send');
  static const Key navMore = Key('nav_more');

  // ---------------------------------------------------------------------------
  // Home (home_*)
  // ---------------------------------------------------------------------------
  static const Key homeAddAccount = Key('home_add_account');
  static const Key homeAddAccountEmptyBtn = Key('home_add_account_empty_btn');
  static const Key homeBell = Key('home_bell');
  static const Key homeBellUnreadDot = Key('home_bell_unread_dot');
  static const Key homeNotificationsIconBtn = Key('home_notifications_icon_btn');
  static const Key homeToggleBalance = Key('home_toggle_balance');
  static const Key homeSearch = Key('home_search');
  static const Key homeSeeAll = Key('home_see_all');
  static const Key homeViewAllHistoryBtn = Key('home_view_all_history_btn');
  static const Key homeQuickSendBtn = Key('home_quick_send_btn');
  static const Key homeQuickScanBtn = Key('home_quick_scan_btn');
  static const Key homeQuickRequestBtn = Key('home_quick_request_btn');
  static const Key homeAccountsEmpty = Key('home_accounts_empty');
  static const Key homeAccountsError = Key('home_accounts_error');
  static const Key homeTransactionsEmpty = Key('home_transactions_empty');
  static const Key homeTransactionsError = Key('home_transactions_error');
  static const Key homeTransactionsLoading = Key('home_transactions_loading');
  static Key homeAccount(int id) => Key('home_account_$id');
  static Key tx(int id) => Key('tx_$id');

  // ---------------------------------------------------------------------------
  // Quick Actions (qa_*)
  // ---------------------------------------------------------------------------
  static const Key qaSend = Key('qa_send');
  static const Key qaScan = Key('qa_scan');
  static const Key qaRequest = Key('qa_request');
  static const Key qaMyQr = Key('qa_my_qr');

  // ---------------------------------------------------------------------------
  // Action Buttons (btn_*)
  // ---------------------------------------------------------------------------
  static const Key btnAddAccount = Key('btn_add_account');
  static const Key btnFreeze = Key('btn_freeze');
  static const Key btnAccountSettings = Key('btn_account_settings');
  static const Key btnDone = Key('btn_done');
  static const Key btnShare = Key('btn_share');
  static const Key btnRefresh = Key('btn_refresh');
  static const Key btnRetry = Key('btn_retry');

  // ---------------------------------------------------------------------------
  // Accounts (account_*)
  // ---------------------------------------------------------------------------
  static const Key accountsAdd = Key('accounts_add');
  static const Key accountToggleVisibility = Key('account_toggle_visibility');
  static const Key accountsNoTransactions = Key('accounts_no_transactions');
  static const Key confirmPauseBtn = Key('confirm_pause_btn');
  static const Key confirmResumeBtn = Key('confirm_resume_btn');
  static const Key confirmRemoveAccountBtn = Key('confirm_remove_account_btn');
  static const Key renameAccountField = Key('rename_account_field');
  static const Key renameAccountSaveBtn = Key('rename_account_save_btn');
  static const Key reverifyAccountBtn = Key('reverify_account_btn');

  // ---------------------------------------------------------------------------
  // Send (send_*)
  // ---------------------------------------------------------------------------
  static const Key sendScanQr = Key('send_scan_qr');
  static const Key sendSearch = Key('send_search');
  static const Key sendNew = Key('send_new');
  static const Key sendPhoneField = Key('send_phone_field');
  static const Key sendLookupBtn = Key('send_lookup_btn');
  static const Key sendAmountField = Key('send_amount_field');
  static const Key sendNoteField = Key('send_note_field');
  static const Key sendSenderAccountDropdown = Key('send_sender_account_dropdown');
  static const Key sendReviewBtn = Key('send_review_btn');
  static Key recipient(int id) => Key('recipient_$id');

  // ---------------------------------------------------------------------------
  // Tiles (tile_*)
  // ---------------------------------------------------------------------------
  static const Key tilePayByPhone = Key('tile_pay_by_phone');
  static const Key tileScanQr = Key('tile_scan_qr');
  static const Key tileRequests = Key('tile_requests');
  static const Key tileBetweenAccounts = Key('tile_between_accounts');

  // ---------------------------------------------------------------------------
  // More (more_*)
  // ---------------------------------------------------------------------------
  static const Key moreScreen = Key('more_screen');
  static const Key moreProfile = Key('more_profile');
  static const Key moreQr = Key('more_qr');
  static const Key moreRequests = Key('more_requests');
  static const Key moreHistory = Key('more_history');
  static const Key moreNotifications = Key('more_notifications');
  static const Key morePrivacy = Key('more_privacy');
  static const Key moreSecurity = Key('more_security');
  static const Key moreLanguage = Key('more_language');
  static const Key moreHelp = Key('more_help');
  static const Key moreLogout = Key('more_logout');
  static const Key moreLogoutConfirmBtn = Key('more_logout_confirm_btn');
  static const Key profileCloseBtn = Key('profile_close_btn');
  static const Key helpCloseBtn = Key('help_close_btn');

  // ---------------------------------------------------------------------------
  // Theme (theme_*)
  // ---------------------------------------------------------------------------
  static const Key themeToggle = Key('theme_toggle');
  static const Key themeOptionSystem = Key('theme_option_system');
  static const Key themeOptionLight = Key('theme_option_light');
  static const Key themeOptionDark = Key('theme_option_dark');

  // ---------------------------------------------------------------------------
  // Auth & Onboarding
  // ---------------------------------------------------------------------------
  static const Key getStartedBtn = Key('get_started_btn');
  static const Key signInBtn = Key('sign_in_btn');
  static const Key loginPhoneField = Key('login_phone_field');
  static const Key loginPinField = Key('login_pin_field');
  static const Key loginForgotPinBtn = Key('login_forgot_pin_btn');
  static const Key loginSubmitBtn = Key('login_submit_btn');
  static const Key loginGotoRegisterBtn = Key('login_goto_register_btn');
  static const Key registerNameField = Key('register_name_field');
  static const Key registerPhoneField = Key('register_phone_field');
  static const Key registerNicField = Key('register_nic_field');
  static const Key registerPinField = Key('register_pin_field');
  static const Key registerConfirmPinField = Key('register_confirm_pin_field');
  static const Key registerSubmitBtn = Key('register_submit_btn');
  static const Key consentTermsCheckbox = Key('consent_terms_checkbox');
  static const Key consentPrivacyCheckbox = Key('consent_privacy_checkbox');
  static const Key consentContinueBtn = Key('consent_continue_btn');
  static const Key otpInputField = Key('otp_input_field');
  static const Key otpVerifyBtn = Key('otp_verify_btn');
  static const Key otpResendBtn = Key('otp_resend_btn');
  static const Key recoverPhoneField = Key('recover_phone_field');
  static const Key recoverNicField = Key('recover_nic_field');
  static const Key recoverSubmitBtn = Key('recover_submit_btn');

  // ---------------------------------------------------------------------------
  // Add Bank Account
  // ---------------------------------------------------------------------------
  static const Key addBankInstitutionDropdown = Key('add_bank_institution_dropdown');
  static const Key addBankNumberField = Key('add_bank_number_field');
  static const Key addBankNameField = Key('add_bank_name_field');
  static const Key addBankLabelField = Key('add_bank_label_field');
  static const Key addBankConsentCheckbox = Key('add_bank_consent_checkbox');
  static const Key addBankSubmitBtn = Key('add_bank_submit_btn');

  // ---------------------------------------------------------------------------
  // Transfers & PIN Auth
  // ---------------------------------------------------------------------------
  static const Key transferPinInput = Key('transfer_pin_input');
  static const Key transferConfirmPinBtn = Key('transfer_confirm_pin_btn');
  static const Key confirmationContinueBtn = Key('confirmation_continue_btn');
  static const Key transferRefreshBtn = Key('transfer_refresh_btn');
  static const Key transferDoneBtn = Key('transfer_done_btn');

  // ---------------------------------------------------------------------------
  // QR Payments
  // ---------------------------------------------------------------------------
  static const Key qrOpenScannerBtn = Key('qr_open_scanner_btn');
  static const Key qrScanFab = Key('qr_scan_fab');
  static const Key qrAmountField = Key('qr_amount_field');
  static const Key qrNoteField = Key('qr_note_field');
  static const Key qrGenerateDynamicBtn = Key('qr_generate_dynamic_btn');

  // ---------------------------------------------------------------------------
  // History & Receipts
  // ---------------------------------------------------------------------------
  static const Key historyFilterBtn = Key('history_filter_btn');
  static const Key historyRefreshBtn = Key('history_refresh_btn');
  static const Key historyResetFilterBtn = Key('history_reset_filter_btn');
  static const Key historyApplyFiltersBtn = Key('history_apply_filters_btn');
  static const Key filterChipCompleted = Key('filter_chip_completed');
  static const Key filterChipFailed = Key('filter_chip_failed');
  static const Key filterChipReceived = Key('filter_chip_received');
  static const Key filterChipSent = Key('filter_chip_sent');
  static const Key filterMinAmountField = Key('filter_min_amount_field');
  static const Key filterMaxAmountField = Key('filter_max_amount_field');
  static const Key receiptShareBtn = Key('receipt_share_btn');

  // ---------------------------------------------------------------------------
  // Notifications & Preferences
  // ---------------------------------------------------------------------------
  static const Key notifsMarkAllReadBtn = Key('notifs_mark_all_read_btn');
  static const Key prefsPushSwitch = Key('prefs_push_switch');
  static const Key prefsSmsSwitch = Key('prefs_sms_switch');
  static const Key prefsTxAlertsSwitch = Key('prefs_tx_alerts_switch');
  static const Key prefsEmailSwitch = Key('prefs_email_switch');
  static const Key prefsInAppSwitch = Key('prefs_in_app_switch');
  static const Key prefsSecAlertsSwitch = Key('prefs_sec_alerts_switch');

  // ---------------------------------------------------------------------------
  // Settings & Security
  // ---------------------------------------------------------------------------
  static const Key privacyDiscoverableSwitch = Key('privacy_discoverable_switch');
  static const Key privacyFreezeSwitch = Key('privacy_freeze_switch');
  static const Key securityBiometricsSwitch = Key('security_biometrics_switch');
  static const Key securityStepUpSwitch = Key('security_step_up_switch');
  static const Key settingsPrivacyTile = Key('settings_privacy_tile');
  static const Key settingsSecurityTile = Key('settings_security_tile');
  static const Key settingsSignOutTile = Key('settings_sign_out_tile');
  static const Key settingsRenameBtn = Key('settings_rename_btn');
  static const Key settingsSetDefaultBtn = Key('settings_set_default_btn');
  static const Key settingsRemoveBtn = Key('settings_remove_btn');
  static const Key settingsToggleDiscoverable = Key('settings_toggle_discoverable');
  static const Key settingsToggleReceiving = Key('settings_toggle_receiving');
}

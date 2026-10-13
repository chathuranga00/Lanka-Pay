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
  static const Key navPay = Key('nav_pay');
  static const Key navCity = Key('nav_city');
  static const Key navChat = Key('nav_chat');
  static const Key navExplore = Key('nav_explore');
  static const Key navAccounts = Key('nav_accounts');
  static const Key navSend = Key('nav_send');
  static const Key navMore = Key('nav_more');

  // ---------------------------------------------------------------------------
  // Global Search (search_*)
  // ---------------------------------------------------------------------------
  static const Key searchScreen = Key('search_screen');
  static const Key searchInput = Key('search_input');
  static const Key searchClearBtn = Key('search_clear_btn');
  static const Key searchRecentList = Key('search_recent_list');
  static const Key searchClearRecentBtn = Key('search_clear_recent_btn');
  static const Key searchResultsList = Key('search_results_list');
  static const Key searchEmptyState = Key('search_empty_state');
  static const Key searchErrorState = Key('search_error_state');
  static const Key searchLoading = Key('search_loading');

  // ---------------------------------------------------------------------------
  // Avatar Sheet (avatar_sheet_*)
  // ---------------------------------------------------------------------------
  static const Key avatarSheet = Key('avatar_sheet');
  static const Key avatarSheetSettings = Key('avatar_sheet_settings');
  static const Key avatarSheetPrivacy = Key('avatar_sheet_privacy');
  static const Key avatarSheetSecurity = Key('avatar_sheet_security');
  static const Key avatarSheetLanguage = Key('avatar_sheet_language');
  static const Key avatarSheetAppearance = Key('avatar_sheet_appearance');
  static const Key avatarSheetLogout = Key('avatar_sheet_logout');

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
  // Cards (seg_*, cards_*, card_*, btn_card_*, flow_add_card, etc.)
  // ---------------------------------------------------------------------------
  static const Key segAccounts = Key('seg_accounts');
  static const Key segCards = Key('seg_cards');
  static const Key cardsAdd = Key('cards_add');
  static const Key cardToggleVisibility = Key('card_toggle_visibility');
  static const Key btnAddCard = Key('btn_add_card');
  static const Key btnFreezeCard = Key('btn_freeze_card');
  static const Key btnCardDetails = Key('btn_card_details');
  static const Key btnCardSettings = Key('btn_card_settings');
  static const Key flowAddCard = Key('flow_add_card');
  static Key card(dynamic id) => Key('card_$id');
  static const Key confirmFreezeCardBtn = Key('confirm_freeze_card_btn');
  static const Key confirmUnfreezeCardBtn = Key('confirm_unfreeze_card_btn');
  static const Key confirmRemoveCardBtn = Key('confirm_remove_card_btn');
  static const Key stepUpPinInput = Key('step_up_pin_input');
  static const Key stepUpPinConfirmBtn = Key('step_up_pin_confirm_btn');
  static const Key btnChooseTestCard = Key('btn_choose_test_card');
  static const Key btnChooseVirtualCard = Key('btn_choose_virtual_card');
  static const Key inputCardNumber = Key('input_card_number');
  static const Key inputCardExpiry = Key('input_card_expiry');
  static const Key inputCardCvv = Key('input_card_cvv');
  static const Key inputCardHolderName = Key('input_card_holder_name');
  static const Key inputCardNickname = Key('input_card_nickname');
  static const Key checkboxCardConsent = Key('checkbox_card_consent');
  static const Key btnCardFormSubmit = Key('btn_card_form_submit');
  static const Key bannerTestCardNotice = Key('banner_test_card_notice');
  static const Key linkTestCardsScreen = Key('link_test_cards_screen');
  static const Key inputCardOtp = Key('input_card_otp');
  static const Key btnCardOtpVerify = Key('btn_card_otp_verify');
  static const Key btnCardOtpResend = Key('btn_card_otp_resend');
  static const Key otpCooldownText = Key('otp_cooldown_text');
  static const Key otpAttemptsText = Key('otp_attempts_text');
  static const Key cardOtpSuccess = Key('card_otp_success');
  static const Key btnCardOtpDone = Key('btn_card_otp_done');
  static const Key cardRevealSheet = Key('card_reveal_sheet');
  static const Key revealCountdownTimer = Key('reveal_countdown_timer');
  static const Key cardSettingsSheet = Key('card_settings_sheet');
  static const Key cardSettingsNicknameField = Key('card_settings_nickname_field');
  static const Key cardSettingsSaveNicknameBtn = Key('card_settings_save_nickname_btn');
  static const Key cardSettingsPerPaymentLimitField = Key('card_settings_per_payment_limit_field');
  static const Key cardSettingsDailyLimitField = Key('card_settings_daily_limit_field');
  static const Key cardSettingsSaveLimitsBtn = Key('card_settings_save_limits_btn');
  static const Key cardSettingsOnlineSwitch = Key('card_settings_online_switch');
  static const Key cardSettingsBackingAccountDropdown = Key('card_settings_backing_account_dropdown');
  static const Key cardSettingsRemoveBtn = Key('card_settings_remove_btn');
  static const Key emptyAddCardBtn = Key('empty_add_card_btn');

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
  // Card Funding (pay_from_*, card_challenge_*)
  // ---------------------------------------------------------------------------
  static const Key payFromSelector = Key('pay_from_selector');
  static const Key cardChallengeOtp = Key('card_challenge_otp');
  static const Key inputChallengeOtp = Key('input_challenge_otp');
  static const Key btnSubmitChallengeOtp = Key('btn_submit_challenge_otp');
  static const Key resultSuggestedNextStep = Key('result_suggested_next_step');
  static const Key confirmPaymentMethod = Key('confirm_payment_method');
  static const Key confirmReference = Key('confirm_reference');
  static const Key filterFundingAll = Key('filter_funding_all');
  static const Key filterFundingAccount = Key('filter_funding_account');
  static const Key filterFundingCard = Key('filter_funding_card');
  static Key payFromSource(String type, int id) => Key('pay_from_${type}_$id');

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

  // ---------------------------------------------------------------------------
  // Super App Shared Widgets
  // ---------------------------------------------------------------------------
  static const Key appAvatar = Key('app_avatar');
  static const Key searchBarField = Key('search_bar_field');
  static const Key storyCard = Key('story_card');
  static const Key infoTile = Key('info_tile');
  static const Key walletCard = Key('wallet_card');
  static const Key nudgeBanner = Key('nudge_banner');
  static const Key productTile = Key('product_tile');
  static const Key appBottomNav = Key('app_bottom_nav');

  // ---------------------------------------------------------------------------
  // Stories (story_*)
  // ---------------------------------------------------------------------------
  static Key story(String id) => Key('story_$id');
  static const Key storyViewer = Key('story_viewer');
  static const Key storyClose = Key('story_close');
  static const Key storyAction = Key('story_action');
}

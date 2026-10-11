# LankaPay Mobile UI Redesign Specification & Architecture Plan

> **Scope**: Purely client-side UI/UX redesign of the Flutter mobile application (`/mobile`).  
> **Status**: Planning & Architecture Phase. **No application code modified.**  
> **Backend API Contract**: 100% Unchanged (zero schema or endpoint alterations).  
> **Target Visual Design**: Modern FinTech Neo-Banking aesthetic based on the reference design (Home, Cards, Transfers, in Light & Dark modes).

---

## 1. Existing Mobile Architecture Inventory

This section catalogs every existing screen, route, state provider/controller, repository/service, and corresponding backend API endpoint across `/mobile`.

### 1.1 Complete Screen & Route Inventory

| Screen Widget | Route Path | Feature Module | State Controller / Provider | Primary Responsibilities |
| :--- | :--- | :--- | :--- | :--- |
| `SplashScreen` | `/splash` | `auth` | `TokenStorage` | Checks persistent JWT tokens in secure storage; redirects to `/home` if authenticated or `/welcome` if unauthenticated. |
| `WelcomeScreen` | `/welcome` | `auth` | Stateless | Branded onboarding entry point with primary call-to-actions to Login, Register, and Consent. |
| `ConsentScreen` | `/consent` | `auth` | Local state | Mandatory Terms of Service and PDPA Privacy Policy consent checkboxes with legal disclosures. |
| `RegisterScreen` | `/register` | `auth` | `authStateProvider` (`AuthNotifier`) | Customer onboarding form validating Full Name, E.164 Mobile Number, National Identity Card (NIC), and 6-digit PIN. |
| `OtpVerificationScreen` | `/verify-otp` | `auth` | `authStateProvider` (`AuthNotifier`) | 6-digit SMS OTP input screen with countdown timer and token persistence upon successful verification. |
| `LoginScreen` | `/login` | `auth` | `authStateProvider` (`AuthNotifier`) | Phone number and 6-digit PIN authentication interface. |
| `RecoveryScreen` | `/recover` | `auth` | `AuthService` | Account recovery / PIN reset verification using registered phone and NIC. |
| `HomeScreen` | `/home` | `home` | `bankAccountsProvider` | Primary user dashboard showing simulated balance, default account card, quick action buttons, and recent activity. |
| `AccountsScreen` | `/accounts` | `accounts` | `bankAccountsProvider` (`BankAccountsNotifier`) | Multi-bank account list management: view linked bank accounts, set default, toggle registry discoverability & receiving status, rename, and remove. |
| `AddBankAccountScreen` | `/accounts/add` | `accounts` | `bankAccountServiceProvider` | Form to link a new commercial bank account with institution picker, account number, holder name, and nickname label. |
| `SendScreen` | `/send` | `send` | `transferServiceProvider`, `bankAccountsProvider` | Two-step transfer journey: Recipient lookup by phone number -> Debit account & amount entry -> Quote fetching -> Review sheet -> Step-up PIN authorization modal. |
| `TransferResultScreen` | `/send/result` | `send` | `transferServiceProvider` | High-fidelity payment outcome screen (COMPLETED in emerald green, REQUIRES_RECONCILIATION in amber with polling/refresh, FAILED in red). |
| `QrScreen` | `/qr` | `qr` | `qrServiceProvider` | Tabbed QR interface: Tab 0 generates static personal LankaPay-ID QR (opaque token); Tab 1 generates dynamic payment request QR with custom amount and expiry. |
| `QrScannerScreen` | *(Modal / Camera)* | `qr` | `qrServiceProvider` | Camera viewfinder for scanning LankaQR payloads and resolving opaque tokens (`QREF-xxx`). |
| `HistoryScreen` | `/history` | `history` | `historyServiceProvider` | Filterable and paginated transfer history (filters by direction, status, bank, amount range, date range). |
| `ReceiptScreen` | *(Detail sheet/route)* | `history` | `historyServiceProvider` | Digital tamper-evident transaction receipt displaying digital SHA-256 signature, bank reference, timestamps, and share action. |
| `NotificationsScreen` | `/notifications` | `notifications` | `notificationServiceProvider` | Dual-tab notification interface: Tab 0 shows privacy-safe in-app notifications with mark-as-read; Tab 1 manages delivery preferences (push, SMS, email, alerts). |
| `SettingsScreen` | `/settings` | `settings` | `authStateProvider` (`AuthNotifier`) | Main preferences hub with navigation to Privacy, Security, Linked Accounts, Language switcher, and Sign Out. |
| `PrivacySettingsScreen` | `/settings/privacy` | `settings` | `paymentProfileProvider` (`PaymentProfileNotifier`) | Payment Identity Registry settings: toggle phone-number discovery under PDPA, and emergency kill-switch freeze. |
| `SecuritySettingsScreen` | `/settings/security` | `settings` | Local state | Biometric authentication toggle, mandatory step-up PIN switch for transfers, and active trusted device sessions management. |

---

### 1.2 Services, Providers & API Endpoints

| Service / Provider | File Path | Method | HTTP Method & Endpoint | Payload / Parameters | Notes & Error Handling |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `AuthService`<br>`authServiceProvider` | `mobile/lib/features/auth/data/auth_service.dart` | `register()` | `POST /auth/register` | Body: `{phone, fullName, nic, pin, role}` | Returns `{userId, otpExpiresAt}`. Normalizes phone to E.164. |
| | | `verifyOtp()` | `POST /auth/verify-otp` | Body: `{userId, otp}` | Returns `{accessToken, refreshToken}`. Stores in `TokenStorage`. |
| | | `login()` | `POST /auth/login` | Body: `{phone, pin}` | Returns `{accessToken, refreshToken}`. |
| | | `logout()` | `POST /auth/logout` | Body: `{refreshToken}` | Revokes refresh token server-side; clears local tokens. |
| | | `initiateRecovery()` | `GET /payment-identities/lookup` | Query: `identifier` | Checks identity for recovery workflow. |
| `BankAccountService`<br>`bankAccountsProvider` | `mobile/lib/features/accounts/data/bank_account_service.dart` | `listInstitutions()` | `GET /bank-institutions` | None | Returns supported banks (BOC, Commercial Bank, HNB, Sampath, etc.). |
| | | `listAccounts()` | `GET /bank-accounts` | None | Returns active linked accounts (excludes `status == 'REMOVED'`). |
| | | `linkAccount()` | `POST /bank-accounts/link` | Body: `{institutionCode, accountNumber, accountName, label, consentGiven}` | Links simulated bank account. |
| | | `patchAccount()` | `PATCH /bank-accounts/{id}` | Body: `{label, discoverable, receivingEnabled}` | Updates nickname, registry discovery, or receiving toggle. |
| | | `setDefault()` | `POST /bank-accounts/{id}/set-default` | None | Sets default account for receiving funds. |
| | | `removeAccount()` | `DELETE /bank-accounts/{id}` | Body: `{consentGiven: true}` | Soft-deletes / revokes account link. |
| `TransferService`<br>`transferServiceProvider` | `mobile/lib/features/send/data/transfer_service.dart` | `lookupRecipient()` | `GET /payment-identities/lookup` | Query: `phone` | Anti-enumeration: returns `FOUND`, `NOT_FOUND`, `DISCOVERY_DISABLED`, `PRIVACY_RESTRICTED`. |
| | | `getQuote()` | `POST /transfers/quote` | Body: `{payeeId, payeePhone, amount, currency}` | Pre-calculates network fees and total debit amount. |
| | | `initiateTransfer()` | `POST /transfers` | Headers: `Idempotency-Key`, `X-Step-Up-Pin`<br>Body: `{amount, currency, pin, payeeId, payeePhone, quoteReference, note}` | Executes CEFT simulated transfer. Double-tap protected. |
| | | `getTransferById()` | `GET /transfers/{id}` | None | Polls status for pending / reconciliation transactions. |
| `HistoryService`<br>`historyServiceProvider` | `mobile/lib/features/history/data/history_service.dart` | `listHistory()` | `GET /transfers` | Query: `page, size, direction, status, bank, minAmount, maxAmount, from, to` | Returns paginated transfer list. |
| | | `getReceipt()` | `GET /transfers/{id}/receipt` | None | Returns signed digital receipt with tamper-evident signature. |
| `QrService`<br>`qrServiceProvider` | `mobile/lib/features/qr/data/qr_service.dart` | `generateStaticQr()` | `POST /qr/references` | Body: `{qrType: 'STATIC'}` | Generates static personal payment reference token. |
| | | `generateDynamicQr()` | `POST /qr/references` | Body: `{qrType: 'DYNAMIC', amount, validityMinutes, currency, note}` | Generates dynamic 15-minute expiring payment request. |
| | | `resolveReference()` | `GET /qr/references/{ref}` | None | Resolves opaque QR reference to recipient and amount. |
| `NotificationService`<br>`notificationServiceProvider` | `mobile/lib/features/notifications/data/notification_service.dart` | `getNotifications()` | `GET /notifications` | Query: `unreadOnly, page, size` | Returns in-app notifications. |
| | | `markAsRead()` | `PATCH /notifications/{id}/read` | None | Marks single notification as read. |
| | | `markAllAsRead()` | `POST /notifications/mark-all-read` | None | Marks all notifications as read. |
| | | `getPreferences()` | `GET /notifications/preferences` | None | Fetches notification channel preferences. |
| | | `updatePreferences()` | `PATCH /notifications/preferences` | Body: `{inAppEnabled, pushEnabled, emailEnabled, smsEnabled, transactionAlerts, securityAlerts, marketingAlerts}` | Updates user preferences. |
| `PaymentProfileNotifier`<br>`paymentProfileProvider` | `mobile/lib/features/settings/presentation/privacy_settings_screen.dart` | `_loadProfile()` | `GET /payment-profile/me` | None | Loads privacy status and masked phone number. |
| | | `toggleDiscoverability()` | `PATCH /payment-profile/discovery` | Body: `{discoverable: bool}` | Updates directory discovery flag. |

---

## 2. Reference Screen Mapping & Architecture

The 6 reference screenshots showcase a high-end, clean neo-banking interface featuring ambient gradient backgrounds, embossed card carousels, colorful service tiles, transaction feeds, and a modern bottom navigation dock.

```
+-----------------------------------------------------------------------------------+
|                            NEW APP NAVIGATION DOCK                                |
|                                                                                   |
|  [Tab 0: Home]          [Tab 1: Cards]         [Tab 2: Transfers]    [Tab 3: More]|
|  /home                  /accounts              /send                 /settings    |
|  (Home Dashboard)       (Linked Accounts)      (Recipient & Send)    (Preferences)|
+-----------------------------------------------------------------------------------+
```

### 2.1 Screen-by-Screen Mapping

#### 1. Reference "Home" -> Our Home Dashboard (`/home`)
* **Top Header Section**:
  * User Initials Avatar Circle (`JT` in reference): Displays user's initials derived from registered profile name or fallback initials, styled with a modern gradient.
  * Personal Greeting: `"Hello, James"` (or `"Hello, [User Name]"`).
  * Quick Actions: Search magnifying glass icon (jumps/focuses recipient search) and Notification bell with a red unread badge dot (`key: Key('home_notifications_icon_btn')`).
* **Total Balance Display**:
  * Label: `"Total balance"` (subtle grey typography).
  * Amount Display: Large bold text (e.g. `LKR 125,450.00` or aggregated linked accounts balance), accompanied by the required regulatory `SIMULATED` pill badge.
* **Cards Carousel ("CARDS" section)**:
  * Section header: `"CARDS"` with an `"Add +"` action button linking directly to `/accounts/add` (`key: Key('home_cards_add_btn')`).
  * Horizontal swipable cards:
    * Card 1: Sleek obsidian geometric-mesh card with VISA/LankaPay logo, account nickname/type (`"Salary" / "Primary Savings"`), simulated balance, and masked number (`"** 6917"`).
    * Card 2+: Vibrant lavender/indigo gradient card representing second linked bank account.
* **Finance Quick Action Tiles ("FINANCE" section)**:
  * 4 vivid gradient squircle tiles replacing old flat button row:
    1. **"Send / Pay"** (Purple gradient, Star/Send icon, `key: Key('home_quick_send_btn')`) -> Navigates to `/send`.
    2. **"LankaQR Scan"** (Orange gradient, QR icon, `key: Key('home_quick_scan_btn')`) -> Navigates to `/qr`.
    3. **"Request"** (Magenta/Rose gradient, Request/Receive icon, `key: Key('home_quick_request_btn')`) -> Navigates to dynamic QR generator on `/qr`.
    4. **"Analytics / History"** (Cyan/Teal gradient, Chart icon) -> Navigates to `/history`.
* **Recent Activity Feed ("LAST TRANSACTIONS" section)**:
  * Header: `"LAST TRANSACTIONS"` with `"See all >"` (`key: Key('home_view_all_history_btn')`) linking to `/history`.
  * Live transaction list fetched via `HistoryService.listHistory(page: 0, size: 5)` displaying icon, payee/payer name, relative timestamp, and amount (`- LKR 1,500.00` in red / `+ LKR 4,200.00` in green).

---

#### 2. Reference "Cards" -> Our Linked Bank Accounts (`/accounts`)
* **Header Bar**:
  * Large Title: `"Cards"` (or `"Linked Accounts"`).
  * Top Action: `"+"` button (`key: Key('add_bank_account_icon_btn')`) navigating to `/accounts/add`.
  * Theme Toggle Pill: Integrated Day/Night switch pill at top allowing instant theme preview.
* **Balance Display**:
  * Label: `"Available"`.
  * Amount: Large bold typography showing balance of the selected active card.
* **Featured Card Showcase**:
  * Full-width embossed payment card displaying institution logo, account holder name, masked account number, default badge, and masked balance with hide/show toggle.
  * Horizontal swipe carousel to inspect other linked bank accounts.
* **Quick Management Action Row**:
  * 3 elevated squircle buttons:
    1. **`+ Add money` / Link**: Add account or simulated top-up (`key: Key('accounts_quick_add_btn')`).
    2. **`Freeze`**: Quick toggle to freeze account receiving status via `PATCH /bank-accounts/{id}` (`key: Key('accounts_quick_freeze_btn')`).
    3. **`Settings`**: Opens bottom sheet with account rename, set as default, and remove options (`key: Key('accounts_quick_settings_btn')`).
* **Card Activity Feed**:
  * Displays transfers associated with the selected bank account (or `"No transaction yet"` empty state card).

---

#### 3. Reference "Transfers" -> Our Send Money & Recipient Search (`/send`)
* **Header Bar**:
  * Large Title: `"Transfers"`.
  * Top Action: LankaQR scanner icon button (`key: Key('transfers_qr_scan_btn')`) launching `/qr` camera viewfinder.
* **Recipient Search Input**:
  * Modern search bar with magnifying glass icon and clear button (`key: Key('send_phone_field')`), supporting phone number or LankaPay-ID search.
* **Recent Recipients Horizontal Carousel**:
  * Leading Circle: Purple action button `"+ Send"` (`key: Key('send_new_recipient_btn')`).
  * Recipient Avatars: Horizontal scroll of recent payees with photos/initials and first names (`"Anna L."`, `"Penny K."`, `"Kamal P."`).
  * Tapping any avatar pre-populates the recipient field and initiates instant lookup!
* **Transfer Method Grid (4-Tile Section)**:
  * 1. **"Popular payments"**: Frequent transfer amounts or utility templates.
  * 2. **"Bank of recipient"**: Transfer directly using destination bank code.
  * 3. **"Between my cards"**: Instant inter-account transfer between user's own linked banks.
  * 4. **"Recipient's card / LankaPay-ID"**: Standard phone-number directory transfer.
* **Transaction History Snippet**:
  * Shows recent outgoing fund transfers for quick re-send.
* **Preserved Business Logic & Modals**:
  * When a recipient is selected or looked up via `Key('send_lookup_btn')`, the full verified recipient card, debit bank selector, amount input (`Key('send_amount_field')`), quote modal (`Key('send_review_btn')`), confirmation sheet (`Key('confirmation_continue_btn')`), and step-up PIN dialog (`Key('transfer_pin_input')`, `Key('transfer_confirm_pin_btn')`) execute seamlessly.

---

#### 4. Persistent Bottom Navigation Bar
* A modern floating / docked navigation bar providing 1-tap switching across primary modules:
  1. **`Home`** (`/home`) — Dashboard, balance, cards carousel, quick finance tiles.
  2. **`Cards`** (`/accounts`) — Bank account cards, available balance, card controls.
  3. **`Transfers`** (`/send`) — Recipient lookup, recent recipients, payment options.
  4. **`More`** (`/settings`) — Privacy, security, devices, language, logout.

---

## 3. Widget & Theme Replacement Strategy

### 3.1 Theme Overhaul (`app_theme.dart`)

The current theme uses an institutional deep green (`0xFF0A5C36`) with gold accents (`0xFFD4AF37`). The redesign transitions the app into a modern Neo-Banking aesthetic:

#### Color Tokens Comparison

| Token Concept | Current Theme | Redesigned Light Theme | Redesigned Dark Theme |
| :--- | :--- | :--- | :--- |
| **Primary Accent** | Emerald Green (`#0A5C36`) | Modern Royal Indigo (`#6C4CE8`) | Electric Violet Glow (`#7C5CFC`) |
| **Secondary Accent** | Gold (`#D4AF37`) | Soft Periwinkle (`#9D86FF`) | Soft Lavender (`#B8A5FE`) |
| **Scaffold Background** | Off-white (`#F7FAF8`) | Soft Lavender-Tint (`#F5F4FA`) | Obsidian Void (`#0E0E14`) |
| **Ambient Top Glow** | None (solid) | Lavender Mesh Glow (`#E8E5FB` -> `#F5F4FA`) | Deep Violet Mesh Glow (`#22163B` -> `#0E0E14`) |
| **Card Surface** | Pure White (`#FFFFFF`) | Crisp Elevated White (`#FFFFFF`) | Dark Charcoal Glass (`#181822`) |
| **Text Primary** | Deep Green-Black (`#111D17`) | Deep Charcoal (`#101016`) | Crisp Pure White (`#FBFBFE`) |
| **Text Secondary** | Muted Grey-Green (`#526058`) | Neutral Slate (`#75758B`) | Muted Steel (`#8E8EA8`) |
| **Border Radius** | 12px – 16px | 20px – 24px (Cards), 40px (Pills) | 20px – 24px (Cards), 40px (Pills) |
| **Elevation & Shadow** | Standard 1dp elevation | Soft diffused multi-layer shadow | Subtle glowing border (`rgba(255,255,255,0.06)`) |

#### Theme Code Implementation Plan
* Keep `AppColors` class intact, updating semantic definitions to provide:
  * `AppColors.brandPrimary` (`#6C4CE8`), `AppColors.brandSecondary` (`#7C5CFC`)
  * `AppColors.cardDark` (`#181822`), `AppColors.cardLight` (`#FFFFFF`)
  * Gradient presets: `AppColors.darkAmbientGradient`, `AppColors.lightAmbientGradient`, `AppColors.cardMeshGradient`
  * Tile gradients: `AppColors.tilePurple`, `AppColors.tileOrange`, `AppColors.tilePink`, `AppColors.tileCyan`
* Configure `ThemeData.lightTheme` and `ThemeData.darkTheme` so that **all global Material 3 components** (Buttons, TextFields, Dialogs, BottomSheets, SnackBar, Checkboxes, Switches) automatically adopt the new palette and rounded geometry.

---

### 3.2 Widget Replacements & Additions

| Existing Widget | Replacement / Enhancement | Reason |
| :--- | :--- | :--- |
| `HomeScreen` (flat scaffold with standard AppBar) | Replaced with modern custom top bar (Avatar, Greeting, Search, Bell with badge), horizontal `CardsCarouselWidget`, 4-gradient `FinanceTilesGrid`, and dynamic `RecentTransactionsList`. | Matches Reference Home screens (Light & Dark). |
| `AccountsScreen` (simple vertical ListView of cards) | Replaced with `CardsShowcaseView` featuring large Available Balance, swipable debit card mesh visuals, 3-button quick action dock (`+ Add money`, `Freeze`, `Settings`), and account history. | Matches Reference Cards screens (Light & Dark). |
| `SendScreen` (plain step 0 phone field) | Replaced with `TransfersHubView` featuring top search bar, `RecentRecipientsCarousel`, 4-tile transfer categories, and bottom transactions list, followed by existing verified review & PIN modals. | Matches Reference Transfers screens (Light & Dark). |
| Top-level navigation | Add `MainNavigationShell` with a modern floating / blurred bottom navigation bar hosting Home, Cards, Transfers, and More. | Matches persistent bottom bar in all 6 reference screenshots. |

---

### 3.3 Untouched Screens (Automatic Theme Inheritance)

The following screens will remain structurally untouched during this phase. Because their UI is built using standard Material components (`Scaffold`, `Card`, `ElevatedButton`, `OutlinedButton`, `TextField`, `ListTile`, `SwitchListTile`, `AlertDialog`), they will **automatically adopt the new styling** through the updated `ThemeData`:

1. **Authentication Flow**:
   * `SplashScreen` (`/splash`)
   * `WelcomeScreen` (`/welcome`)
   * `ConsentScreen` (`/consent`)
   * `RegisterScreen` (`/register`)
   * `OtpVerificationScreen` (`/verify-otp`)
   * `LoginScreen` (`/login`)
   * `RecoveryScreen` (`/recover`)
2. **Sub-Flows & Detail Modals**:
   * `AddBankAccountScreen` (`/accounts/add`)
   * `TransferResultScreen` (`/send/result`)
   * `QrScreen` & `QrScannerScreen` (`/qr`)
   * `HistoryScreen` (`/history`)
   * `ReceiptScreen` (Transaction receipt)
   * `NotificationsScreen` (`/notifications`)
   * `SettingsScreen` (`/settings`)
   * `PrivacySettingsScreen` (`/settings/privacy`)
   * `SecuritySettingsScreen` (`/settings/security`)

---

## 4. Data Gap Analysis & Client-Side Derivation

The reference UI includes elements that do not map directly to a dedicated backend endpoint. The table below outlines how each item will be derived client-side without altering the backend API contract.

| UI Element in Reference | Backend Status | Client-Side Derivation Strategy | Fallback / Limitation |
| :--- | :--- | :--- | :--- |
| **Total Balance**<br>(e.g. `"$8 600"` on Home) | No single `/balance` endpoint (in Sri Lanka banking, balance inquiries require commercial bank switch adapters). | **Derived Client-Side**: Aggregate simulated balances across all active linked accounts in `bankAccountsProvider`. If each account holds a simulated balance, calculate `sum(accounts.balance)`. Maintain the `SIMULATED` regulatory tag. | If no accounts linked, display `LKR 0.00`. |
| **Available Balance**<br>(e.g. `"$2 230"` on Cards) | No live balance endpoint per account. | **Derived Client-Side**: Display the selected account's simulated balance (e.g., standard default account amount `LKR 125,450.00`). | Include eye icon to hide/show balance for privacy. |
| **Recent Recipients Avatar Carousel**<br>(e.g. `"Anna L."`, `"Penny K."`, `"Klark"`) | No `/transfers/recipients` or `/transfers/frequent` endpoint exists in backend. | **Derived Client-Side from `HistoryService.listHistory()`**: Call `GET /transfers?direction=SENT&size=30`. Filter and deduplicate by `receiverDisplayName` or `payeePhone`. Order by most recent `completedAt` timestamp. Extract recipient initials for avatar rings. Tapping any avatar auto-fills phone number in `SendScreen`. | If history is empty, carousel displays the single `+ Send` button. |
| **Unread Notification Badge**<br>(Red dot on bell icon) | No dedicated `/notifications/count` endpoint. | **Derived Client-Side from `NotificationService.getNotifications()`**: Call `GET /notifications?unreadOnly=true&size=1`. If results list is non-empty (`length > 0`), display the red indicator dot on `Key('home_notifications_icon_btn')`. | Refreshes when notifications screen is opened or dismissed. |
| **User Name & Initials**<br>(`"Hello, James"` / `"JT"` avatar) | `AuthService.login()` returns access tokens and phone number. | **Derived Client-Side from Profile or Storage**: Retrieve user name stored during registration (`fullName`) or query `GET /payment-profile/me` (`safeDisplayName`). Compute initials (`"James Taylor"` -> `"JT"`). | Fallback to `"Hello, Member"` and generic user icon if name unavailable. |
| **Card Specific Transactions**<br>(Cards screen bottom list) | No `/bank-accounts/{id}/transfers` endpoint. | **Derived Client-Side from `HistoryService`**: Filter cached transfer history by `bankTxRef` or institution matching the selected card. | If empty, display `"No transaction yet"` as shown in the reference image. |

---

## 5. Risk Assessment & Test Preservation

### 5.1 Routing & Navigation Architecture Risks

* **Risk: Introducing Bottom Navigation Shell**:
  * Currently, `/mobile` uses flat `GoRoute` entries in `app_router.dart`. Navigating between `/home`, `/accounts`, `/send`, and `/settings` is performed via `context.push()`.
  * Introducing a bottom navigation bar using `ShellRoute` or `StatefulShellRoute` risks altering routing behavior or breaking deep-linking expectations if not configured carefully.
* **Mitigation Strategy**:
  * Implement `StatefulShellRoute.indexedStack` in `app_router.dart` covering the 4 primary branches:
    * Branch 0: `/home` (`HomeScreen`)
    * Branch 1: `/accounts` (`AccountsScreen`)
    * Branch 2: `/send` (`SendScreen`)
    * Branch 3: `/settings` (`SettingsScreen`)
  * Ensure all route path strings (`'/home'`, `'/accounts'`, `'/send'`, `'/settings'`) remain identical.
  * Standalone child routes (`/accounts/add`, `/send/result`, `/qr`, `/history`, `/notifications`, `/settings/privacy`, `/settings/security`) remain top-level routes that push on top of the shell.

---

### 5.2 Widget Test Breakage & Key Preservation Matrix

The existing test suite (`mobile/test/widget_test.dart`, `mobile/test/transfer_test.dart`, `mobile/test/qr_history_notifs_test.dart`) relies on exact widget keys and text finders. **All keys must be strictly preserved in the redesigned widgets.**

| Existing Test Identifier | Associated Screen | Redesign Location & Preservation Requirement |
| :--- | :--- | :--- |
| `Key('home_quick_send_btn')` | `HomeScreen` | Attached to the new 1st gradient tile ("Send / Pay") in the FINANCE grid. |
| `Key('home_quick_scan_btn')` | `HomeScreen` | Attached to the new 2nd gradient tile ("Scan QR") in the FINANCE grid. |
| `Key('home_quick_request_btn')` | `HomeScreen` | Attached to the new 3rd gradient tile ("Request") in the FINANCE grid. |
| `find.text('Recent Activity')` | `HomeScreen` | Must be retained as the section title or accessible label for the transactions feed. |
| `Key('home_view_all_history_btn')` | `HomeScreen` | Attached to the `"See all >"` action button in the LAST TRANSACTIONS header. |
| `Key('home_notifications_icon_btn')`| `HomeScreen` | Attached to the notification bell icon in the top header. |
| `Key('home_settings_icon_btn')` | `HomeScreen` | Retained or aliased to navigate to settings. |
| `Key('send_phone_field')` | `SendScreen` | Attached to the primary search/input field at the top of the Transfers screen. |
| `Key('send_lookup_btn')` | `SendScreen` | Attached to the search execution button or search trigger. |
| `Key('send_amount_field')` | `SendScreen` | Retained in Step 1 of the transfer process. |
| `Key('send_note_field')` | `SendScreen` | Retained in Step 1 of the transfer process. |
| `Key('send_review_btn')` | `SendScreen` | Retained to trigger the confirmation sheet. |
| `Key('confirmation_continue_btn')` | `SendScreen` | Retained inside the transfer confirmation bottom sheet. |
| `Key('transfer_pin_input')` | `SendScreen` | Retained in the Step-up PIN dialog. |
| `Key('transfer_confirm_pin_btn')` | `SendScreen` | Retained in the Step-up PIN dialog. |
| `Key('add_bank_account_icon_btn')` | `AccountsScreen` | Attached to the `"+"` icon in the top header of the Cards screen. |
| `Key('empty_add_account_btn')` | `AccountsScreen` | Retained in the empty-state cards view. |
| `Key('rename_account_btn_${id}')` | `AccountsScreen` | Retained inside the account settings action bottom sheet. |
| `Key('remove_account_btn_${id}')` | `AccountsScreen` | Retained inside the account settings action bottom sheet. |
| `Key('set_default_btn_${id}')` | `AccountsScreen` | Retained inside the account settings action bottom sheet. |

---

### 5.3 Security & Regulatory Compliance Integrity

1. **Step-Up Authentication & Anti-Tamper**:
   * Outbound transfers MUST retain the mandatory 6-digit PIN verification modal (`X-Step-Up-Pin` header) and unique `Idempotency-Key` generation. The redesign will not combine or skip review steps.
2. **PDPA Privacy Disclosures**:
   * The opaque QR references (`QREF-xxx`) and masked phone numbers (`+9477***1234`) must remain masked in both light and dark mode.
3. **Simulated Sandbox Banner**:
   * As required by Sri Lanka sandbox guidelines, the `SimulatedBanner` widget (`"SIMULATED - TEST MONEY ONLY"`) must remain visible across primary authenticated screens.

---

## 6. Implementation Staging Checklist

When approved to begin coding, implementation will proceed through the following non-breaking stages:

- [ ] **Stage 1: Theme & Color Token Modernization**  
  Update `app_theme.dart` with modern purple/violet palette, dark/light glow backgrounds, 24px border radii, and elevated button styling. Verify existing screens still render cleanly.
- [ ] **Stage 2: Persistent Bottom Navigation Shell**  
  Implement `MainNavigationShell` with tabs for Home, Cards, Transfers, and More. Update `app_router.dart` using `StatefulShellRoute` while preserving all existing routes.
- [ ] **Stage 3: Redesign Home Screen (`/home`)**  
  Build header with initials avatar, Total Balance, swipable `CardsCarouselWidget`, 4-tile `FinanceTilesGrid`, and `LastTransactionsList`. Preserve all `home_*` test keys.
- [ ] **Stage 4: Redesign Cards Screen (`/accounts`)**  
  Build header with theme toggle pill, Available Balance, embossed payment card carousel, and 3-action dock (`+ Add money`, `Freeze`, `Settings`). Preserve all account management keys.
- [ ] **Stage 5: Redesign Transfers Screen (`/send`)**  
  Build search bar, recent recipients horizontal avatar carousel derived from history, 4-tile category grid, and connect seamlessly to the existing verified review sheet and step-up PIN dialog. Preserve all `send_*` and `transfer_*` test keys.
- [ ] **Stage 6: Test Suite Validation**  
  Run `flutter test` across all widget and unit test suites (`widget_test.dart`, `transfer_test.dart`, `qr_history_notifs_test.dart`) to guarantee 100% test pass rate.

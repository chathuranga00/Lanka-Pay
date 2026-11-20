# LankaPay ID Mobile App UI Manual QA Checklist

This document provides a comprehensive, rigorous Manual Quality Assurance checklist for the Flutter-based **LankaPay ID** mobile application across both iOS and Android.

---

## 1. Visual Aesthetics & Design System Conformance

- [ ] **Unified Theme Palette:**
  - [ ] **Light Theme:** Deep Purple gradient header (`#2D1B69` to `#1F104F`), crisp white surfaces, soft violet muted cards (`#F5F3FF`), clean border lines (`#E2E8F0`).
  - [ ] **Dark Theme:** Deep Charcoal background (`#12121A`), surface card elevations (`#1E1E2A`), high-contrast crisp text (`#FFFFFF` and `#94A3B8`).
  - [ ] **Contrast Compliance:** All typography satisfies WCAG AA contrast (minimum 4.5:1 for body copy, 3:1 for large display headers).
- [ ] **Simulated Environment Visibility:**
  - [ ] Every screen displays the amber/yellow `[SIMULATED - NO REAL MONEY]` header badge or banner so testers never confuse test tokens with real LKR.
- [ ] **Shared Component Consistency:**
  - [ ] Every primary flow uses `GradientHeaderScaffold` with consistent rounded top corners (28–32 dp) and subtle card elevations.
  - [ ] Buttons use `ActionButton` with 48 dp+ tap targets, pill or 16 dp rounded radius, and active elevation states.
  - [ ] Cards use `AccountCard` with geometric hexagon pattern accents and frost overlays when paused.
  - [ ] Tiles use `MenuTile`, `QuickActionTile`, or `TransactionTile` with unified typography and icon containers.

---

## 2. Motion & Micro-Interactions

- [ ] **Theme Cross-Fade:** Switching between System, Light, and Dark modes transitions smoothly (~300–400 ms) without harsh blinking.
- [ ] **System Reduce Motion Setting:**
  - [ ] When *Reduce Motion* is enabled in Android / iOS Accessibility settings:
    - [ ] `AccountCard` carousel scaling disabled (instantly renders at 1.0 scale).
    - [ ] Skeleton shimmer animations halt or simplify to static muted placeholders.
    - [ ] Tab switches occur immediately without animated slides.
- [ ] **Payment Result Discipline:**
  - [ ] `TransferResultScreen` strictly avoids frivolous or distracting decorative particle animations (confetti, bouncy spinners).
  - [ ] Status iconography (Green Success checkmark, Red Failed alert, Amber Pending timer) appears with dignified, clean fade-in.
- [ ] **Haptic Feedback:**
  - [ ] Light tactile haptic feedback triggers on primary button presses (`HapticFeedback.lightImpact()`).
  - [ ] Haptic feedback fires when switching tabs or expanding bottom sheets.

---

## 3. Accessibility & Responsive Typography

- [ ] **Semantics Labels on Icon-Only Controls:**
  - [ ] QR scanner icon has tooltip & semantics label: `"Scan QR Code"`.
  - [ ] Back buttons have tooltip & semantics label: `"Back"`.
  - [ ] Notification bell has tooltip & semantics label: `"Notifications"`.
  - [ ] Balance visibility eye toggle has tooltip & semantics label: `"Toggle balance"`.
  - [ ] Account number eye toggle has tooltip & semantics label: `"Reveal account digits"` / `"Hide account digits"`.
  - [ ] Share receipt icon has tooltip & semantics label: `"Share Receipt"`.
- [ ] **48 dp Minimum Tap Targets:**
  - [ ] All icon buttons, menu tiles, form action buttons, and segmented options have minimum tap target size >= 48x48 dp.
- [ ] **Text Scaling (Up to 200%):**
  - [ ] App tested under OS accessibility font scaling set to **1.5x** and **2.0x**.
  - [ ] Headers wrap gracefully without horizontal clipping.
  - [ ] Multi-line subtitles in `MenuTile` and `QuickActionTile` expand vertically without overflow.
  - [ ] Dialog titles wrap into `Expanded` widgets without throwing RenderFlex errors.

---

## 4. Screen-by-Screen Interaction Checklist

### 4.1 Home Screen (`/home`)
- [ ] `home_notifications_btn`: Opens `/notifications` screen.
- [ ] `home_balance_toggle`: Masks (`LKR ••••••••`) and reveals simulated total balance.
- [ ] `home_add_account`: Navigates directly to Link Bank Account modal (`/accounts/add`).
- [ ] `home_account_<id>`: Tapping an account card jumps to the Accounts tab focused on that card.
- [ ] `qa_send`: Navigates to `/send`.
- [ ] `qa_scan`: Navigates to `/qr` tab with scanner activated.
- [ ] `qa_request`: Navigates to `/qr` tab on Request Payment mode.
- [ ] `qa_my_qr`: Navigates to `/qr` tab showing Static LankaQR code.
- [ ] `home_see_all`: Navigates to full `/history` list.
- [ ] `tx_<id>`: Tapping any recent transaction opens its `ReceiptScreen`.
- [ ] **Pull to Refresh:** Refreshes accounts, recent transactions, and monthly statistics.

### 4.2 Accounts Screen (`/accounts`)
- [ ] `accounts_add` & `btn_add_account`: Opens `/accounts/add` screen.
- [ ] `account_toggle_visibility`: Toggles account number masking between `******7890` and `******••••`.
- [ ] `account_card_<id>`: Swiping carousel transitions cards with smooth scale interpolation.
- [ ] `btn_freeze`:
  - [ ] Displays confirmation dialog to pause receiving.
  - [ ] Confirming pauses receiving and renders frosty frosted-glass overlay on card.
  - [ ] Tapping again prompts to unfreeze / resume receiving.
- [ ] `btn_account_settings`:
  - [ ] Opens modal bottom sheet with full account controls.
  - [ ] `settings_toggle_discoverable`: Toggles telephone discoverability for this bank account.
  - [ ] `settings_toggle_receiving`: Pauses or resumes transfers into this account.
  - [ ] `settings_set_default`: Marks this account as the primary settlement default.
  - [ ] `settings_rename_btn`: Opens dialog to assign friendly alias (e.g., "Salary Account").
  - [ ] `settings_remove_btn`: Prompts confirmation to unlink simulated bank account.

### 4.3 Send Screen (`/send`)
- [ ] `send_scan_qr`: Activates camera/simulated scanner to parse LankaQR payload.
- [ ] `send_new`: Highlights phone number search input.
- [ ] `tile_pay_by_phone`: Focuses phone search bar.
- [ ] `tile_scan_qr`: Opens QR scanner.
- [ ] `tile_requests`: Navigates to QR Request Payment tab.
- [ ] `tile_between_accounts`: Opens informational dialog clarifying multi-account transfer policy.
- [ ] **Recipient Lookup Flow:**
  - [ ] `send_phone_field`: Accepts phone number (e.g. `0771234567`).
  - [ ] `send_lookup_btn`: Queries simulated backend directory.
  - [ ] **States Handled Gracefully:**
    - [ ] `FOUND`: Shows verified recipient badge, bank name, and account dropdown.
    - [ ] `NOT_FOUND`: Displays user-friendly non-blocking message.
    - [ ] `DISCOVERY_DISABLED`: Explains recipient has opted out of telephone discovery.
    - [ ] `SERVICE_UNAVAILABLE`: Displays retry option.
- [ ] **Transfer Form & Review:**
  - [ ] `send_sender_account_dropdown`: Selects debit account (with `isExpanded: true` preventing overflow).
  - [ ] `send_amount_field`: Accepts transfer amount with automated currency formatting.
  - [ ] `send_note_field`: Accepts optional payment reference/memo.
  - [ ] `send_review_btn`: Validates input and presents Step-Up confirmation sheet.
  - [ ] `transfer_pin_input`: Secure step-up PIN input prompt.
- [ ] **Result Screens (`TransferResultScreen`):**
  - [ ] `SUCCESS`: Verified green badge, transaction reference, button to view receipt.
  - [ ] `FAILED`: Specific reason displayed with clear advice on whether to retry.
  - [ ] `PENDING` / `RECONCILING`: Clear warning not to submit duplicate transfer while background settlement resolves.

### 4.4 LankaQR Screens (`/qr`)
- [ ] **Static My QR Tab:**
  - [ ] Displays EMVCo-compliant LankaQR code with simulated badge.
  - [ ] Shows verified account name and privacy-preserving masked identifier.
  - [ ] Explains that no sensitive bank credentials (PII) are exposed in QR payload.
- [ ] **Dynamic Payment QR Tab:**
  - [ ] Amount input field and memo input.
  - [ ] `Create Dynamic Payment QR` button generates time-bounded, single-use payment QR.
  - [ ] Timer countdown displays expiration period.
- [ ] **Scan QR Tab:**
  - [ ] Camera viewport overlay with targeted square reticle and flashlight toggle.

### 4.5 History & Receipt Screens (`/history`, `/receipt`)
- [ ] **Search & Filters:**
  - [ ] Search by recipient name or transaction reference.
  - [ ] Filter chips: `All`, `Completed`, `Failed`, `Pending`.
  - [ ] Date range selector filter.
- [ ] **Receipt Screen:**
  - [ ] Receipt number, timestamp, sender/receiver details, debited amount, and bank fee.
  - [ ] Cryptographic simulated digital signature and bank settlement trace.
  - [ ] `receipt_share_btn`: Shares or copies text summary to clipboard.

### 4.6 Notifications Screen (`/notifications`)
- [ ] Categorized list of transactional alerts, security warnings, and system announcements.
- [ ] Unread indicator dots.
- [ ] "Mark All as Read" header button.

### 4.7 More / Settings Screen (`/more`)
- [ ] `more_profile`: Opens user profile sheet (Name, Masked NIC, LankaPay ID).
- [ ] `more_qr`: Quick access to QR tab.
- [ ] `more_requests`: Access to QR payment requests.
- [ ] `more_history`: Navigates to full transaction log.
- [ ] `more_notifications`: Navigates to notification center.
- [ ] `more_privacy`: Navigates to Privacy & Discoverability settings.
- [ ] `more_security`: Navigates to Security settings (Biometrics, PIN Step-Up).
- [ ] `more_language`: Opens trilingual modal (`English`, `සිංහල`, `தமிழ்`).
- [ ] `more_help`: Opens contact and FAQs bottom sheet.
- [ ] **Appearance Segmented Control:**
  - [ ] `theme_option_system`: Follows device system theme.
  - [ ] `theme_option_light`: Forces bright clean light palette.
  - [ ] `theme_option_dark`: Forces deep charcoal dark palette.
- [ ] `more_logout`: Shows sign-out confirmation dialog.

---

## 5. Network Resiliency, Offline & Error States

- [ ] **Offline Banner:**
  - [ ] App detects network loss via connectivity listener.
  - [ ] Shows persistent top banner: *"Offline Mode - Displaying cached data"*.
- [ ] **Shimmer Loading Skeletons:**
  - [ ] Slow network conditions render pulsing skeleton placeholders for account cards, balance display, recipient list, and transaction tiles.
  - [ ] No layout shift (CLS) when real data replaces skeleton items.
- [ ] **API Error Retry:**
  - [ ] `ErrorRetry` widget displays whenever an endpoint fails.
  - [ ] Tapping "Retry" cleanly re-fetches the Riverpod future without crashing.
- [ ] **Empty States:**
  - [ ] Zero linked accounts displays informative card with *"Link your first account"* CTA.
  - [ ] Zero transactions displays friendly graphic with *"Make your first transfer"* CTA.
  - [ ] Zero notifications displays clean empty inbox graphic.

---

## 6. Form Validation & Security Edge Cases

- [ ] **NIC Validation:** Accepts old 9-digit format (e.g., `123456789V`) and new 12-digit format (e.g., `199512345678`).
- [ ] **Phone Number Validation:** Accepts standard Sri Lankan mobile formats (`07XXXXXXXX`, `+947XXXXXXXX`).
- [ ] **PIN Inputs:**
  - [ ] Obscured digits with toggle option.
  - [ ] Strict 6-digit numerical constraint.
  - [ ] Matching confirmation required on registration/reset.
- [ ] **Session Expiry & Token Refresh:**
  - [ ] Expired tokens prompt Step-Up re-authentication without dropping unsaved draft forms where possible.

# LankaPay ID — Click-by-Click Evaluation & Demo Script

**Project:** LankaPay ID National Payment Identifier  
**Scenario Specification:** Master Prompt Section 24 ("End-to-End Demonstration Scenario")  
**Target Audience:** Academic Evaluators, Testers, and Demonstrators  
**Estimated Run Time:** 10–12 minutes

---

## 1. Demonstration Personas & Initial State

Ensure both backend and clients are running per the [Setup Guide](file:///q:/MY%20Projects/Lanka%20Pay/docs/setup-guide.md). The application will auto-seed these accounts in `SIMULATED` mode:

| Persona | Phone Number | PIN | Role & Seeded Accounts |
| :--- | :--- | :--- | :--- |
| **Kasun Perera** (Sender) | `+94770000001` | `123456` | Sender. 3 linked accounts:<br>• Bank A (`100100`) — Balance: `150,000.00 LKR`<br>• Bank B (`100101`) — Balance: `75,000.00 LKR` *(Default)*<br>• Bank C (`100102`) — Balance: `25,000.00 LKR` |
| **Nimal Silva** (Receiver) | `+94770000002` | `123456` | Receiver. 1 linked account:<br>• Bank A (`100201`) — Balance: `50,000.00 LKR` *(Default)* |
| **System Admin** | `+94770000000` | `123456` | Back-office administrator on Admin Web Portal. |

---

## Step 1: User Login & Recipient Discovery (Privacy-Preserving Lookup)

**Objective:** Demonstrate recipient phone search and verify that PII is masked (abbreviated display name, no raw account numbers).

1. Open the **Mobile App**.
2. On the **Login Screen**:
   - Enter Phone Number: `0770000001`
   - Enter PIN: `123456`
   - Tap **"Sign In"**.
3. You are now on the **Home Dashboard**. Observe:
   - Green banner: `"SIMULATED - test money only"`.
   - Linked accounts summary shows Bank B as default.
4. Tap the **"Send"** action button in the Quick Actions section.
5. In the **Recipient Lookup** field, enter Nimal's phone number: `0770000002`.
6. Tap **"Search Recipient"**.
7. **Verification Checkpoint:**
   - [x] Recipient name is displayed as **`Nimal P.`** (or `Nimal S.`) with the rest masked.
   - [x] Receiving institution is displayed as **`Bank A (Commercial Bank)`**.
   - [x] Raw 12-to-16 digit account number is **NOT** exposed anywhere on the screen.

---

## Step 2: Instant P2P Payment (Default Bank B Funding Account)

**Objective:** Complete an instant transfer using the user's default linked bank account.

1. On the transfer setup screen (recipient verified):
   - Funding Account: Observe that **`Bank B (****1001)`** is pre-selected as the default.
   - Amount: Enter `500.00`.
   - Remarks / Reference: Enter `Lunch reimbursement`.
2. Tap **"Continue to Review"**.
3. On the **Confirmation Screen**, review the transaction details:
   - Recipient: `Nimal P.`
   - Destination Bank: `Bank A`
   - Source Account: `Bank B (****1001)`
   - Transfer Amount: `500.00 LKR`
   - Processing Fee: `0.00 LKR`
   - Total Amount: `500.00 LKR`
4. Tap **"Authorize Payment"**.
5. Enter PIN: `123456` on the authentication dialog.
6. Tap **"Confirm"**.
7. **Verification Checkpoint:**
   - [x] Screen transitions immediately to **Payment Success** (Emerald Green).
   - [x] Displays Bank Reference: `SIM-B-...`.
   - [x] Status badge shows `COMPLETED`.
   - [x] Double-tap or retry does not create a duplicate debit (enforced via `Idempotency-Key`).

---

## Step 3: Multi-Bank Funding Account Selection

**Objective:** Demonstrate switching the funding account to another linked bank before payment.

1. From the Payment Success screen, tap **"Back to Home"**.
2. Tap **"Send"**.
3. Enter Recipient: `0770000002` and tap **"Search Recipient"**.
4. In the **"Pay From"** account dropdown/selector:
   - Select **`Bank C (****1002)`** instead of default Bank B.
5. Enter Amount: `750.00`.
6. Tap **"Continue to Review"** $\rightarrow$ tap **"Authorize Payment"**.
7. Enter PIN: `123456` and confirm.
8. **Verification Checkpoint:**
   - [x] Payment succeeds with `COMPLETED` status.
   - [x] Provider reference confirms settlement originated from Bank C: `SIM-C-...`.

---

## Step 4: Insufficient Funds & Deterministic Bank Rejection

**Objective:** Demonstrate bank-level decline handling without crashing or hanging.

1. From Home Dashboard, tap **"Send"**.
2. Enter Recipient: `0770000002` and search.
3. Select Funding Account: **`Bank C (****1002)`** (seeded balance: `25,000.00 LKR`).
4. Enter Amount: `50,000.00` (exceeds balance) or `2000000.00`.
5. Tap **"Continue to Review"** $\rightarrow$ tap **"Authorize Payment"**.
6. Enter PIN: `123456` and confirm.
7. **Verification Checkpoint:**
   - [x] Transitions to **Payment Failed** screen (Crimson Red).
   - [x] Friendly error message: `"Account balance is below the requested transfer amount"`.
   - [x] Error code: `INSUFFICIENT_FUNDS`.
   - [x] Ledger reflects zero debit; daily cumulative spend counter is not altered.

---

## Step 5: Delayed / Timeout Settlement & Admin Reconciliation

**Objective:** Demonstrate asynchronous settlement, non-green pending UI, and administrative reconciliation.

1. From Home Dashboard, tap **"Send"**.
2. Enter Recipient: `0770000002`.
3. Enter the special test amount: `30000.00` (configured in simulated adapter to trigger a network timeout).
4. Authorize with PIN `123456`.
5. **Verification Checkpoint (Mobile):**
   - [x] Screen displays **Processing / Requires Reconciliation** with an Amber alert badge.
   - [x] Screen is **NEVER GREEN** (adheres to Section 18: Never show green for unconfirmed payments).
   - [x] Offers a `"Check Status"` button.
6. Open **Admin Web Portal** (`http://localhost:5173/`):
   - Login with Phone: `+94770000000` / PIN: `123456`.
   - Navigate to **"Reconciliation Queue"** in the sidebar.
   - Locate the transaction for `30,000.00 LKR` with status `REQUIRES_RECONCILIATION`.
   - Click the **"Reconcile"** action button.
   - Select Outcome: **"Confirm Settlement (Success)"** and click Submit.
7. Return to the **Mobile App** and tap **"Check Status"**:
   - [x] Transaction status transitions to **`COMPLETED`**.

---

## Step 6: Dynamic LankaQR Code Generation & Scanning

**Objective:** Demonstrate EMVCo / LankaQR compliant dynamic request generation with embedded amount and expiry.

1. Log into mobile as **Nimal** (`0770000002` / `123456`) or use another emulator/device.
2. Tap **"Receive / QR"** from the dashboard.
3. Tap **"Request Amount"** to generate a **Dynamic QR**:
   - Amount: `1200.00`
   - Remarks: `Coffee & Pastry`
   - Expiry: `15 minutes`
4. Tap **"Generate QR Code"**.
5. Observe the generated QR image and shareable payment link (`lankapay://pay?ref=...`).
6. Switch back to **Kasun's** device:
   - Tap **"Scan QR"**.
   - Scan or simulate decoding the reference token.
7. **Verification Checkpoint:**
   - [x] Recipient (`Nimal P.`) and Amount (`1,200.00 LKR`) are locked and pre-populated.
   - [x] Kasun confirms with PIN `123456` $\rightarrow$ payment executes instantly.

---

## Step 7: Tamper-Evident Transaction History & Audit Receipts

**Objective:** Demonstrate transaction history filters and cryptographic tamper validation.

1. In the Mobile App, tap the **"History"** tab in the bottom navigation.
2. Observe the chronological stream of transactions from Steps 2, 3, 5, and 6.
3. Test Filters:
   - Filter by **"Completed"** $\rightarrow$ only successful transfers appear.
   - Filter by **"Failed"** $\rightarrow$ the 50,000.00 LKR decline appears.
4. Tap the first completed transfer (`500.00 LKR`).
5. In the **Transaction Details** screen, tap **"View Digital Receipt"**.
6. **Verification Checkpoint:**
   - [x] Shows unique LankaPay Reference ID, Bank Reference, and Timestamp.
   - [x] Displays **SHA-256 Tamper Checksum**: `SHA256:...`.
   - [x] Tap **"Verify Integrity"** $\rightarrow$ verifies checksum matches event log state.

---

## Step 8: Emergency Identity Freeze & Discoverability Toggle

**Objective:** Demonstrate consumer privacy rights and fraud prevention controls.

1. In the Mobile App, tap **"Settings"** $\rightarrow$ **"Privacy & Payment Identity"**.
2. Toggle **"Emergency Identity Freeze"** to **ON**.
3. Confirm the warning prompt: *"Freezing your payment identity prevents all incoming and outgoing transfers."*
4. Attempt a transfer to Kasun from another user:
   - Recipient Lookup for `0770000001`.
5. **Verification Checkpoint:**
   - [x] Lookup returns error: **`LOOKUP_IDENTITY_FROZEN`**.
   - [x] No bank routing data or account information is returned.
6. Toggle **"Emergency Identity Freeze"** to **OFF** $\rightarrow$ identity resumes active status.

---

## Step 9: Virtual & Linked Card Scenario (Add, Verify, Pay, Freeze, Decline, Unfreeze, Remove)

> ⚠ **TEST CARDS ONLY**: Cards in LankaPay are strictly simulated. Refer to `docs/test-cards.md` for test numbers. Never enter real card credentials.

**Objective:** Demonstrate complete PCI-compliant card lifecycle and card-funded payment flow.

### 9.1 Link Test Card & Verify Bank OTP
1. In the Mobile App, navigate to the **"Accounts"** tab in bottom navigation.
2. Toggle the segmented control to **"Cards"** (`seg_cards`).
3. Tap the **"+" (Add Card)** button (`cards_add`).
4. Select or enter test card from `docs/test-cards.md` (e.g. Test Card 1: `ALWAYS_APPROVED` ending in `0010`, CVV `111`, expiry `12/27`).
5. Confirm the explicit card linking consent checkbox and tap **"Link Card"**.
6. The screen prompts for **Bank OTP Challenge** (`card_challenge_otp`).
7. Enter the 6-digit bank verification OTP (e.g. `112233`) and confirm.
8. **Verification Checkpoint:**
   - [x] Card appears in carousel with label **"TEST CARD"**.
   - [x] Masked number shows `•••• 0010` (no full PAN).
   - [x] Status is active and shows remaining daily limit.

### 9.2 Pay from the Card & Inspect Digital Receipt
1. Tap **"Send"** on Home screen, lookup recipient Nimal (`0770000002`).
2. On amount screen, enter `1000.00` LKR.
3. In the **"Pay from"** selector (`pay_from_selector`), choose your test card `•••• 0010 (TEST CARD)`.
4. On the confirmation screen:
   - [x] Displays principal amount: `1,000.00 LKR`.
   - [x] Displays simulated card fee: `15.00 LKR (Simulated Card Processing Fee 1.5%)`.
   - [x] Displays total debited: `1,015.00 LKR`.
   - [x] Payment method is labeled: `Paid with card •••• 0010 (TEST CARD)`.
5. Authenticate with step-up PIN (`123456`).
6. **Verification Checkpoint:**
   - [x] Transfer completes immediately (`COMPLETED`).
   - [x] Digital receipt shows fee breakdown, transaction reference, and card payment method.

### 9.3 Freeze Card & Attempt Payment (Decline Verification)
1. Return to the **"Cards"** tab, select the card, and tap **"Freeze"** (`card_action_freeze`).
2. Card updates to a frosted overlay with a snowflake icon and status badge **"FROZEN"**.
3. Attempt to make a send or QR payment of `500.00` LKR selecting this frozen card.
4. **Verification Checkpoint:**
   - [x] Payment is declined with error code `CARD_FROZEN`.
   - [x] Screen presents friendly recovery suggestion: *"Unfreeze card or select another payment source"*.

### 9.4 Unfreeze & Remove Card
1. Return to **"Cards"** tab and tap **"Unfreeze"** (`card_action_unfreeze`).
2. Enter step-up PIN (`123456`) $\rightarrow$ Card status resumes **"ACTIVE"**.
3. Tap **"Settings"** on the card actions and select **"Remove Card"**.
4. Confirm step-up PIN $\rightarrow$ Card is securely removed and deleted from user view.

---

## 10. Evaluation Completion Checklist

| Evaluation Area | Target Behavior | Result |
| :--- | :--- | :--- |
| **Privacy & Security** | Display name masked; no plain account numbers; PIN BCrypt hashed | Passed |
| **Multi-Bank Core** | 3 linked banks; default account switching; multi-gateway dispatch | Passed |
| **Resilience & Idempotency** | Double-tap protection; timeout reconciliation queue; safe failure | Passed |
| **LankaQR Interoperability** | Dynamic QR with amount lock, digital signature, and expiration | Passed |
| **Admin Back-Office** | Gateway health toggle; reconciliation settlement; audit log search | Passed |

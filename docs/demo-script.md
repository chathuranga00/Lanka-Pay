# LankaPay Super-App – 12-Step Evaluation & Demo Script

> **Scenario Specification**: Super-App End-to-End Evaluation Flow  
> **Backend Automated Verification**: [`SuperAppEndToEndDemoTest.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/e2e/SuperAppEndToEndDemoTest.java) (12/12 Steps Passing)  
> **Estimated Duration**: 10–12 minutes  
> **Simulation Disclaimer**: All transactions, billers, cards, merchants, and balances represent simulated mock data.

---

## Overview of the 12-Step Demo Scenario

| Step | Capability Demonstrated | Core Module(s) | Verification Checkpoint |
|---|---|---|---|
| **1** | User Registration & Verification | `identity` | OTP confirmed; user receives JWT; zero PII leak |
| **2** | Bank Account & Virtual Card Provisioning | `bank-integration`, `simvault` | Bank account linked; virtual card issued via SimVault |
| **3** | Neo-Banking Home Dashboard | `home` | Aggregator returns greeting, wallet balance, and security nudge in < 500ms |
| **4** | Financial Literacy Story | `story` | Story opened, read, marked as seen; whitelisted deep-link navigation |
| **5** | Card-Funded Utility Bill Payment | `bills`, `cards` | Electricity bill (3,450.00 LKR + 30.00 fee = 3,480.00 LKR = 348,000 minor units) paid with card |
| **6** | Standing Order / Scheduled Payment | `scheduled` | Monthly recurring payment created; active schedule logged |
| **7** | Group Bill Split with Rate-Limited Reminder | `split` | 6,000.00 LKR dinner split among 3 friends (2,000.00 LKR each); reminder cooldown |
| **8** | Monthly Spend Analytics & Budget Tracking | `analytics` | Bill payment reflected in monthly budget; categorization updated |
| **9** | Loyalty Points Accrual & Voucher Redemption | `rewards` | Points earned on successful payment; points separated from fiat; redeemed for voucher |
| **10**| City Merchant & LankaQR In-Store Payment | `city`, `qr` | Merchant directory search; LankaQR payload resolved; payment completed |
| **11**| Customer Support Chat & FAQ Bot Handoff | `support` | Automated FAQ answers query; sanitized text; agent handoff queue |
| **12**| Regulatory Statement Export & Audit Verification | `admin`, `audit` | Monthly statement exported; compliance audit events verified |

---

## Step-by-Step Instructions

### Step 1: User Registration & OTP Verification
1. Launch the mobile app and tap **"Register"**.
2. Enter details:
   - Full Name: `Kamal Perera`
   - Mobile Number: `0771234567` (`+94771234567`)
   - National Identity Card (NIC): `199012345678`
   - Security PIN: `123456`
3. Tap **"Register"**. System sends 6-digit OTP to mobile phone.
4. Enter OTP `123456` and tap **"Verify & Continue"**.
- **Verification**: User is authenticated; access token issued; profile status is `ACTIVE`.

---

### Step 2: Link Bank Account & Issue Virtual Card
1. From the dashboard or Accounts tab, tap **"Add Account"**.
2. Select Bank: `Bank of Ceylon (BOC)`. Enter account number `100234567890`.
3. Check consent box and tap **"Link Account"**.
4. In the Cards section, tap **"Add Card"** $\rightarrow$ **"Issue Virtual Card"**.
5. Set label: `Virtual Daily Card`, daily spend limit: `100,000.00 LKR`.
- **Verification**: Account appears with masked number `•••• 5678` and initial balance `125,450.00 LKR`. Virtual card `**** 0010` is active.

---

### Step 3: View Neo-Banking Home Dashboard
1. Navigate to the **Home** tab (`/home`).
2. Observe the aggregated layout loaded in < 500ms:
   - Greeting: `Good morning, Kamal` with initial avatar `K`.
   - Wallet card: Displaying `LKR 125,450.00` with simulated test badge.
   - Security nudge: `"Never share your PIN, OTP, or password with anyone."`
3. Tap the eye icon to toggle balance privacy (`••••••••`).
- **Verification**: Home Aggregator SLA < 500ms verified; no section failures.

---

### Step 4: Open & Read Financial Literacy Story
1. In the top Stories row on the Home tab, tap the story card **"Protect Your PIN"**.
2. Read through the carousel page explaining safety against phishing.
3. Tap the action button **"Security Settings"**.
- **Verification**: Client router navigates to `/settings/security`. Story is marked as seen with subtle border ring upon returning to Home.

---

### Step 5: Pay Utility Bill from Virtual Card
1. Navigate to **Pay Hub** (`/pay-hub`) $\rightarrow$ tap **"Pay Bills"** (`/bills`).
2. Select Category: `Electricity` $\rightarrow$ Select Biller: `Simulated Electricity Board (SIM_ELEC_01)`.
3. Enter Account Reference: `1009876543`.
4. Enter Amount: `3,450.00 LKR` (345,000 minor units).
5. Review Fee Breakdown:
   - Bill Amount: `3,450.00 LKR`
   - Convenience Fee: `30.00 LKR` (3,000 minor units)
   - Total Payable: `3,480.00 LKR` (348,000 minor units)
6. Choose Payment Method: `Virtual Daily Card (**** 0010)`.
7. Authorize with PIN `123456`.
- **Verification**: Payment succeeds with status `COMPLETED`. Receipt issued with total `348,000 minor units`.

---

### Step 6: Create Recurring Scheduled Standing Order
1. In **Pay Hub**, tap **"Scheduled Payments"** $\rightarrow$ tap **"+"** (`/scheduled/create`).
2. Payee Type: `Electricity Bill` (`SIM_ELEC_01`).
3. Amount: `3,500.00 LKR`.
4. Frequency: `Monthly on the 1st`.
5. Tap **"Create Schedule"** and authorize with PIN `123456`.
- **Verification**: Schedule status is `ACTIVE`; appears in scheduled payments list with next execution date.

---

### Step 7: Split Dinner Bill with Friends
1. In **Pay Hub**, tap **"Split a Bill"** $\rightarrow$ tap **"New Split"** (`/split/create`).
2. Title: `Dinner at Cinnamon Leaf`. Total Amount: `6,000.00 LKR`.
3. Add Participants:
   - Friend 1: `Nimali Silva` (`+94772345678`) – `2,000.00 LKR`
   - Friend 2: `Kasun Perera` (`+94773456789`) – `2,000.00 LKR`
   - Your Share: `2,000.00 LKR`
4. Tap **"Create Split"**.
5. On the Split Details screen, tap **"Send Reminders"**. Tap again immediately to observe the 60-second cooldown rate limit.
- **Verification**: Split shows progress `"1 of 3 settled"`. Reminders trigger with rate-limiting feedback.

---

### Step 8: View Spend Analytics & Monthly Budget
1. Tap the **Activity / Spend** info tile on the Home Screen (`/activity`).
2. Observe Monthly Breakdown:
   - Spent this month: `Rs 3,480.00`
   - Monthly Budget: `Rs 50,000.00`
   - Used fraction: `6.96%`
   - Category Breakdown: `Utilities & Bills: 100%`
- **Verification**: Only `COMPLETED` transactions are counted toward budget metrics.

---

### Step 9: Earn & Redeem Loyalty Rewards Points
1. Tap the **Rewards** pill on the Home Screen (`/rewards`).
2. Observe Points Balance: User earned points on the completed utility bill payment.
3. Switch to the **Offers** tab $\rightarrow$ select `Rs 500 Fuel Voucher (500 pts)`.
4. Tap **"Redeem Voucher"**.
- **Verification**: Points balance decreases by 500; voucher code `LANKAPAY-FUEL-500` is generated. Non-fiat points ledger strictly separates loyalty points from currency.

---

### Step 10: In-Store Payment at City Merchant
1. Navigate to the **City** tab (`/city`).
2. Search for `Cinnamon Leaf Bistro` or filter by category `Food & Cafes`.
3. Tap the merchant card $\rightarrow$ tap **"Pay via LankaQR"**.
4. Confirm amount `1,500.00 LKR` and authorize payment.
- **Verification**: LankaQR payload resolves; payment succeeds; points credited to rewards ledger.

---

### Step 11: Customer Support & Automated FAQ Bot
1. Navigate to the **Chat** tab (`/support`).
2. Observe safety banner: `"Never share your OTP, PIN, or full card number."`
3. Tap FAQ shortcut: `"How do I freeze my card?"`
4. FAQ bot responds immediately with instructions.
5. Tap **"Talk to Agent"** to test human handoff queue.
- **Verification**: Bot messages are HTML-sanitized; conversation status transitions to `WAITING_FOR_AGENT`.

---

### Step 12: Statement Export & Administrative Compliance
1. In Settings, select **"Export Statement"**.
2. Select Month: `October 2026` $\rightarrow$ Format: `PDF / CSV`.
3. Open the **Admin Web Console** (`/api/v1/admin/users/1/audit`).
4. Inspect the immutable audit log:
   - Account creation
   - Card provisioning
   - Utility bill payment
   - Schedule creation
   - Points accrual
- **Verification**: All financial actions produce cryptographically attributable audit entries.

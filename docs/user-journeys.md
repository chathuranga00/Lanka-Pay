# Lanka Pay – User Journeys

> **Draft** – will be refined once `docs/MASTER_PROMPT.md` is populated.

All sequence diagrams use the module names from `architecture.md`.
"SIMULATED" labels appear wherever a bank or SMS interaction occurs.

---

## 1. Register & Verify Phone

A new user installs the app, enters their phone number, verifies via OTP, and sets a PIN/password.

```mermaid
sequenceDiagram
    actor U as User (Flutter)
    participant API as API Gateway / Security
    participant ID as identity
    participant NOTIF as notification
    participant SMS as SMS Gateway (SIMULATED)
    participant DB as MySQL

    U->>API: POST /api/v1/auth/register {phone, fullName, nic}
    API->>ID: registerUser(phone, fullName, nic)
    ID->>DB: INSERT users (status=PENDING_VERIFICATION)
    ID->>NOTIF: sendOtp(phone, otp_hash, ttl=5min)
    NOTIF->>SMS: send SMS "Your Lanka Pay OTP is XXXXXX [SIMULATED]"
    SMS-->>NOTIF: delivery receipt
    NOTIF-->>ID: ok
    ID-->>API: 202 Accepted {userId, expiresAt}
    API-->>U: 202 Accepted

    U->>API: POST /api/v1/auth/verify-otp {userId, otp}
    API->>ID: verifyOtp(userId, otp)
    ID->>DB: compare hashed OTP, check TTL
    ID->>DB: UPDATE users SET status=ACTIVE
    ID-->>API: 200 OK {accessToken, refreshToken}
    API-->>U: 200 OK – registration complete
```

---

## 2. Login

Existing user logs in with phone + PIN, receives JWT.

```mermaid
sequenceDiagram
    actor U as User (Flutter)
    participant API as API Gateway / Security
    participant ID as identity
    participant AUD as audit
    participant DB as MySQL

    U->>API: POST /api/v1/auth/login {phone, pin}
    API->>ID: authenticate(phone, pin)
    ID->>DB: fetch user by phone
    ID->>ID: BCrypt.verify(pin, storedHash)
    alt credentials valid
        ID->>AUD: log LOGIN_SUCCESS(userId, ip, timestamp)
        ID-->>API: 200 OK {accessToken, refreshToken}
        API-->>U: 200 OK
    else invalid
        ID->>AUD: log LOGIN_FAILURE(phone, ip, timestamp)
        ID-->>API: 401 Unauthorized
        API-->>U: 401 Unauthorized
    end
```

---

## 3. Link a Bank Account

User adds a bank account; the adapter verifies it with the (simulated) bank.

```mermaid
sequenceDiagram
    actor U as User (Flutter)
    participant API as API Gateway / Security
    participant BANK as bank-integration
    participant ADP as BankAdapter (SIMULATED)
    participant NOTIF as notification
    participant AUD as audit
    participant DB as MySQL

    U->>API: POST /api/v1/bank-accounts {bankCode, accountNumber, accountName}
    Note over API: JWT required – object-level: userId from token
    API->>BANK: linkAccount(userId, bankCode, accountNumber, accountName)
    BANK->>ADP: verifyAccount(bankCode, accountNumber, accountName) [SIMULATED]
    ADP-->>BANK: {verified: true, simulated: true}
    BANK->>DB: INSERT bank_accounts (status=ACTIVE, maskedNumber=****1234)
    BANK->>AUD: log BANK_ACCOUNT_LINKED(userId, bankCode, maskedNumber)
    BANK->>NOTIF: alert user "Bank account linked [SIMULATED]"
    BANK-->>API: 201 Created {bankAccountId, maskedNumber}
    API-->>U: 201 Created
```

---

## 4. Lookup Payee + Initiate Transfer

User enters a phone number, looks up the payee, reviews, and confirms a transfer.

```mermaid
sequenceDiagram
    actor U as User (Flutter)
    participant API as API Gateway / Security
    participant REG as registry
    participant TRF as transfer
    participant BANK as bank-integration
    participant ADP as BankAdapter (SIMULATED)
    participant NOTIF as notification
    participant AUD as audit
    participant DB as MySQL

    U->>API: GET /api/v1/registry/lookup?phone=07XXXXXXXX
    API->>REG: lookupByPhone(phone, requesterId)
    REG->>DB: find user where phone=? AND discoverable=true
    REG-->>API: 200 OK {payeeId, displayName, maskedPhone}
    API-->>U: display payee info for confirmation

    U->>API: POST /api/v1/transfers {payeeId, amount, currency=LKR, note}
    API->>TRF: initiateTransfer(senderId, payeeId, amount, currency)
    TRF->>TRF: validate amount > 0, within daily limit
    TRF->>DB: INSERT transfers (status=PENDING)
    TRF->>BANK: debit(senderBankAccountId, amount) [SIMULATED]
    BANK->>ADP: debit(...) [SIMULATED]
    ADP-->>BANK: {success: true, txRef: "SIM-xxx", simulated: true}
    BANK->>ADP: credit(payeeBankAccountId, amount) [SIMULATED]
    ADP-->>BANK: {success: true, txRef: "SIM-yyy", simulated: true}
    BANK-->>TRF: both legs confirmed
    TRF->>DB: UPDATE transfers SET status=COMPLETED
    TRF->>AUD: log TRANSFER_COMPLETED(senderId, payeeId, amount)
    TRF->>NOTIF: notify sender + payee [SIMULATED]
    TRF-->>API: 200 OK {transferId, status=COMPLETED, simulated: true}
    API-->>U: payment confirmed
```

---

## 5. QR Code Payment

Merchant displays a QR; payer scans it and confirms payment.

```mermaid
sequenceDiagram
    actor M as Merchant (Flutter)
    actor P as Payer (Flutter)
    participant API as API Gateway / Security
    participant QR as qr
    participant TRF as transfer
    participant NOTIF as notification
    participant AUD as audit
    participant DB as MySQL

    M->>API: POST /api/v1/qr/generate {amount, currency=LKR, note}
    API->>QR: generateQr(merchantId, amount, currency, note)
    QR->>DB: INSERT qr_codes (status=ACTIVE, expiresAt=+15min)
    QR-->>API: 200 OK {qrId, qrPayload, expiresAt}
    API-->>M: display QR code on screen

    P->>API: POST /api/v1/qr/pay {qrPayload}
    API->>QR: parseAndValidate(qrPayload, payerId)
    QR->>DB: fetch qr_codes where id=? AND status=ACTIVE AND expiresAt > now()
    QR->>TRF: initiateTransfer(payerId, merchantId, amount, currency, qrRef)
    Note over TRF: same debit/credit flow as Journey 4
    TRF-->>QR: {transferId, status=COMPLETED}
    QR->>DB: UPDATE qr_codes SET status=USED
    QR->>AUD: log QR_PAYMENT(payerId, merchantId, amount, qrId)
    QR->>NOTIF: notify merchant + payer [SIMULATED]
    QR-->>API: 200 OK {transferId, status=COMPLETED, simulated: true}
    API-->>P: payment confirmed
```

---

## 6. Disable Discovery (Privacy Control)

User opts out of phone-number lookup by other users.

```mermaid
sequenceDiagram
    actor U as User (Flutter)
    participant API as API Gateway / Security
    participant REG as registry
    participant AUD as audit
    participant DB as MySQL

    U->>API: PATCH /api/v1/registry/discovery {discoverable: false}
    Note over API: JWT required – userId from token
    API->>REG: setDiscoverable(userId, false)
    REG->>DB: UPDATE registry SET discoverable=false WHERE userId=?
    REG->>AUD: log DISCOVERY_DISABLED(userId, timestamp)
    REG-->>API: 200 OK {discoverable: false}
    API-->>U: discovery disabled
```

---

## 7. Admin: View & Freeze Account

Admin looks up a user and freezes their account.

```mermaid
sequenceDiagram
    actor A as Admin (Flutter Web)
    participant API as API Gateway / Security
    participant ADMIN as admin
    participant ID as identity
    participant AUD as audit
    participant DB as MySQL

    A->>API: GET /api/v1/admin/users?phone=07XXXXXXXX
    Note over API: JWT required – ROLE_ADMIN
    API->>ADMIN: searchUser(phone)
    ADMIN->>ID: findByPhone(phone)
    ID->>DB: SELECT users WHERE phone=?
    ID-->>ADMIN: userDetails (masked PII)
    ADMIN-->>API: 200 OK {userId, displayName, status}
    API-->>A: user record displayed

    A->>API: PATCH /api/v1/admin/users/{userId}/status {status: FROZEN, reason}
    API->>ADMIN: freezeUser(adminId, userId, reason)
    ADMIN->>ID: updateStatus(userId, FROZEN)
    ID->>DB: UPDATE users SET status=FROZEN
    ADMIN->>AUD: log ADMIN_FREEZE_USER(adminId, userId, reason, timestamp)
    ADMIN-->>API: 200 OK {userId, status=FROZEN}
    API-->>A: account frozen
```

# Lanka Pay – Backend Package & Folder Structure

> Exact Maven layout for the Spring Boot 3 / Java 21 backend.
> All source lives under `backend/src/main/java/com/lankapay/`.

---

## Maven Project Layout

```
backend/
├── pom.xml
├── .mvn/
│   └── wrapper/
│       ├── maven-wrapper.jar
│       └── maven-wrapper.properties
├── mvnw
├── mvnw.cmd
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/lankapay/
│   │   │       │
│   │   │       ├── LankaPayApplication.java          ← @SpringBootApplication
│   │   │       │
│   │   │       ├── common/                           ← Shared kernel (no module deps)
│   │   │       │   ├── domain/
│   │   │       │   │   ├── Money.java                ← record Money(BigDecimal amount, String currency)
│   │   │       │   │   ├── UserId.java               ← typed wrapper
│   │   │       │   │   └── AccountId.java
│   │   │       │   ├── exception/
│   │   │       │   │   ├── LankaPayException.java
│   │   │       │   │   ├── ValidationException.java
│   │   │       │   │   ├── ResourceNotFoundException.java
│   │   │       │   │   └── AuthorizationException.java
│   │   │       │   ├── web/
│   │   │       │   │   ├── ErrorResponse.java        ← standard error body
│   │   │       │   │   └── GlobalExceptionHandler.java ← @RestControllerAdvice
│   │   │       │   └── util/
│   │   │       │       └── SimulatedResponseUtil.java ← stamps simulated:true
│   │   │       │
│   │   │       ├── identity/                         ── MODULE: identity
│   │   │       │   ├── domain/
│   │   │       │   │   ├── User.java                 ← @Entity idn_users
│   │   │       │   │   ├── UserStatus.java           ← enum
│   │   │       │   │   ├── UserRole.java             ← enum
│   │   │       │   │   ├── Otp.java                  ← @Entity idn_otps
│   │   │       │   │   ├── OtpPurpose.java           ← enum
│   │   │       │   │   └── RefreshToken.java         ← @Entity idn_refresh_tokens
│   │   │       │   ├── repository/
│   │   │       │   │   ├── UserRepository.java
│   │   │       │   │   ├── OtpRepository.java
│   │   │       │   │   └── RefreshTokenRepository.java
│   │   │       │   ├── service/
│   │   │       │   │   ├── UserService.java
│   │   │       │   │   ├── OtpService.java
│   │   │       │   │   └── AuthService.java
│   │   │       │   ├── web/
│   │   │       │   │   ├── AuthController.java       ← /api/v1/auth/*
│   │   │       │   │   ├── RegisterRequest.java
│   │   │       │   │   ├── VerifyOtpRequest.java
│   │   │       │   │   ├── LoginRequest.java
│   │   │       │   │   └── TokenResponse.java
│   │   │       │   └── security/
│   │   │       │       ├── JwtService.java
│   │   │       │       ├── JwtAuthFilter.java
│   │   │       │       └── SecurityConfig.java       ← @Configuration @EnableWebSecurity
│   │   │       │
│   │   │       ├── registry/                         ── MODULE: registry
│   │   │       │   ├── domain/
│   │   │       │   │   └── Registry.java             ← @Entity reg_registry
│   │   │       │   ├── repository/
│   │   │       │   │   └── RegistryRepository.java
│   │   │       │   ├── service/
│   │   │       │   │   └── RegistryService.java
│   │   │       │   └── web/
│   │   │       │       ├── RegistryController.java   ← /api/v1/registry/*
│   │   │       │       ├── LookupResponse.java
│   │   │       │       └── DiscoveryRequest.java
│   │   │       │
│   │   │       ├── bank/                             ── MODULE: bank-integration
│   │   │       │   ├── adapter/
│   │   │       │   │   ├── BankAdapter.java          ← interface (the contract)
│   │   │       │   │   ├── BankAdapterRequest.java
│   │   │       │   │   ├── BankAdapterResponse.java  ← always has simulated field
│   │   │       │   │   └── simulated/
│   │   │       │   │       └── SimulatedBankAdapter.java  ← SIMULATED impl
│   │   │       │   ├── domain/
│   │   │       │   │   ├── BankAccount.java          ← @Entity bnk_bank_accounts
│   │   │       │   │   └── BankAccountStatus.java    ← enum
│   │   │       │   ├── repository/
│   │   │       │   │   └── BankAccountRepository.java
│   │   │       │   ├── service/
│   │   │       │   │   └── BankAccountService.java
│   │   │       │   └── web/
│   │   │       │       ├── BankAccountController.java ← /api/v1/bank-accounts/*
│   │   │       │       ├── LinkBankAccountRequest.java
│   │   │       │       └── BankAccountResponse.java
│   │   │       │
│   │   │       ├── transfer/                         ── MODULE: transfer
│   │   │       │   ├── domain/
│   │   │       │   │   ├── Transfer.java             ← @Entity trf_transfers
│   │   │       │   │   ├── TransferStatus.java       ← enum
│   │   │       │   │   ├── TransferInitiatedVia.java ← enum APP | QR
│   │   │       │   │   └── DailyLimit.java           ← @Entity trf_daily_limits
│   │   │       │   ├── repository/
│   │   │       │   │   ├── TransferRepository.java
│   │   │       │   │   └── DailyLimitRepository.java
│   │   │       │   ├── service/
│   │   │       │   │   ├── TransferService.java
│   │   │       │   │   └── DailyLimitService.java
│   │   │       │   └── web/
│   │   │       │       ├── TransferController.java   ← /api/v1/transfers/*
│   │   │       │       ├── InitiateTransferRequest.java
│   │   │       │       └── TransferResponse.java
│   │   │       │
│   │   │       ├── qr/                               ── MODULE: qr
│   │   │       │   ├── domain/
│   │   │       │   │   ├── QrCode.java               ← @Entity qr_codes
│   │   │       │   │   └── QrStatus.java             ← enum
│   │   │       │   ├── repository/
│   │   │       │   │   └── QrCodeRepository.java
│   │   │       │   ├── service/
│   │   │       │   │   └── QrService.java
│   │   │       │   └── web/
│   │   │       │       ├── QrController.java         ← /api/v1/qr/*
│   │   │       │       ├── GenerateQrRequest.java
│   │   │       │       ├── GenerateQrResponse.java
│   │   │       │       └── QrPayRequest.java
│   │   │       │
│   │   │       ├── notification/                     ── MODULE: notification
│   │   │       │   ├── domain/
│   │   │       │   │   ├── Notification.java         ← @Entity ntf_notifications
│   │   │       │   │   ├── NotificationChannel.java  ← enum SMS | PUSH
│   │   │       │   │   └── NotificationType.java     ← enum
│   │   │       │   ├── repository/
│   │   │       │   │   └── NotificationRepository.java
│   │   │       │   ├── service/
│   │   │       │   │   ├── NotificationService.java  ← public API used by other modules
│   │   │       │   │   ├── SmsChannel.java           ← interface
│   │   │       │   │   ├── PushChannel.java          ← interface
│   │   │       │   │   └── simulated/
│   │   │       │   │       ├── SimulatedSmsChannel.java   ← SIMULATED
│   │   │       │   │       └── SimulatedPushChannel.java  ← SIMULATED
│   │   │       │   └── scheduler/
│   │   │       │       └── NotificationPurgeJob.java ← @Scheduled – 90-day purge
│   │   │       │
│   │   │       ├── audit/                            ── MODULE: audit
│   │   │       │   ├── domain/
│   │   │       │   │   └── AuditLog.java             ← @Entity aud_audit_log
│   │   │       │   ├── repository/
│   │   │       │   │   └── AuditLogRepository.java
│   │   │       │   └── service/
│   │   │       │       └── AuditService.java         ← append-only; no delete methods
│   │   │       │
│   │   │       └── admin/                            ── MODULE: admin
│   │   │           ├── service/
│   │   │           │   └── AdminService.java
│   │   │           └── web/
│   │   │               ├── AdminUserController.java  ← /api/v1/admin/users/*
│   │   │               ├── AdminTransferController.java ← /api/v1/admin/transfers/*
│   │   │               └── AdminAuditController.java ← /api/v1/admin/audit-log
│   │   │
│   │   └── resources/
│   │       ├── application.yml                       ← reads from env vars
│   │       ├── application-local.yml                 ← gitignored local overrides
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__init_schema.sql           ← all tables (Flyway baseline)
│   │               ├── V2__seed_admin_user.sql       ← default admin account
│   │               └── ...                           ← future migrations
│   │
│   └── test/
│       └── java/
│           └── com/lankapay/
│               ├── identity/
│               │   ├── AuthControllerTest.java       ← @WebMvcTest
│               │   ├── UserServiceTest.java          ← unit test
│               │   └── OtpServiceTest.java
│               ├── registry/
│               │   └── RegistryServiceTest.java
│               ├── bank/
│               │   ├── BankAccountControllerTest.java
│               │   └── SimulatedBankAdapterTest.java
│               ├── transfer/
│               │   ├── TransferServiceTest.java
│               │   └── TransferControllerTest.java
│               ├── qr/
│               │   └── QrServiceTest.java
│               └── common/
│                   └── MoneyTest.java
│
└── docker-compose.yml                                ← MySQL 8 for local dev
```

---

## Key File Notes

| File | Purpose |
|---|---|
| `LankaPayApplication.java` | Entry point; `@SpringBootApplication` scans `com.lankapay` |
| `SecurityConfig.java` | Defines filter chain, public routes, role guards, CORS |
| `JwtAuthFilter.java` | Extracts and validates Bearer token on every request |
| `BankAdapter.java` | The only interface any code outside `bank` may call for bank ops |
| `SimulatedBankAdapter.java` | Always returns success with `simulated: true`; labelled SIMULATED |
| `AuditService.java` | Exposes only `log(AuditEvent)` — no read or delete methods |
| `GlobalExceptionHandler.java` | Maps all exceptions to `ErrorResponse`; never leaks stack traces |
| `SimulatedResponseUtil.java` | Stamps `"simulated": true` on every bank/SMS/push response object |
| `V1__init_schema.sql` | Full DDL for all tables; Flyway runs this on first startup |

---

## Naming Conventions

| Layer | Convention | Example |
|---|---|---|
| Entity | PascalCase, no suffix | `User`, `Transfer` |
| Repository | `<Entity>Repository` | `TransferRepository` |
| Service | `<Entity>Service` or `<Feature>Service` | `AuthService`, `DailyLimitService` |
| Controller | `<Feature>Controller` | `AuthController` |
| Request DTO | `<Action><Resource>Request` | `InitiateTransferRequest` |
| Response DTO | `<Resource>Response` | `TransferResponse` |
| Enum | PascalCase, values UPPER_SNAKE | `TransferStatus.COMPLETED` |
| DB table | `<module_prefix>_<plural_noun>` | `trf_transfers`, `idn_users` |
| Flyway migration | `V{n}__{snake_description}.sql` | `V3__add_qr_codes_index.sql` |

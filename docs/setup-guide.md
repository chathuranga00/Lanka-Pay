# LankaPay ID — Local Development & Setup Guide

This guide provides end-to-end instructions for running the complete LankaPay ID ecosystem on a local development machine, covering the MySQL database, Spring Boot backend, Flutter mobile client, and React admin portal.

---

## 1. Prerequisites & Environment Requirements

Ensure the following tools are installed on your workstation:

| Tool | Recommended Version | Verification Command | Purpose |
| :--- | :--- | :--- | :--- |
| **Java JDK** | 21 LTS (OpenJDK / Eclipse Temurin) | `java -version` | Backend runtime & compiler |
| **Maven** | 3.9+ (or use bundled `./mvnw`) | `mvn -v` | Backend dependency & build tool |
| **Node.js** | 18.x or 20.x LTS | `node -v` | Admin web portal runtime |
| **npm** | 9.x or 10.x | `npm -v` | Admin web package manager |
| **Flutter SDK** | 3.22.x+ (Dart 3.4+) | `flutter doctor` | Mobile app cross-platform SDK |
| **MySQL** | 8.0+ (or Docker Engine) | `mysql --version` / `docker -v` | Relational persistence |
| **Git** | 2.40+ | `git --version` | Version control |

---

## 2. Database Setup (MySQL 8)

LankaPay ID uses MySQL 8 with automated schema provisioning managed by Flyway.

### Option A: Using Docker (Recommended)
Run a dedicated MySQL 8 container with UTF-8mb4 collation:
```bash
docker run -d \
  --name lankapay-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=lankapay_db \
  -e MYSQL_USER=lankapay_user \
  -e MYSQL_PASSWORD=lankapay_pass \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

### Option B: Native MySQL Installation
Log into your local MySQL CLI and execute:
```sql
CREATE DATABASE IF NOT EXISTS lankapay_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'lankapay_user'@'localhost' IDENTIFIED BY 'lankapay_pass';
GRANT ALL PRIVILEGES ON lankapay_db.* TO 'lankapay_user'@'localhost';
FLUSH PRIVILEGES;
```

---

## 3. Environment Variables Configuration

Copy the example environment template at the repository root:
```bash
cp .env.example .env
```

### Key Configuration Properties
Review and adjust values in `.env` as appropriate:

```env
# ── Database ─────────────────────────────────────────────────────────────
DB_HOST=localhost
DB_PORT=3306
DB_NAME=lankapay_db
DB_USERNAME=lankapay_user
DB_PASSWORD=lankapay_pass

# ── JWT Secrets (Minimum 256-bit / 32 characters in production) ──────────
JWT_SECRET=c2VjdXJlLWx5LXJhbmRvbS1rZXktZm9yLXVuaXZlcnNpdHktcHJvdG90eXBlLTIwMjY=
JWT_ACCESS_EXPIRATION_MS=900000         # 15 minutes
JWT_REFRESH_EXPIRATION_MS=604800000     # 7 days

# ── Data Encryption at Rest ──────────────────────────────────────────────
AES_SECRET_KEY=32-character-secret-key-1234567890

# ── Simulation & Banking Mode ───────────────────────────────────────────
BANK_ADAPTER_MODE=SIMULATED            # Set to SIMULATED for test mode
SIMULATED_DATA_SEEDING=true            # Automatically seed Kasun, Nimal & admin

# ── Security & Abuse Prevention ─────────────────────────────────────────
RATE_LIMIT_USER_PER_MIN=10
RATE_LIMIT_IP_PER_MIN=20
OTP_MAX_ATTEMPTS=5
DAILY_DEFAULT_LIMIT=100000.00
```

---

## 4. Backend Service Setup (Spring Boot)

The backend service is located in `backend/`.

### 1. Build and Run
```bash
cd backend

# Build without running tests:
mvn clean compile

# Run the Spring Boot application:
mvn spring-boot:run
```
*(Alternatively, on Unix/macOS: `./mvnw spring-boot:run`, on Windows: `mvnw.cmd spring-boot:run`)*

### 2. Verify Application Startup
Upon startup, Flyway executes all pending schema migrations in `src/main/resources/db/migration/`. If `BANK_ADAPTER_MODE=SIMULATED`, [`SimulatedDataSeeder`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/bank/adapter/simulated/SimulatedDataSeeder.java) populates the sample test accounts.

- **Base URL:** `http://localhost:8080/api/v1`
- **Swagger / OpenAPI UI:** `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON Spec:** `http://localhost:8080/v3/api-docs`
- **Health Endpoint:** `http://localhost:8080/actuator/health`

### 3. Run Backend Test Suite
```bash
# Execute all 318 unit, integration, security, and load tests:
mvn test
```

---

## 5. Mobile Client Setup (Flutter)

The mobile application is located in `mobile/`.

### 1. Install Dependencies
```bash
cd mobile
flutter pub get
```

### 2. Run the App
Launch the app with the local backend base URL passed as a `--dart-define` parameter:

#### Chrome / Web Preview:
```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```

#### Android Emulator:
*Note: Android emulators map `http://10.0.2.2:8080` to the host machine's localhost.*
```bash
flutter run -d android --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
```

#### Physical Android / iOS Device:
Connect your mobile device to the same Wi-Fi network as your host and use your computer's local IP address:
```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.100:8080/api/v1
```

### 3. Run Mobile Widget & Feature Tests
```bash
flutter test
```

---

## 6. Admin Web Portal Setup (React + Vite)

The administrative web portal is located in `admin-web/`.

### 1. Install Dependencies
```bash
cd admin-web
npm install
```

### 2. Start the Development Server
```bash
npm run dev
```
The application will launch at `http://localhost:5173/`.

### 3. Log into Admin Portal
- **URL:** `http://localhost:5173/`
- **Admin Phone Number:** `+94770000000` (or `0770000000`)
- **Default Admin PIN:** `123456`

---

## 7. Pre-Seeded Test Credentials

When running in `SIMULATED` mode, the backend seeds the following accounts:

| User | Mobile Number | NIC | PIN | Linked Accounts & Default |
| :--- | :--- | :--- | :--- | :--- |
| **Kasun Perera** | `+94770000001` | `199012345678` | `123456` | **Bank A:** `100100` (`150,000.00 LKR`)<br>**Bank B (Default):** `100101` (`75,000.00 LKR`)<br>**Bank C:** `100102` (`25,000.00 LKR`) |
| **Nimal Silva** | `+94770000002` | `198598765432` | `123456` | **Bank A (Default):** `100201` (`50,000.00 LKR`) |
| **Scenario Fail** | `+94770000003` | `199211112222` | `123456` | **Bank A:** `100400` (`50.00 LKR`) — Triggers `INSUFFICIENT_FUNDS` |
| **Scenario Delay** | `+94770000004` | `199333334444` | `123456` | **Bank A:** `100200` — Triggers delayed settlement |
| **Scenario Reversal** | `+94770000005` | `199455556666` | `123456` | **Bank A:** `100500` — Triggers settlement reversal |
| **System Admin** | `+94770000000` | `198000000000` | `123456` | Admin Role (`ROLE_ADMIN`) |

---

## 8. Troubleshooting & Common Issues

### MySQL Connection Refused (`CommunicationsException`)
- Ensure the MySQL service or Docker container is active and listening on port 3306.
- Verify credentials by connecting via terminal: `mysql -u lankapay_user -p -h localhost lankapay_db`.

### Mobile App Cannot Reach Backend (`SocketException` / `Connection refused`)
- When running on an Android emulator, `localhost` refers to the emulator itself. Always use `--dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1`.
- On physical devices, verify that firewall rules allow inbound TCP traffic on port 8080.

### CORS Errors in Admin Web
- Verify that `app.security.cors-allowed-origins` in `backend/src/main/resources/application.yml` contains `http://localhost:5173`.

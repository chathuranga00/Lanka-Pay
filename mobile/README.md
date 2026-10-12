# LankaPay ID — Mobile Client (Flutter)

The official cross-platform mobile client for the **LankaPay ID** national proxy payment identifier platform, built with **Flutter 3.22+** and **Dart 3.4+**.

---

## 🎨 Design System & Modern UI Tokens

The mobile UI follows a **Neo-Banking Design System** tailored for high security, accessibility, and visual elegance:

- **Tokens:** Centralized in [`lib/core/theme/app_tokens.dart`](lib/core/theme/app_tokens.dart) and [`lib/core/theme/app_theme.dart`](lib/core/theme/app_theme.dart).
- **Primary Colors:** Deep Violet (`#2D1B69` to `#1F104F` gradients) with soft lavender muted cards (`#F5F3FF`) in Light mode; Deep Charcoal (`#12121A`) with surface cards (`#1E1E2A`) in Dark mode.
- **Accents:** Emerald Green for verified payments and status confirmation; Amber for simulated test environment badges.
- **Shared Components:**
  - `GradientHeaderScaffold`: Cohesive top gradient header with rounded bottom card view (28–32 dp radius).
  - `AccountCard`: Carousel cards with subtle geometric hexagon textures, masked account toggles, and frosted freeze overlays.
  - `QuickActionTile` & `MenuTile`: 48 dp+ tap target action buttons with haptic feedback.
  - `TransactionTile`: Verified recipient initial avatars, amount styling, and timestamp metadata.
  - `ShimmerSkeleton`: Custom shimmer animations during network loading states.
- **Accessibility:**
  - Minimum 48 dp tap targets across all interactive controls.
  - WCAG AA contrast compliance in both light and dark themes.
  - Semantics labels and tooltips on all icon-only buttons.
  - 200% text scaling without layout overflow.
  - Honors system *Reduce Motion* settings.

---

## 📱 Visual Regression Golden Baseline Screenshots

Visual regression baselines are stored in `test/goldens/`:

| Screen | Light Theme | Dark Theme |
| :--- | :--- | :--- |
| **Home Dashboard** | `test/goldens/home_screen_light.png` | `test/goldens/home_screen_dark.png` |
| **Accounts Management** | `test/goldens/accounts_screen_light.png` | `test/goldens/accounts_screen_dark.png` |
| **Send & Transfer** | `test/goldens/send_screen_light.png` | `test/goldens/send_screen_dark.png` |

---

## 🧪 Testing Suite & Quality Assurance

The mobile test suite contains **91 automated tests** across **10 test files** with 100% pass rate:

```bash
# 1. Run static analysis (asserts 0 issues)
flutter analyze

# 2. Run all unit, widget, and navigation tests
flutter test

# 3. Update golden screenshot baselines if design tokens are intentionally altered
flutter test --update-goldens test/screens_golden_test.dart
```

### Test Coverage Highlights:
- `test/all_keys_tap_test.dart`: Complete E2E tap test asserting every registered key in `AppTestKeys` across Home, Accounts, Send, and More screens.
- `test/screens_golden_test.dart`: Pixel-accurate golden regression tests in both Light and Dark themes.
- `test/home_screen_test.dart`: Dashboard cards, balance masking toggles, quick actions, pull-to-refresh.
- `test/accounts_screen_test.dart`: Account carousel, freeze/pause toggles, default bank selection, and linking flows.
- `test/send_screen_redesign_test.dart`: Recipient phone lookup, bank account dropdown, amount input, and transfer execution.
- `test/qr_history_notifs_test.dart`: Dynamic & static LankaQR generation, transaction history filtering, tamper-evident receipts.
- `test/design_system_test.dart`: Design system widgets, tokens, buttons, and theme contrast.

For manual QA procedures, see the [Mobile UI QA Checklist](../docs/ui-qa-checklist.md).

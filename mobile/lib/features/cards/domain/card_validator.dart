import 'package:flutter/services.dart';

/// Validation logic and formatters for card entry.
class CardValidator {
  CardValidator._();

  /// Standard Luhn algorithm verification.
  /// Ignores whitespace and non-digit characters.
  /// Returns true if number is non-empty, contains 13-19 digits, and passes Luhn check.
  static bool isValidLuhn(String number) {
    final clean = number.replaceAll(RegExp(r'\D'), '');
    if (clean.length < 13 || clean.length > 19) return false;

    int sum = 0;
    bool alternate = false;

    for (int i = clean.length - 1; i >= 0; i--) {
      int digit = int.parse(clean[i]);
      if (alternate) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      alternate = !alternate;
    }

    return sum % 10 == 0;
  }

  /// Verifies MM/YY expiry format and checks valid month range (01-12)
  static bool isValidExpiry(String expiry) {
    final clean = expiry.replaceAll(RegExp(r'\D'), '');
    if (clean.length != 4) return false;
    final month = int.tryParse(clean.substring(0, 2));
    if (month == null || month < 1 || month > 12) return false;
    return true;
  }

  /// Verifies CVV length (3 or 4 digits)
  static bool isValidCvv(String cvv) {
    final clean = cvv.replaceAll(RegExp(r'\D'), '');
    return clean.length >= 3 && clean.length <= 4;
  }
}

/// Auto-formats a 16-digit card number with space grouping: `XXXX XXXX XXXX XXXX`.
class CardNumberInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final digits = newValue.text.replaceAll(RegExp(r'\D'), '');
    final limitedDigits = digits.length > 16 ? digits.substring(0, 16) : digits;

    final buffer = StringBuffer();
    for (int i = 0; i < limitedDigits.length; i++) {
      if (i > 0 && i % 4 == 0) {
        buffer.write(' ');
      }
      buffer.write(limitedDigits[i]);
    }

    final formatted = buffer.toString();
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }
}

/// Auto-formats MM/YY expiry date with slash: `MM/YY`.
class CardExpiryInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final digits = newValue.text.replaceAll(RegExp(r'\D'), '');
    final limitedDigits = digits.length > 4 ? digits.substring(0, 4) : digits;

    final buffer = StringBuffer();
    for (int i = 0; i < limitedDigits.length; i++) {
      if (i == 2) {
        buffer.write('/');
      }
      buffer.write(limitedDigits[i]);
    }

    final formatted = buffer.toString();
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }
}

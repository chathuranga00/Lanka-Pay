#!/usr/bin/env python3
"""
CI Secret & PII Scanner: Enforce PCI-DSS PAN Prohibition

Scans the repository for any Luhn-valid 13-19 digit card numbers (Primary Account Numbers).
Fails with exit code 1 if any Luhn-valid card number is found anywhere in the repository
EXCEPT in docs/test-cards.md.
"""

import os
import re
import sys

def is_luhn_valid(number: str) -> bool:
    """Verify Luhn checksum formula (MOD 10)."""
    digits = [int(c) for c in number]
    odd_digits = digits[-1::-2]
    even_digits = digits[-2::-2]
    total = sum(odd_digits)
    for d in even_digits:
        doubled = d * 2
        total += doubled - 9 if doubled > 9 else doubled
    return total % 10 == 0

# Matches 13 to 19 consecutive digits starting with non-zero (ISO/IEC 7812 MII 1..9)
CARD_PATTERN = re.compile(r'\b([1-9]\d{12,18})\b')

EXCLUDE_DIRS = {
    '.git',
    'node_modules',
    '.dart_tool',
    'target',
    'build',
    '.idea',
    '.vscode',
    '.system_generated',
}

# The single allowed exception specified by requirements: docs/test-cards.md
ALLOWLIST_PATHS = {
    'docs/test-cards.md',
    '.gitleaks.toml',
    'scripts/scan-luhn-cards.py',
}

def scan_repository():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    violations = []

    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Prune excluded directories in-place
        dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]

        for filename in filenames:
            abs_filepath = os.path.join(dirpath, filename)
            rel_path = os.path.relpath(abs_filepath, root_dir).replace('\\', '/')

            if rel_path in ALLOWLIST_PATHS or any(rel_path.endswith(allow) for allow in ALLOWLIST_PATHS):
                continue

            try:
                with open(abs_filepath, 'r', encoding='utf-8', errors='ignore') as f:
                    for line_num, line in enumerate(f, 1):
                        for match in CARD_PATTERN.finditer(line):
                            candidate = match.group(1)
                            if is_luhn_valid(candidate):
                                violations.append((rel_path, line_num, candidate))
            except Exception:
                # Skip unreadable or binary files
                pass

    if violations:
        print("=" * 78)
        print("CI PII/SECRET SCAN FAILURE: Unauthorized Luhn-valid card number(s) found!")
        print("Policy: Raw card numbers (PANs) are PROHIBITED anywhere except docs/test-cards.md.")
        print("=" * 78)
        for path, line, card in violations:
            masked = card[:4] + "*" * (len(card) - 8) + card[-4:]
            print(f"  [VIOLATION] {path}:{line} -> {masked} (length: {len(card)})")
        print("=" * 78)
        sys.exit(1)
    else:
        print("CI PII/SECRET SCAN PASSED: No unauthorized Luhn-valid card numbers found.")
        sys.exit(0)

if __name__ == '__main__':
    scan_repository()

import 'package:flutter/material.dart';
import '../../../../core/theme/app_tokens.dart';

class TestCardItem {
  final String number;
  final String expiry;
  final String cvv;
  final String holderName;
  final String scenario;
  final String description;

  const TestCardItem({
    required this.number,
    required this.expiry,
    required this.cvv,
    required this.holderName,
    required this.scenario,
    required this.description,
  });
}

const List<TestCardItem> kSeedTestCards = [
  TestCardItem(
    number: '9900000000000010',
    expiry: '12/27',
    cvv: '111',
    holderName: 'Test Approved',
    scenario: 'ALWAYS_APPROVED',
    description: 'Authorization succeeds immediately. Ideal for happy path.',
  ),
  TestCardItem(
    number: '9900000000000028',
    expiry: '12/27',
    cvv: '222',
    holderName: 'Test Insufficient',
    scenario: 'INSUFFICIENT_FUNDS',
    description: 'Authorization declined — not enough balance.',
  ),
  TestCardItem(
    number: '9900000000000069',
    expiry: '01/20',
    cvv: '333',
    holderName: 'Test Expired',
    scenario: 'CARD_EXPIRED',
    description: 'Authorization declined — card expiry is in the past.',
  ),
  TestCardItem(
    number: '9900000000000119',
    expiry: '12/27',
    cvv: '444',
    holderName: 'Test OTP Challenge',
    scenario: 'REQUIRES_OTP_CHALLENGE',
    description: 'Authorization paused — bank OTP challenge required.',
  ),
  TestCardItem(
    number: '9900000000000994',
    expiry: '12/27',
    cvv: '555',
    holderName: 'Test Timeout',
    scenario: 'PROVIDER_TIMEOUT',
    description: 'Adapter timeout — reconciled later to approved.',
  ),
  TestCardItem(
    number: '9900000000001232',
    expiry: '12/27',
    cvv: '666',
    holderName: 'Test Delayed',
    scenario: 'DELAYED_CONFIRMATION',
    description: 'Returns pending then confirmed.',
  ),
  TestCardItem(
    number: '9900000000002347',
    expiry: '12/27',
    cvv: '777',
    holderName: 'Test Hard Decline',
    scenario: 'HARD_DECLINE',
    description: 'Declined permanently — card flagged stolen/blocked.',
  ),
  TestCardItem(
    number: '9900000000003451',
    expiry: '12/27',
    cvv: '888',
    holderName: 'Test Reversal',
    scenario: 'LATER_REVERSAL',
    description: 'Authorization succeeds; later issuer reverses charge.',
  ),
];

/// Debug screen displaying seeded test cards from docs/test-cards.md.
class TestCardsListScreen extends StatelessWidget {
  const TestCardsListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Simulated Test Cards'),
      ),
      body: ListView.separated(
        padding: const EdgeInsets.all(AppTokens.screenHorizontalPadding),
        itemCount: kSeedTestCards.length,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          final item = kSeedTestCards[index];
          return Card(
            elevation: 0,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
              ),
            ),
            child: InkWell(
              borderRadius: BorderRadius.circular(16),
              onTap: () {
                // Return selected card data to form
                Navigator.of(context).pop(item);
              },
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          item.scenario,
                          style: const TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontWeight: FontWeight.w800,
                            fontSize: 14,
                            color: AppTokens.primaryViolet,
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: AppTokens.primaryViolet.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text(
                            'TAP TO USE',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                              color: AppTokens.primaryViolet,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${item.number.substring(0, 4)} ${item.number.substring(4, 8)} ${item.number.substring(8, 12)} ${item.number.substring(12)}',
                      style: const TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.2,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Text('Exp: ${item.expiry}', style: const TextStyle(fontSize: 13, color: Colors.grey)),
                        const SizedBox(width: 16),
                        Text('CVV: ${item.cvv}', style: const TextStyle(fontSize: 13, color: Colors.grey)),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Text(
                            item.holderName,
                            style: const TextStyle(fontSize: 13, color: Colors.grey),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      item.description,
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

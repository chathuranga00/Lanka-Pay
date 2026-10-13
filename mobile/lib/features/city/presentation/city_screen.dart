import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/section_header.dart';

/// City Tab (Tab 2: City).
/// Discover local merchants accepting LankaQR, categories, and exclusive offers.
class CityScreen extends ConsumerWidget {
  const CityScreen({super.key = const Key('city_screen')});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final categories = [
      {'name': 'All Places', 'icon': Icons.explore_rounded},
      {'name': 'Supermarket', 'icon': Icons.shopping_basket_rounded},
      {'name': 'Cafes & Dining', 'icon': Icons.local_cafe_rounded},
      {'name': 'Fashion & Retail', 'icon': Icons.checkroom_rounded},
      {'name': 'Health & Care', 'icon': Icons.spa_rounded},
    ];

    final merchants = [
      {
        'name': 'Keells Super',
        'category': 'Supermarket • Colombo 03',
        'rating': '4.8',
        'discount': '10% OFF with LankaQR',
      },
      {
        'name': 'Cargills Food City',
        'category': 'Daily Essentials • Islandwide',
        'rating': '4.7',
        'discount': 'LKR 250 Cash Back',
      },
      {
        'name': 'Colombo Coffee Club',
        'category': 'Artisanal Cafe • Colombo 07',
        'rating': '4.9',
        'discount': '15% Off Beverages',
      },
      {
        'name': 'Odel Department Store',
        'category': 'Fashion & Lifestyle • Alexandra Place',
        'rating': '4.6',
        'discount': 'Special Card Privilege',
      },
      {
        'name': 'Spa Ceylon Luxury Ayurveda',
        'category': 'Wellness & Care • Galle Face',
        'rating': '4.9',
        'discount': 'Points Multiplier 2x',
      },
    ];

    return Scaffold(
      key: const Key('city_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'City & Merchants',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 1. Categories Horizontal Row
          SizedBox(
            height: 42,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: categories.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final cat = categories[index];
                final isSelected = index == 0;
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? AppTokens.primaryBlue
                        : (isDark ? AppTokens.cardDark : AppTokens.tileTintLight),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        cat['icon'] as IconData,
                        size: 16,
                        color: isSelected
                            ? Colors.white
                            : (isDark ? Colors.white70 : AppTokens.primaryBlue),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        cat['name'] as String,
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: isSelected
                              ? Colors.white
                              : (isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 24),

          // 2. Featured LankaQR Places
          const SectionHeader(title: 'PLACES ACCEPTING LANKAQR'),
          const SizedBox(height: 12),
          ...merchants.map((m) {
            return Container(
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: isDark ? AppTokens.cardDark : Colors.white,
                borderRadius: BorderRadius.circular(20),
                boxShadow: isDark ? [] : AppTokens.softShadowLight,
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                  width: 0.8,
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: AppTokens.tileTintLight,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Icon(
                      Icons.storefront_rounded,
                      color: AppTokens.primaryBlue,
                      size: 26,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                m['name']!,
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                  color: isDark
                                      ? AppTokens.textPrimaryDark
                                      : AppTokens.textPrimaryLight,
                                ),
                              ),
                            ),
                            Row(
                              children: [
                                const Icon(Icons.star_rounded, size: 16, color: Colors.amber),
                                const SizedBox(width: 2),
                                Text(
                                  m['rating']!,
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(
                          m['category']!,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 12,
                            color: isDark
                                ? AppTokens.textSecondaryDark
                                : AppTokens.textSecondaryLight,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: AppTokens.nudgeBannerBgLight,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            m['discount']!,
                            style: const TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: AppTokens.nudgeBannerTextLight,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }
}

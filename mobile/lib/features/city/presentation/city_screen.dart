import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/section_header.dart';
import '../data/city_service.dart';
import '../domain/city_models.dart';
import 'merchant_detail_screen.dart';

/// City Tab: Local Merchants & In-Store Discovery.
/// Features search, category chips, offers row, merchant list cards with LankaQR.
class CityScreen extends ConsumerWidget {
  const CityScreen({super.key = const Key('city_screen')});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final selectedCategory = ref.watch(selectedCityCategoryProvider);
    final merchants = ref.watch(filteredCityMerchantsProvider);
    final offers = ref.watch(cityOffersProvider);

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
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // 1. Search Bar
          Container(
            height: 48,
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                width: 0.8,
              ),
              boxShadow: isDark ? [] : AppTokens.softShadowLight,
            ),
            child: TextField(
              key: TestKeys.citySearchInput,
              onChanged: (val) {
                ref.read(citySearchQueryProvider.notifier).setQuery(val);
              },
              decoration: InputDecoration(
                hintText: 'Search simulated merchants, areas, cafes...',
                hintStyle: TextStyle(
                  fontSize: 13,
                  color: isDark ? Colors.white38 : Colors.black38,
                ),
                prefixIcon: const Icon(Icons.search_rounded, size: 20, color: AppTokens.primaryBlue),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(vertical: 14),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 2. Category Chips
          SizedBox(
            height: 40,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: CityCategoryType.values.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final cat = CityCategoryType.values[index];
                final isSelected = selectedCategory == cat;
                return InkWell(
                  key: TestKeys.cityCat(cat.key),
                  borderRadius: BorderRadius.circular(14),
                  onTap: () {
                    ref.read(selectedCityCategoryProvider.notifier).select(cat);
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? AppTokens.primaryBlue
                          : (isDark ? AppTokens.cardDark : AppTokens.tileTintLight),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          cat.icon,
                          size: 16,
                          color: isSelected
                              ? Colors.white
                              : (isDark ? Colors.white70 : AppTokens.primaryBlue),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          cat.label,
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
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 20),

          // 3. "Offers for you" Horizontal Row
          if (offers.isNotEmpty) ...[
            const SectionHeader(title: 'OFFERS FOR YOU'),
            const SizedBox(height: 10),
            SizedBox(
              key: TestKeys.cityOffersRow,
              height: 110,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: offers.length,
                separatorBuilder: (_, __) => const SizedBox(width: 12),
                itemBuilder: (context, index) {
                  final offer = offers[index];
                  return Container(
                    width: 250,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: isDark ? AppTokens.cardDark : Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: AppTokens.primaryBlue.withOpacity(0.2),
                        width: 1,
                      ),
                      boxShadow: isDark ? [] : AppTokens.softShadowLight,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.green.withOpacity(0.15),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                offer.discountLabel ?? '${offer.pointsMultiplier}x Points',
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                  color: Colors.green,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          offer.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          offer.description,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11,
                            color: isDark ? Colors.white60 : Colors.black54,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 20),
          ],

          // 4. Merchant List Cards
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const SectionHeader(title: 'MERCHANTS ACCEPTING LANKAQR'),
              Text(
                '${merchants.length} places',
                style: const TextStyle(fontSize: 12, color: Colors.grey, fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 12),

          if (merchants.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 40),
              child: Center(
                child: Column(
                  children: [
                    Icon(Icons.storefront_outlined, size: 48, color: Colors.grey.withOpacity(0.5)),
                    const SizedBox(height: 12),
                    const Text('No merchants found matching your filter'),
                  ],
                ),
              ),
            )
          else
            ...merchants.map((m) {
              return InkWell(
                key: TestKeys.merchant(m.id),
                borderRadius: BorderRadius.circular(20),
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => MerchantDetailScreen(merchant: m),
                    ),
                  );
                },
                child: Container(
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
                                    m.name,
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
                                IconButton(
                                  key: m.id == 'merch_1' ? TestKeys.merchantFav : null,
                                  padding: EdgeInsets.zero,
                                  constraints: const BoxConstraints(),
                                  icon: Icon(
                                    m.isFavourite ? Icons.favorite_rounded : Icons.favorite_border_rounded,
                                    size: 20,
                                    color: m.isFavourite ? Colors.redAccent : Colors.grey,
                                  ),
                                  onPressed: () {
                                    ref.read(cityMerchantsProvider.notifier).toggleFavourite(m.id);
                                  },
                                ),
                              ],
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${m.category} • ${m.area}',
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 12,
                                color: isDark
                                    ? AppTokens.textSecondaryDark
                                    : AppTokens.textSecondaryLight,
                              ),
                            ),
                            if (m.offers.isNotEmpty) ...[
                              const SizedBox(height: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppTokens.nudgeBannerBgLight,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  m.offers.first.discountLabel ?? m.offers.first.title,
                                  style: const TextStyle(
                                    fontFamily: AppTokens.fontFamily,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                    color: AppTokens.nudgeBannerTextLight,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }),
        ],
      ),
    );
  }
}

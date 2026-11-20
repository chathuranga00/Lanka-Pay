import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/city_models.dart';

/// 20 Simulated Merchants (no real brands, pure simulated Ceylon entities).
final List<CityMerchantItem> kInitialSimulatedMerchants = [
  const CityMerchantItem(
    id: 'merch_1',
    name: 'Cinnamon Leaf Bistro',
    category: 'Food',
    area: 'Colombo 03',
    description: 'Authentic contemporary Ceylon fusion dining, fresh juices, and specialty coffees.',
    openingHours: '07:30 - 23:00',
    qrReference: 'QR-CITY-MERCH-1',
    offers: [
      CityOfferItem(
        id: 'offer_1',
        merchantId: 'merch_1',
        title: '2.5x Points on Weekend Dining',
        description: 'Earn 2.5x simulated rewards points on all dine-in orders on weekends.',
        pointsMultiplier: 2.5,
        discountLabel: '10% OFF Bill',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_2',
    name: 'Metro Island Grocers',
    category: 'Groceries',
    area: 'Colombo 04',
    description: 'Fresh highland vegetables, farm dairy, tropical fruits, and household pantry essentials.',
    openingHours: '08:00 - 22:00',
    qrReference: 'QR-CITY-MERCH-2',
    offers: [
      CityOfferItem(
        id: 'offer_2',
        merchantId: 'merch_2',
        title: 'Double Points on Fresh Groceries',
        description: 'Earn 2x rewards on fresh fruits and local dairy purchases over LKR 2,500.',
        pointsMultiplier: 2.0,
        discountLabel: 'LKR 500 OFF',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_3',
    name: 'Ceylon Spark Fuel',
    category: 'Fuel',
    area: 'Colombo 07',
    description: 'Euro 4 petrol, auto diesel, rapid tyre air service, and 24/7 convenience kiosk.',
    openingHours: 'Open 24 Hours',
    qrReference: 'QR-CITY-MERCH-3',
    offers: [
      CityOfferItem(
        id: 'offer_3',
        merchantId: 'merch_3',
        title: '3x Simulated Points on Euro 4 Fuel',
        description: 'Fill up your vehicle and earn triple points on LankaQR settlements.',
        pointsMultiplier: 3.0,
        discountLabel: 'Free Tyre Air',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_4',
    name: 'Lanka Care Pharmacy',
    category: 'Health',
    area: 'Colombo 05',
    description: 'Dispensing prescription medicines, organic wellness supplements, and first aid care.',
    openingHours: '08:00 - 22:30',
    qrReference: 'QR-CITY-MERCH-4',
    offers: [
      CityOfferItem(
        id: 'offer_4',
        merchantId: 'merch_4',
        title: '2x Points on Wellness Products',
        description: 'Receive double rewards points on essential health and personal care items.',
        pointsMultiplier: 2.0,
        discountLabel: '15% OFF',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_5',
    name: 'Starlight Cinema Hub',
    category: 'Entertainment',
    area: 'Colombo 02',
    description: 'IMAX cinema experience, Dolby Atmos surround, recliner seats, and warm gourmet popcorn.',
    openingHours: '10:00 - 00:00',
    qrReference: 'QR-CITY-MERCH-5',
    offers: [
      CityOfferItem(
        id: 'offer_5',
        merchantId: 'merch_5',
        title: 'Movie Night Bonus Points',
        description: 'Earn 2x points when purchasing cinema tickets through city merchant payment.',
        pointsMultiplier: 2.0,
        discountLabel: 'Free Popcorn',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_6',
    name: 'Southern Silk & Cotton',
    category: 'Shopping',
    area: 'Colombo 03',
    description: 'Handloom batik shirts, breathable linen outfits, and artisan handcrafted accessories.',
    openingHours: '09:30 - 20:00',
    qrReference: 'QR-CITY-MERCH-6',
    offers: [
      CityOfferItem(
        id: 'offer_6',
        merchantId: 'merch_6',
        title: 'Handloom Heritage Bonus',
        description: 'Triple points on authentic cotton and handloom apparel purchases.',
        pointsMultiplier: 3.0,
        discountLabel: '20% OFF',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_7',
    name: 'Blue Wave Express Taxi',
    category: 'Transport',
    area: 'Colombo 01',
    description: 'Airport transfers, intercity luxury sedans, and reliable city travel bookings.',
    openingHours: 'Open 24 Hours',
    qrReference: 'QR-CITY-MERCH-7',
  ),
  const CityMerchantItem(
    id: 'merch_8',
    name: 'Kumbuk Shade Tea Room',
    category: 'Food',
    area: 'Kandy City',
    description: 'Single-origin hill country teas, fresh scones, homemade strawberry jam, and cakes.',
    openingHours: '08:00 - 21:00',
    qrReference: 'QR-CITY-MERCH-8',
  ),
  const CityMerchantItem(
    id: 'merch_9',
    name: 'Hill Capital Mart',
    category: 'Groceries',
    area: 'Kandy City',
    description: 'Organic spices, hill country vegetables, unrefined jaggery, and daily groceries.',
    openingHours: '08:30 - 21:30',
    qrReference: 'QR-CITY-MERCH-9',
  ),
  const CityMerchantItem(
    id: 'merch_10',
    name: 'Central Rapid Fuel',
    category: 'Fuel',
    area: 'Kandy City',
    description: 'High octane fuel, battery emergency top-up, car wash, and quick expressway snacks.',
    openingHours: '06:00 - 23:00',
    qrReference: 'QR-CITY-MERCH-10',
  ),
  const CityMerchantItem(
    id: 'merch_11',
    name: 'AyurVeda Botanic Care',
    category: 'Health',
    area: 'Kandy City',
    description: 'Traditional herbal massage oils, herbal balms, and natural wellness treatments.',
    openingHours: '09:00 - 19:00',
    qrReference: 'QR-CITY-MERCH-11',
  ),
  const CityMerchantItem(
    id: 'merch_12',
    name: 'Ramboda View Cafe',
    category: 'Food',
    area: 'Nuwara Eliya',
    description: 'Highland mist espresso, hot chocolate, warm apple pies, and fresh strawberry waffles.',
    openingHours: '07:00 - 20:00',
    qrReference: 'QR-CITY-MERCH-12',
  ),
  const CityMerchantItem(
    id: 'merch_13',
    name: 'Galle Fort Bakehouse',
    category: 'Food',
    area: 'Galle Fort',
    description: 'Wood-fired sourdough baguettes, cinnamon scrolls, artisan sandwiches, and gelato.',
    openingHours: '07:00 - 22:00',
    qrReference: 'QR-CITY-MERCH-13',
    offers: [
      CityOfferItem(
        id: 'offer_7',
        merchantId: 'merch_13',
        title: 'Bakery Morning Delight',
        description: 'Get 2x points and enjoy fresh morning croissants.',
        pointsMultiplier: 2.0,
        discountLabel: 'Buy 1 Get 1 Coffee',
      ),
    ],
  ),
  const CityMerchantItem(
    id: 'merch_14',
    name: 'Ocean Breeze Fish Market',
    category: 'Groceries',
    area: 'Galle Fort',
    description: 'Freshly caught deep-sea tuna, jumbo prawns, seer fish steaks, and marine produce.',
    openingHours: '06:00 - 18:00',
    qrReference: 'QR-CITY-MERCH-14',
  ),
  const CityMerchantItem(
    id: 'merch_15',
    name: 'Southern Wave Watersports',
    category: 'Entertainment',
    area: 'Galle Fort',
    description: 'Beginner surfboard hire, coastal boat tours, and certified coral reef snorkeling trips.',
    openingHours: '08:30 - 18:30',
    qrReference: 'QR-CITY-MERCH-15',
  ),
  const CityMerchantItem(
    id: 'merch_16',
    name: 'Old Town Artisan Crafts',
    category: 'Shopping',
    area: 'Galle Fort',
    description: 'Hand-carved wooden masks, polished coconut shell homeware, and brass ornaments.',
    openingHours: '09:00 - 21:00',
    qrReference: 'QR-CITY-MERCH-16',
  ),
  const CityMerchantItem(
    id: 'merch_17',
    name: 'Negombo Coastal Fuel',
    category: 'Fuel',
    area: 'Negombo',
    description: 'Expressway entry refuel station, diesel exhaust fluid, and travel beverages.',
    openingHours: 'Open 24 Hours',
    qrReference: 'QR-CITY-MERCH-17',
  ),
  const CityMerchantItem(
    id: 'merch_18',
    name: 'Lagoon Fishery & Groceries',
    category: 'Groceries',
    area: 'Negombo',
    description: 'Crab delicacies, dried fish, coconut treacle, and fresh coastal fruits.',
    openingHours: '06:00 - 20:00',
    qrReference: 'QR-CITY-MERCH-18',
  ),
  const CityMerchantItem(
    id: 'merch_19',
    name: 'Golden Sun Wellness',
    category: 'Health',
    area: 'Negombo',
    description: 'Holistic spa treatments, reflexology, aromatherapy, and natural herbal care.',
    openingHours: '08:00 - 21:00',
    qrReference: 'QR-CITY-MERCH-19',
  ),
  const CityMerchantItem(
    id: 'merch_20',
    name: 'Island Central Transit',
    category: 'Transport',
    area: 'Colombo 01',
    description: 'Intercity coach tickets, eco electric shuttles, and day city excursion tours.',
    openingHours: '05:00 - 23:00',
    qrReference: 'QR-CITY-MERCH-20',
  ),
];

class MerchantListNotifier extends Notifier<List<CityMerchantItem>> {
  @override
  List<CityMerchantItem> build() => kInitialSimulatedMerchants;

  void toggleFavourite(String merchantId) {
    state = state.map((m) {
      if (m.id == merchantId) {
        return m.copyWith(isFavourite: !m.isFavourite);
      }
      return m;
    }).toList();
  }
}

final cityMerchantsProvider =
    NotifierProvider<MerchantListNotifier, List<CityMerchantItem>>(
  MerchantListNotifier.new,
);

class SelectedCityCategoryNotifier extends Notifier<CityCategoryType> {
  @override
  CityCategoryType build() => CityCategoryType.all;

  void select(CityCategoryType category) {
    state = category;
  }
}

final selectedCityCategoryProvider =
    NotifierProvider<SelectedCityCategoryNotifier, CityCategoryType>(
  SelectedCityCategoryNotifier.new,
);

class CitySearchQueryNotifier extends Notifier<String> {
  @override
  String build() => '';

  void setQuery(String query) {
    state = query;
  }
}

final citySearchQueryProvider =
    NotifierProvider<CitySearchQueryNotifier, String>(
  CitySearchQueryNotifier.new,
);

final filteredCityMerchantsProvider = Provider<List<CityMerchantItem>>((ref) {
  final merchants = ref.watch(cityMerchantsProvider);
  final category = ref.watch(selectedCityCategoryProvider);
  final query = ref.watch(citySearchQueryProvider).trim().toLowerCase();

  return merchants.where((m) {
    if (category != CityCategoryType.all) {
      if (!m.category.toLowerCase().contains(category.key)) {
        return false;
      }
    }
    if (query.isNotEmpty) {
      final matchesName = m.name.toLowerCase().contains(query);
      final matchesDesc = m.description.toLowerCase().contains(query);
      final matchesArea = m.area.toLowerCase().contains(query);
      if (!matchesName && !matchesDesc && !matchesArea) {
        return false;
      }
    }
    return true;
  }).toList();
});

final cityOffersProvider = Provider<List<CityOfferItem>>((ref) {
  final merchants = ref.watch(cityMerchantsProvider);
  final offers = <CityOfferItem>[];
  for (final m in merchants) {
    offers.addAll(m.offers);
  }
  return offers;
});

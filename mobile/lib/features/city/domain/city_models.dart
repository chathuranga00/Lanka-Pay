import 'package:flutter/material.dart';

enum CityCategoryType {
  all('all', 'All Places', Icons.explore_rounded),
  food('food', 'Food & Dining', Icons.restaurant_rounded),
  groceries('groceries', 'Groceries', Icons.shopping_basket_rounded),
  fuel('fuel', 'Fuel Stations', Icons.local_gas_station_rounded),
  transport('transport', 'Transport', Icons.directions_car_rounded),
  health('health', 'Health & Care', Icons.local_hospital_rounded),
  entertainment('entertainment', 'Entertainment', Icons.movie_rounded),
  shopping('shopping', 'Shopping', Icons.storefront_rounded);

  final String key;
  final String label;
  final IconData icon;

  const CityCategoryType(this.key, this.label, this.icon);
}

class CityOfferItem {
  final String id;
  final String merchantId;
  final String title;
  final String description;
  final double? pointsMultiplier;
  final String? discountLabel;
  final String terms;

  const CityOfferItem({
    required this.id,
    required this.merchantId,
    required this.title,
    required this.description,
    this.pointsMultiplier,
    this.discountLabel,
    this.terms = 'Simulated promotional offer. Points/discount applied upon payment confirmation.',
  });

  factory CityOfferItem.fromJson(Map<String, dynamic> json) {
    return CityOfferItem(
      id: json['id'] as String? ?? '',
      merchantId: json['merchantId'] as String? ?? '',
      title: json['title'] as String? ?? '',
      description: json['description'] as String? ?? '',
      pointsMultiplier: (json['pointsMultiplier'] as num?)?.toDouble(),
      discountLabel: json['discountLabel'] as String?,
      terms: json['terms'] as String? ?? '',
    );
  }
}

class CityMerchantItem {
  final String id;
  final String name;
  final String category;
  final String area;
  final String description;
  final String openingHours;
  final String qrReference;
  final bool isFavourite;
  final List<CityOfferItem> offers;

  const CityMerchantItem({
    required this.id,
    required this.name,
    required this.category,
    required this.area,
    required this.description,
    required this.openingHours,
    required this.qrReference,
    this.isFavourite = false,
    this.offers = const [],
  });

  CityMerchantItem copyWith({
    String? id,
    String? name,
    String? category,
    String? area,
    String? description,
    String? openingHours,
    String? qrReference,
    bool? isFavourite,
    List<CityOfferItem>? offers,
  }) {
    return CityMerchantItem(
      id: id ?? this.id,
      name: name ?? this.name,
      category: category ?? this.category,
      area: area ?? this.area,
      description: description ?? this.description,
      openingHours: openingHours ?? this.openingHours,
      qrReference: qrReference ?? this.qrReference,
      isFavourite: isFavourite ?? this.isFavourite,
      offers: offers ?? this.offers,
    );
  }

  factory CityMerchantItem.fromJson(Map<String, dynamic> json) {
    final rawOffers = json['offers'] as List<dynamic>? ?? [];
    return CityMerchantItem(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      category: json['category'] as String? ?? 'FOOD',
      area: json['area'] as String? ?? 'Colombo',
      description: json['description'] as String? ?? '',
      openingHours: json['openingHours'] as String? ?? '08:00 - 22:00',
      qrReference: json['qrReference'] as String? ?? '',
      isFavourite: json['isFavourite'] as bool? ?? false,
      offers: rawOffers.map((o) => CityOfferItem.fromJson(o as Map<String, dynamic>)).toList(),
    );
  }
}

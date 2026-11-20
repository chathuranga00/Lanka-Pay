package com.lankapay.city.store;

import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.CityOffer;
import com.lankapay.city.domain.MerchantStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryMerchantStore implements MerchantStore {

    private final Map<String, CityMerchant> merchants = new ConcurrentHashMap<>();
    private final Map<String, CityOffer> offers = new ConcurrentHashMap<>();

    public InMemoryMerchantStore() {
        seedInitialMerchants();
        seedInitialOffers();
    }

    private void seedInitialMerchants() {
        List<CityMerchant> seedList = List.of(
                new CityMerchant(
                        "merch_1",
                        "Cinnamon Leaf Bistro",
                        CityCategory.FOOD,
                        "Colombo 03",
                        "Authentic contemporary Ceylon fusion dining, fresh juices, and specialty coffees.",
                        "07:30 - 23:00",
                        "QR-CITY-MERCH-1",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_2",
                        "Metro Island Grocers",
                        CityCategory.GROCERIES,
                        "Colombo 04",
                        "Fresh highland vegetables, farm dairy, tropical fruits, and household pantry essentials.",
                        "08:00 - 22:00",
                        "QR-CITY-MERCH-2",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_3",
                        "Ceylon Spark Fuel",
                        CityCategory.FUEL,
                        "Colombo 07",
                        "Euro 4 petrol, auto diesel, rapid tyre air service, and 24/7 convenience kiosk.",
                        "Open 24 Hours",
                        "QR-CITY-MERCH-3",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_4",
                        "Lanka Care Pharmacy",
                        CityCategory.HEALTH,
                        "Colombo 05",
                        "Dispensing prescription medicines, organic wellness supplements, and first aid care.",
                        "08:00 - 22:30",
                        "QR-CITY-MERCH-4",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_5",
                        "Starlight Cinema Hub",
                        CityCategory.ENTERTAINMENT,
                        "Colombo 02",
                        "IMAX cinema experience, Dolby Atmos surround, recliner seats, and warm gourmet popcorn.",
                        "10:00 - 00:00",
                        "QR-CITY-MERCH-5",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_6",
                        "Southern Silk & Cotton",
                        CityCategory.SHOPPING,
                        "Colombo 03",
                        "Handloom batik shirts, breathable linen outfits, and artisan handcrafted accessories.",
                        "09:30 - 20:00",
                        "QR-CITY-MERCH-6",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_7",
                        "Blue Wave Express Taxi",
                        CityCategory.TRANSPORT,
                        "Colombo 01",
                        "Airport transfers, intercity luxury sedans, and reliable city travel bookings.",
                        "Open 24 Hours",
                        "QR-CITY-MERCH-7",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_8",
                        "Kumbuk Shade Tea Room",
                        CityCategory.FOOD,
                        "Kandy City",
                        "Single-origin hill country teas, fresh scones, homemade strawberry jam, and cakes.",
                        "08:00 - 21:00",
                        "QR-CITY-MERCH-8",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_9",
                        "Hill Capital Mart",
                        CityCategory.GROCERIES,
                        "Kandy City",
                        "Organic spices, hill country vegetables, unrefined jaggery, and daily groceries.",
                        "08:30 - 21:30",
                        "QR-CITY-MERCH-9",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_10",
                        "Central Rapid Fuel",
                        CityCategory.FUEL,
                        "Kandy City",
                        "High octane fuel, battery emergency top-up, car wash, and quick expressway snacks.",
                        "06:00 - 23:00",
                        "QR-CITY-MERCH-10",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_11",
                        "AyurVeda Botanic Care",
                        CityCategory.HEALTH,
                        "Kandy City",
                        "Traditional herbal massage oils, herbal balms, and natural wellness treatments.",
                        "09:00 - 19:00",
                        "QR-CITY-MERCH-11",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_12",
                        "Ramboda View Cafe",
                        CityCategory.FOOD,
                        "Nuwara Eliya",
                        "Highland mist espresso, hot chocolate, warm apple pies, and fresh strawberry waffles.",
                        "07:00 - 20:00",
                        "QR-CITY-MERCH-12",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_13",
                        "Galle Fort Bakehouse",
                        CityCategory.FOOD,
                        "Galle Fort",
                        "Wood-fired sourdough baguettes, cinnamon scrolls, artisan sandwiches, and gelato.",
                        "07:00 - 22:00",
                        "QR-CITY-MERCH-13",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_14",
                        "Ocean Breeze Fish Market",
                        CityCategory.GROCERIES,
                        "Galle Fort",
                        "Freshly caught deep-sea tuna, jumbo prawns, seer fish steaks, and marine produce.",
                        "06:00 - 18:00",
                        "QR-CITY-MERCH-14",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_15",
                        "Southern Wave Watersports",
                        CityCategory.ENTERTAINMENT,
                        "Galle Fort",
                        "Beginner surfboard hire, coastal boat tours, and certified coral reef snorkeling trips.",
                        "08:30 - 18:30",
                        "QR-CITY-MERCH-15",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_16",
                        "Old Town Artisan Crafts",
                        CityCategory.SHOPPING,
                        "Galle Fort",
                        "Hand-carved wooden masks, polished coconut shell homeware, and brass ornaments.",
                        "09:00 - 21:00",
                        "QR-CITY-MERCH-16",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_17",
                        "Negombo Coastal Fuel",
                        CityCategory.FUEL,
                        "Negombo",
                        "Expressway entry refuel station, diesel exhaust fluid, and travel beverages.",
                        "Open 24 Hours",
                        "QR-CITY-MERCH-17",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_18",
                        "Lagoon Fishery & Groceries",
                        CityCategory.GROCERIES,
                        "Negombo",
                        "Crab delicacies, dried fish, coconut treacle, and fresh coastal fruits.",
                        "06:00 - 20:00",
                        "QR-CITY-MERCH-18",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_19",
                        "Golden Sun Wellness",
                        CityCategory.HEALTH,
                        "Negombo",
                        "Holistic spa treatments, reflexology, aromatherapy, and natural herbal care.",
                        "08:00 - 21:00",
                        "QR-CITY-MERCH-19",
                        MerchantStatus.ACTIVE
                ),
                new CityMerchant(
                        "merch_20",
                        "Island Central Transit",
                        CityCategory.TRANSPORT,
                        "Colombo 01",
                        "Intercity coach tickets, eco electric shuttles, and day city excursion tours.",
                        "05:00 - 23:00",
                        "QR-CITY-MERCH-20",
                        MerchantStatus.ACTIVE
                )
        );

        for (CityMerchant m : seedList) {
            merchants.put(m.id(), m);
        }
    }

    private void seedInitialOffers() {
        List<CityOffer> seedOffers = List.of(
                new CityOffer(
                        "offer_1",
                        "merch_1",
                        "2.5x Points on Weekend Dining",
                        "Enjoy 2.5x simulated rewards points on all dine-in and takeaway orders on Saturdays and Sundays.",
                        2.5,
                        "10% OFF Bill",
                        true,
                        "Informational promo only. Points awarded automatically on confirmed payment."
                ),
                new CityOffer(
                        "offer_2",
                        "merch_2",
                        "Double Points on Fresh Groceries",
                        "Earn 2x rewards on fresh fruits and local farm dairy purchases over LKR 2,500.",
                        2.0,
                        "LKR 500 OFF",
                        true,
                        "Informational offer label. Applied on checkout."
                ),
                new CityOffer(
                        "offer_3",
                        "merch_3",
                        "3x Simulated Points on Euro 4 Fuel",
                        "Fill up your vehicle and earn triple points on LankaQR settlements.",
                        3.0,
                        "Free Tyre Air",
                        true,
                        "Applicable on all fuel pumps during promotional hours."
                ),
                new CityOffer(
                        "offer_4",
                        "merch_4",
                        "2x Points on Wellness Products",
                        "Receive double rewards points on essential health and personal wellness items.",
                        2.0,
                        "15% OFF",
                        true,
                        "Subject to merchant stock and seasonal wellness campaign."
                ),
                new CityOffer(
                        "offer_5",
                        "merch_5",
                        "Movie Night Bonus Points",
                        "Earn 2x points when purchasing cinema tickets through city merchant payment.",
                        2.0,
                        "Free Popcorn",
                        true,
                        "Informational discount perk. Present confirmed payment receipt at counter."
                ),
                new CityOffer(
                        "offer_6",
                        "merch_6",
                        "Handloom Heritage Bonus",
                        "Triple points on authentic cotton and handloom apparel purchases.",
                        3.0,
                        "20% OFF",
                        true,
                        "Simulated informational offer."
                ),
                new CityOffer(
                        "offer_7",
                        "merch_13",
                        "Bakery Morning Delight",
                        "Get 2x points and enjoy fresh morning croissants.",
                        2.0,
                        "Buy 1 Get 1 Coffee",
                        true,
                        "Valid for breakfast hours 07:00 - 11:00."
                )
        );

        for (CityOffer offer : seedOffers) {
            offers.put(offer.id(), offer);
        }
    }

    @Override
    public List<CityMerchant> findAllMerchants() {
        return new ArrayList<>(merchants.values());
    }

    @Override
    public Optional<CityMerchant> findMerchantById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(merchants.get(id));
    }

    @Override
    public CityMerchant saveMerchant(CityMerchant merchant) {
        merchants.put(merchant.id(), merchant);
        return merchant;
    }

    @Override
    public boolean deleteMerchant(String id) {
        return merchants.remove(id) != null;
    }

    @Override
    public List<CityOffer> findAllOffers() {
        return new ArrayList<>(offers.values());
    }

    @Override
    public List<CityOffer> findOffersByMerchantId(String merchantId) {
        if (merchantId == null) return List.of();
        return offers.values().stream()
                .filter(o -> o.merchantId().equals(merchantId))
                .toList();
    }

    @Override
    public Optional<CityOffer> findOfferById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(offers.get(id));
    }

    @Override
    public CityOffer saveOffer(CityOffer offer) {
        offers.put(offer.id(), offer);
        return offer;
    }

    @Override
    public boolean deleteOffer(String id) {
        return offers.remove(id) != null;
    }
}

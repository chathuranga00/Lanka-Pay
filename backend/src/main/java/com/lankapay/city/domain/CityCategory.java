package com.lankapay.city.domain;

/**
 * City merchant categories.
 * All merchants and offers are strictly simulated without real-world brands.
 */
public enum CityCategory {
    FOOD("Food & Dining", "restaurant"),
    GROCERIES("Groceries & Daily Essentials", "shopping_cart"),
    FUEL("Fuel & Stations", "local_gas_station"),
    TRANSPORT("Transport & Travel", "directions_car"),
    HEALTH("Health & Wellness", "local_hospital"),
    ENTERTAINMENT("Entertainment & Leisure", "movie"),
    SHOPPING("Shopping & Retail", "storefront");

    private final String displayName;
    private final String iconName;

    CityCategory(String displayName, String iconName) {
        this.displayName = displayName;
        this.iconName = iconName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconName() {
        return iconName;
    }
}

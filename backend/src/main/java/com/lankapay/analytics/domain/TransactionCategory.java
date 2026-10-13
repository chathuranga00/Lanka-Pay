package com.lankapay.analytics.domain;

public enum TransactionCategory {
    TRANSFERS("Transfers", "#4C84F0"),
    BILLS("Bills", "#F59E0B"),
    SHOPPING("Shopping", "#EC4899"),
    FOOD("Food & Dining", "#10B981"),
    TRANSPORT("Transport", "#8B5CF6"),
    HEALTH("Health & Medical", "#EF4444"),
    EDUCATION("Education", "#3B82F6"),
    ENTERTAINMENT("Entertainment", "#F97316"),
    OTHER("Other", "#6B7280");

    private final String displayName;
    private final String defaultColorHex;

    TransactionCategory(String displayName, String defaultColorHex) {
        this.displayName = displayName;
        this.defaultColorHex = defaultColorHex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultColorHex() {
        return defaultColorHex;
    }

    public static TransactionCategory fromString(String val) {
        if (val == null || val.isBlank()) {
            return OTHER;
        }
        for (TransactionCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(val.trim()) || cat.displayName.equalsIgnoreCase(val.trim())) {
                return cat;
            }
        }
        return OTHER;
    }
}

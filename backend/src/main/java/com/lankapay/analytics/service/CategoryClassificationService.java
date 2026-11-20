package com.lankapay.analytics.service;

import com.lankapay.analytics.domain.TransactionCategory;
import com.lankapay.analytics.domain.UserTransactionCategory;
import com.lankapay.analytics.domain.UserTransactionCategoryStore;
import com.lankapay.bill.adapter.BillerCategory;
import com.lankapay.bill.domain.BillPaymentRecord;
import com.lankapay.transfer.domain.TransactionRecord;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class CategoryClassificationService {

    private final UserTransactionCategoryStore categoryStore;

    public CategoryClassificationService(UserTransactionCategoryStore categoryStore) {
        this.categoryStore = categoryStore;
    }

    public record ResolvedCategory(TransactionCategory category, String colorHex) {}

    public ResolvedCategory resolveTransferCategory(Long userId, TransactionRecord tx) {
        if (tx == null) {
            return new ResolvedCategory(TransactionCategory.TRANSFERS, TransactionCategory.TRANSFERS.getDefaultColorHex());
        }

        // Check user override first
        Optional<UserTransactionCategory> override = categoryStore.find(userId, "TRANSFER", tx.id());
        if (override.isPresent()) {
            TransactionCategory cat = override.get().category();
            String color = override.get().colorHex() != null ? override.get().colorHex() : cat.getDefaultColorHex();
            return new ResolvedCategory(cat, color);
        }

        // Automatic heuristic based on note or receiver name
        String text = ((tx.note() != null ? tx.note() : "") + " " +
                (tx.receiverFullName() != null ? tx.receiverFullName() : "")).toLowerCase(Locale.ROOT);

        if (containsAny(text, "food", "lunch", "dinner", "breakfast", "cafe", "restaurant", "coffee", "pizza", "burger", "kottu", "meal", "bakery")) {
            return new ResolvedCategory(TransactionCategory.FOOD, TransactionCategory.FOOD.getDefaultColorHex());
        }
        if (containsAny(text, "uber", "pickme", "taxi", "fuel", "petrol", "ride", "bus", "train", "parking", "expressway")) {
            return new ResolvedCategory(TransactionCategory.TRANSPORT, TransactionCategory.TRANSPORT.getDefaultColorHex());
        }
        if (containsAny(text, "shop", "store", "daraz", "keells", "cargills", "spar", "clothing", "supermarket", "fashion", "mall")) {
            return new ResolvedCategory(TransactionCategory.SHOPPING, TransactionCategory.SHOPPING.getDefaultColorHex());
        }
        if (containsAny(text, "doctor", "hospital", "pharmacy", "medical", "clinic", "health", "medicine")) {
            return new ResolvedCategory(TransactionCategory.HEALTH, TransactionCategory.HEALTH.getDefaultColorHex());
        }
        if (containsAny(text, "school", "tuition", "university", "course", "exam", "education", "class")) {
            return new ResolvedCategory(TransactionCategory.EDUCATION, TransactionCategory.EDUCATION.getDefaultColorHex());
        }
        if (containsAny(text, "movie", "cinema", "concert", "game", "entertainment", "ticket")) {
            return new ResolvedCategory(TransactionCategory.ENTERTAINMENT, TransactionCategory.ENTERTAINMENT.getDefaultColorHex());
        }

        return new ResolvedCategory(TransactionCategory.TRANSFERS, TransactionCategory.TRANSFERS.getDefaultColorHex());
    }

    public ResolvedCategory resolveBillCategory(Long userId, BillPaymentRecord bill) {
        if (bill == null) {
            return new ResolvedCategory(TransactionCategory.BILLS, TransactionCategory.BILLS.getDefaultColorHex());
        }

        // Check user override first
        Optional<UserTransactionCategory> override = categoryStore.find(userId, "BILL", bill.id());
        if (override.isPresent()) {
            TransactionCategory cat = override.get().category();
            String color = override.get().colorHex() != null ? override.get().colorHex() : cat.getDefaultColorHex();
            return new ResolvedCategory(cat, color);
        }

        if (bill.category() != null) {
            return new ResolvedCategory(TransactionCategory.BILLS, TransactionCategory.BILLS.getDefaultColorHex());
        }

        return new ResolvedCategory(TransactionCategory.BILLS, TransactionCategory.BILLS.getDefaultColorHex());
    }

    private boolean containsAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}

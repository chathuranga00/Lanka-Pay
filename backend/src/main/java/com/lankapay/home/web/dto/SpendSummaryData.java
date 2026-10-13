package com.lankapay.home.web.dto;

/**
 * Monthly spend summary for the "All activity" Info Tile.
 *
 * - totalSpentMinor : total debit in minor units (cents / paisa equivalent)
 * - totalSpentLkr   : formatted string with thousands separators, e.g. "12,450.00"
 * - budgetMinor     : user-set budget ceiling (0 = no budget set)
 * - budgetLkr       : formatted budget string
 * - budgetUsedFraction : 0.0–1.0; used to render the thin progress bar
 * - month           : display month label, e.g. "October"
 * - hasTransactions : false when the user has zero debits this month
 */
public record SpendSummaryData(
        long totalSpentMinor,
        String totalSpentLkr,
        long budgetMinor,
        String budgetLkr,
        double budgetUsedFraction,
        String month,
        boolean hasTransactions
) {}

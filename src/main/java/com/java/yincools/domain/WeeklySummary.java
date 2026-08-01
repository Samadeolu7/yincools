package com.java.yincools.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Profit here is charged minus costs (parts + shop expenses), not cash collected -- it reflects work billed this week regardless of whether it's been paid yet. */
public record WeeklySummary(
        LocalDate weekStart,
        LocalDate weekEnd,
        int jobCount,
        BigDecimal totalCharged,
        BigDecimal totalPaid,
        BigDecimal totalPartsCost,
        BigDecimal totalShopExpenses,
        BigDecimal profit
) {
}

package com.financetracker.dto.response;

import java.math.BigDecimal;

/**
 * Lightweight projection for category spend breakdown.
 * Uses Spring Data JPA interface projection — no entity loading overhead.
 */
public interface CategorySpendSummary {
    String getCategoryName();
    String getCategoryIcon();
    String getCategoryColor();
    BigDecimal getTotalAmount();
    Long getTransactionCount();
}
package com.financetracker.dto.response;

import com.financetracker.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// ============================================================
// Auth Responses
// ============================================================

@Data @Builder
class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserResponse user;
}

@Data @Builder
class UserResponse {
    private String uuid;
    private String email;
    private String fullName;
    private String currency;
    private String timezone;
    private Instant createdAt;
}

// ============================================================
// Transaction Response
// ============================================================

@Data @Builder
class TransactionResponse {
    private String uuid;
    private String accountUuid;
    private String accountName;
    private String categoryId;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private BigDecimal amount;
    private TransactionType type;
    private String description;
    private String note;
    private LocalDate transactionDate;
    private Instant transactionTime;
    private String referenceId;
    private boolean isRecurring;
    private Instant createdAt;
}

// ============================================================
// Account Response
// ============================================================

@Data @Builder
class AccountResponse {
    private String uuid;
    private String name;
    private String accountType;
    private BigDecimal balance;
    private String currency;
    private String color;
    private String icon;
    private boolean isActive;
    private Instant createdAt;
}

// ============================================================
// Dashboard / Analytics Response
// ============================================================

@Data @Builder
class DashboardResponse {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
    private BigDecimal totalAssets;
    private List<CategorySpendSummary> topCategories;
    private List<DailySpendSummary> dailyTrend;
    private List<BudgetSummaryResponse> budgets;
}

@Data @Builder
class BudgetSummaryResponse {
    private String uuid;
    private String name;
    private String categoryName;
    private BigDecimal amount;
    private BigDecimal spent;
    private BigDecimal remainingAmount;
    private BigDecimal utilizationPercent;
    private boolean isOverBudget;
    private boolean alertTriggered;
    private LocalDate startDate;
    private LocalDate endDate;
    private String period;
}
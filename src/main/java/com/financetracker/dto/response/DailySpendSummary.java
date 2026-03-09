package com.financetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailySpendSummary {
    LocalDate getDate();
    BigDecimal getIncome();
    BigDecimal getExpense();
}
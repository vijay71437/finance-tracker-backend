package com.financetracker.dto.request;

import com.financetracker.entity.Budget.BudgetPeriod;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BudgetRequest {

    @NotBlank @Size(max = 100)
    private String name;

    private String categoryUuid;  // null = all categories

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotNull
    private BudgetPeriod period;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @DecimalMin("1.00") @DecimalMax("100.00")
    private BigDecimal alertThreshold = new BigDecimal("80.00");
}
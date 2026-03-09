package com.financetracker.dto.request;

import com.financetracker.entity.Account.AccountType;
import com.financetracker.entity.Budget.BudgetPeriod;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccountRequest {

    @NotBlank @Size(min = 1, max = 100)
    private String name;

    @NotNull
    private AccountType accountType;

    @DecimalMin("0.00")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Size(min = 3, max = 3)
    private String currency = "USD";

    @Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a valid hex code")
    private String color;

    @Size(max = 50)
    private String icon;
}
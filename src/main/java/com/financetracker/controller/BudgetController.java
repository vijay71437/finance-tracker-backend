package com.financetracker.controller;

import com.financetracker.dto.request.BudgetRequest;
import com.financetracker.dto.response.ApiResponse;
import com.financetracker.entity.Budget;
import com.financetracker.service.impl.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Budgets", description = "Create and track spending budgets")
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    @Operation(summary = "Create a new budget")
    public ResponseEntity<ApiResponse<Budget>> create(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody BudgetRequest request) {
        Budget budget = budgetService.createBudget(user.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Budget created", budget));
    }

    @GetMapping
    @Operation(summary = "List all active budgets")
    public ResponseEntity<ApiResponse<List<Budget>>> list(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.getBudgets(user.getUsername())));
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get budget by UUID")
    public ResponseEntity<ApiResponse<Budget>> get(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String uuid) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.getBudget(user.getUsername(), uuid)));
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete a budget")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String uuid) {
        budgetService.deleteBudget(user.getUsername(), uuid);
        return ResponseEntity.ok(ApiResponse.success("Budget deleted", null));
    }
}
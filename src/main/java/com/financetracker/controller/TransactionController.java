package com.financetracker.controller;

import com.financetracker.dto.request.TransactionRequest;
import com.financetracker.dto.response.ApiResponse;
import com.financetracker.entity.Transaction;
import com.financetracker.service.impl.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Transaction controller — CRUD + analytics for financial transactions.
 *
 * <p>All endpoints require a valid Bearer token.
 * User identity is always derived from the JWT (never from request params)
 * to prevent insecure direct object references (IDOR).
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Create and manage financial transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<ApiResponse<Transaction>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {

        Transaction transaction = transactionService.createTransaction(
                userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction created", transaction));
    }

    @GetMapping
    @Operation(summary = "List transactions with date range and pagination")
    public ResponseEntity<ApiResponse<Page<Transaction>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().withDayOfMonth(1)}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Start date (yyyy-MM-dd, default: first day of current month)")
            LocalDate startDate,

            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "End date (yyyy-MM-dd, default: today)")
            LocalDate endDate,

            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100); // Enforce max page size
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "transactionDate", "transactionTime"));

        Page<Transaction> transactions = transactionService.getTransactions(
                userDetails.getUsername(), startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get a single transaction by UUID")
    public ResponseEntity<ApiResponse<Transaction>> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String uuid) {

        Transaction transaction = transactionService.getTransaction(
                userDetails.getUsername(), uuid);
        return ResponseEntity.ok(ApiResponse.success(transaction));
    }

    @PutMapping("/{uuid}")
    @Operation(summary = "Update a transaction")
    public ResponseEntity<ApiResponse<Transaction>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String uuid,
            @Valid @RequestBody TransactionRequest request) {

        Transaction updated = transactionService.updateTransaction(
                userDetails.getUsername(), uuid, request);
        return ResponseEntity.ok(ApiResponse.success("Transaction updated", updated));
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String uuid) {

        transactionService.deleteTransaction(userDetails.getUsername(), uuid);
        return ResponseEntity.ok(ApiResponse.success("Transaction deleted", null));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get income/expense summary and category breakdown for a period")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Map<String, Object> summary = transactionService.getSummary(
                userDetails.getUsername(), startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
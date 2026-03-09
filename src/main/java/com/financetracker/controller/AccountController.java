package com.financetracker.controller;

import com.financetracker.dto.request.AccountRequest;
import com.financetracker.dto.response.ApiResponse;
import com.financetracker.entity.Account;
import com.financetracker.service.impl.AccountService;
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
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Accounts", description = "Manage financial accounts (wallet, bank, credit card)")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<ApiResponse<Account>> create(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody AccountRequest request) {
        Account account = accountService.createAccount(user.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created", account));
    }

    @GetMapping
    @Operation(summary = "List all active accounts")
    public ResponseEntity<ApiResponse<List<Account>>> list(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccounts(user.getUsername())));
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get account by UUID")
    public ResponseEntity<ApiResponse<Account>> get(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String uuid) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccount(user.getUsername(), uuid)));
    }

    @PutMapping("/{uuid}")
    @Operation(summary = "Update account details")
    public ResponseEntity<ApiResponse<Account>> update(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String uuid,
            @Valid @RequestBody AccountRequest request) {
        Account updated = accountService.updateAccount(user.getUsername(), uuid, request);
        return ResponseEntity.ok(ApiResponse.success("Account updated", updated));
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Soft-delete an account")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String uuid) {
        accountService.deleteAccount(user.getUsername(), uuid);
        return ResponseEntity.ok(ApiResponse.success("Account deleted", null));
    }
}
package com.financetracker.service.impl;

import com.financetracker.dto.request.TransactionRequest;
import com.financetracker.entity.Account;
import com.financetracker.entity.Budget;
import com.financetracker.entity.Category;
import com.financetracker.entity.Transaction;
import com.financetracker.entity.Transaction.TransactionType;
import com.financetracker.entity.User;
import com.financetracker.exception.BusinessException;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Transaction service — orchestrates the core finance tracking operations.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Account balance is updated atomically via a single UPDATE statement
 *       to avoid optimistic lock conflicts on high-concurrency writes.</li>
 *   <li>Budget spent amounts are updated asynchronously to avoid blocking
 *       the transaction response. A brief inconsistency window is acceptable.</li>
 *   <li>Cache eviction is keyed by userId to invalidate dashboard caches
 *       after mutations.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    // ---- Create ----

    @Transactional
    @CacheEvict(value = {"dashboard", "transactions"}, key = "#userEmail")
    public Transaction createTransaction(String userEmail, TransactionRequest request) {
        User user = getActiveUser(userEmail);
        Account account = getAccountForUser(request.getAccountUuid(), userEmail);

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(Long.parseLong(request.getCategoryId()))
                    .orElse(null);
        }

        Transaction transaction = Transaction.builder()
                .user(user)
                .account(account)
                .category(category)
                .amount(request.getAmount())
                .type(request.getType())
                .description(request.getDescription())
                .note(request.getNote())
                .transactionDate(request.getTransactionDate())
                .referenceId(request.getReferenceId())
                .build();

        transaction = transactionRepository.save(transaction);

        // Adjust account balance atomically
        BigDecimal delta = computeBalanceDelta(request.getType(), request.getAmount());
        int updated = accountRepository.adjustBalance(account.getId(), user.getId(), delta);
        if (updated == 0) {
            throw new BusinessException("Failed to update account balance — concurrent modification detected");
        }

        // Update budgets async (non-critical path)
        if (request.getType() == TransactionType.EXPENSE && category != null) {
            updateBudgetsAsync(user.getId(), category.getId(), request.getTransactionDate());
        }

        log.info("Transaction created: uuid={}, user={}, amount={}, type={}",
                transaction.getUuid(), userEmail, request.getAmount(), request.getType());

        return transaction;
    }

    // ---- Read ----

    @Transactional(readOnly = true)
    @Cacheable(value = "transactions", key = "#userEmail + ':' + #startDate + ':' + #endDate + ':' + #pageable.pageNumber")
    public Page<Transaction> getTransactions(
            String userEmail, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        User user = getActiveUser(userEmail);
        return transactionRepository.findByUserAndDateRange(
                user.getId(), startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(String userEmail, String transactionUuid) {
        return transactionRepository.findByUuidAndUserEmail(transactionUuid, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + transactionUuid));
    }

    // ---- Update ----

    @Transactional
    @CacheEvict(value = {"dashboard", "transactions"}, key = "#userEmail")
    public Transaction updateTransaction(
            String userEmail, String transactionUuid, TransactionRequest request) {

        Transaction existing = getTransaction(userEmail, transactionUuid);

        // Reverse the original balance effect
        BigDecimal reverseDelta = computeBalanceDelta(existing.getType(), existing.getAmount()).negate();
        accountRepository.adjustBalance(existing.getAccount().getId(),
                existing.getUser().getId(), reverseDelta);

        // Switch account if requested
        Account newAccount = getAccountForUser(request.getAccountUuid(), userEmail);

        // Apply new values
        existing.setAccount(newAccount);
        existing.setAmount(request.getAmount());
        existing.setType(request.getType());
        existing.setDescription(request.getDescription());
        existing.setNote(request.getNote());
        existing.setTransactionDate(request.getTransactionDate());

        if (request.getCategoryId() != null) {
            categoryRepository.findById(Long.parseLong(request.getCategoryId()))
                    .ifPresent(existing::setCategory);
        }

        // Apply new balance effect
        BigDecimal newDelta = computeBalanceDelta(request.getType(), request.getAmount());
        accountRepository.adjustBalance(newAccount.getId(), existing.getUser().getId(), newDelta);

        Transaction saved = transactionRepository.save(existing);
        log.info("Transaction updated: uuid={}", transactionUuid);
        return saved;
    }

    // ---- Delete ----

    @Transactional
    @CacheEvict(value = {"dashboard", "transactions"}, key = "#userEmail")
    public void deleteTransaction(String userEmail, String transactionUuid) {
        Transaction transaction = getTransaction(userEmail, transactionUuid);

        // Reverse balance
        BigDecimal reverseDelta = computeBalanceDelta(transaction.getType(), transaction.getAmount()).negate();
        accountRepository.adjustBalance(
                transaction.getAccount().getId(), transaction.getUser().getId(), reverseDelta);

        transactionRepository.delete(transaction);
        log.info("Transaction deleted: uuid={}, user={}", transactionUuid, userEmail);
    }

    // ---- Analytics ----

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String userEmail, LocalDate startDate, LocalDate endDate) {
        User user = getActiveUser(userEmail);

        BigDecimal totalIncome = transactionRepository.sumByUserAndTypeAndDateRange(
                user.getId(), TransactionType.INCOME, startDate, endDate);
        BigDecimal totalExpense = transactionRepository.sumByUserAndTypeAndDateRange(
                user.getId(), TransactionType.EXPENSE, startDate, endDate);

        totalIncome  = totalIncome  != null ? totalIncome  : BigDecimal.ZERO;
        totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;

        return Map.of(
                "totalIncome",  totalIncome,
                "totalExpense", totalExpense,
                "netSavings",   totalIncome.subtract(totalExpense),
                "categoryBreakdown", transactionRepository.getCategoryBreakdown(
                        user.getId(), startDate, endDate),
                "dailyTrend", transactionRepository.getDailyTrend(
                        user.getId(), startDate, endDate)
        );
    }

    // ---- Private helpers ----

    private BigDecimal computeBalanceDelta(TransactionType type, BigDecimal amount) {
        return switch (type) {
            case INCOME   ->  amount;
            case EXPENSE  -> amount.negate();
            case TRANSFER -> BigDecimal.ZERO; // Handled separately with two account updates
        };
    }

    @Async
    @Transactional
    public void updateBudgetsAsync(Long userId, Long categoryId, LocalDate date) {
        try {
            List<Budget> budgets = budgetRepository
                    .findActiveBudgetsForCategoryAndDate(userId, categoryId, date);

            for (Budget budget : budgets) {
                BigDecimal spent = transactionRepository.sumExpenseByCategory(
                        userId,
                        categoryId,
                        budget.getStartDate(),
                        budget.getEndDate());
                budgetRepository.updateSpent(budget.getId(),
                        spent != null ? spent : BigDecimal.ZERO);

                if (budget.isAlertThresholdReached()) {
                    log.info("Budget alert: userId={}, budgetId={}, utilization={}%",
                            userId, budget.getId(), budget.utilizationPercent());
                    // TODO: publish BudgetAlertEvent to async event bus / push notification service
                }
            }
        } catch (Exception e) {
            log.error("Async budget update failed for userId={}, categoryId={}: {}",
                    userId, categoryId, e.getMessage());
        }
    }

    private User getActiveUser(String email) {
        return userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Account getAccountForUser(String accountUuid, String userEmail) {
        return accountRepository.findByUuidAndUserEmail(accountUuid, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountUuid));
    }
}
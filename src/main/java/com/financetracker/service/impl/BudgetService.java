package com.financetracker.service.impl;

import com.financetracker.dto.request.BudgetRequest;
import com.financetracker.entity.Budget;
import com.financetracker.entity.Category;
import com.financetracker.entity.User;
import com.financetracker.exception.BusinessException;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.BudgetRepository;
import com.financetracker.repository.CategoryRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Budget createBudget(String userEmail, BudgetRequest request) {
        User user = getActiveUser(userEmail);
        validateDateRange(request);

        Category category = null;
        if (request.getCategoryUuid() != null) {
            category = categoryRepository.findById(Long.parseLong(request.getCategoryUuid()))
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }

        Budget budget = Budget.builder()
                .user(user)
                .category(category)
                .name(request.getName())
                .amount(request.getAmount())
                .period(request.getPeriod())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .alertThreshold(request.getAlertThreshold())
                .build();

        // Calculate current spent for the period
        if (category != null) {
            var spent = transactionRepository.sumExpenseByCategory(
                    user.getId(), category.getId(), request.getStartDate(), request.getEndDate());
            if (spent != null) budget.setSpent(spent);
        }

        Budget saved = budgetRepository.save(budget);
        log.info("Budget created: uuid={}, user={}", saved.getUuid(), userEmail);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Budget> getBudgets(String userEmail) {
        User user = getActiveUser(userEmail);
        return budgetRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public Budget getBudget(String userEmail, String budgetUuid) {
        User user = getActiveUser(userEmail);
        return budgetRepository.findByUuidAndUserId(budgetUuid, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + budgetUuid));
    }

    @Transactional
    public void deleteBudget(String userEmail, String budgetUuid) {
        Budget budget = getBudget(userEmail, budgetUuid);
        budget.setActive(false);
        budgetRepository.save(budget);
        log.info("Budget soft-deleted: uuid={}", budgetUuid);
    }

    /**
     * Scheduled job: recalculate all active budget spent amounts nightly.
     * This corrects any drift from async updates or transaction edits.
     * Runs at 2 AM UTC daily.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void recalculateAllBudgets() {
        log.info("Starting nightly budget recalculation...");
        LocalDate today = LocalDate.now();
        // In production, paginate this for millions of budgets
        List<Budget> activeBudgets = budgetRepository.findAll().stream()
                .filter(b -> b.isActive() &&
                        !b.getEndDate().isBefore(today) &&
                        !b.getStartDate().isAfter(today))
                .toList();

        for (Budget budget : activeBudgets) {
            try {
                if (budget.getCategory() != null) {
                    var spent = transactionRepository.sumExpenseByCategory(
                            budget.getUser().getId(),
                            budget.getCategory().getId(),
                            budget.getStartDate(),
                            budget.getEndDate());
                    budgetRepository.updateSpent(budget.getId(),
                            spent != null ? spent : java.math.BigDecimal.ZERO);
                }
            } catch (Exception e) {
                log.error("Budget recalculation failed for budgetId={}: {}", budget.getId(), e.getMessage());
            }
        }
        log.info("Budget recalculation complete. Processed {} budgets.", activeBudgets.size());
    }

    private void validateDateRange(BudgetRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("End date must be after start date");
        }
    }

    private User getActiveUser(String email) {
        return userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
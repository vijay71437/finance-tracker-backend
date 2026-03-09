package com.financetracker.service;

import com.financetracker.dto.request.BudgetRequest;
import com.financetracker.entity.Budget;
import com.financetracker.entity.Budget.BudgetPeriod;
import com.financetracker.entity.Category;
import com.financetracker.entity.User;
import com.financetracker.exception.BusinessException;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.BudgetRepository;
import com.financetracker.repository.CategoryRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.service.impl.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetService")
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private BudgetService budgetService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L).uuid("user-uuid-1")
                .email("user@example.com")
                .fullName("Test User")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("createBudget - should create budget and populate current spent")
    void createBudget_withCategory_populatesCurrentSpent() {
        // Arrange
        Category category = Category.builder().id(5L).name("Food").type(Category.CategoryType.EXPENSE).build();
        BudgetRequest request = buildRequest("5", new BigDecimal("500.00"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        when(userRepository.findActiveByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(transactionRepository.sumExpenseByCategory(
                testUser.getId(), 5L,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(new BigDecimal("120.00"));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        // Act
        Budget saved = budgetService.createBudget(testUser.getEmail(), request);

        // Assert
        assertThat(saved.getSpent()).isEqualByComparingTo("120.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("createBudget - should throw BusinessException when end date before start date")
    void createBudget_invalidDateRange_throwsBusinessException() {
        BudgetRequest request = buildRequest(null, new BigDecimal("500.00"),
                LocalDate.of(2024, 1, 31), LocalDate.of(2024, 1, 1)); // end < start

        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> budgetService.createBudget(testUser.getEmail(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("End date must be after start date");
    }

    @Test
    @DisplayName("getBudgets - should return active budgets only")
    void getBudgets_validUser_returnsActiveBudgets() {
        Budget b1 = Budget.builder().id(1L).uuid("b-uuid-1").name("Food Budget")
                .amount(new BigDecimal("500.00")).isActive(true).build();
        Budget b2 = Budget.builder().id(2L).uuid("b-uuid-2").name("Travel Budget")
                .amount(new BigDecimal("1000.00")).isActive(true).build();

        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
        when(budgetRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(List.of(b1, b2));

        List<Budget> budgets = budgetService.getBudgets(testUser.getEmail());

        assertThat(budgets).hasSize(2);
    }

    @Test
    @DisplayName("deleteBudget - should soft delete (set isActive=false)")
    void deleteBudget_exists_softDeletes() {
        Budget budget = Budget.builder().id(1L).uuid("b-uuid-1")
                .isActive(true).build();

        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
        when(budgetRepository.findByUuidAndUserId("b-uuid-1", testUser.getId()))
                .thenReturn(Optional.of(budget));
        when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        budgetService.deleteBudget(testUser.getEmail(), "b-uuid-1");

        assertThat(budget.isActive()).isFalse();
        verify(budgetRepository).save(budget);
    }

    @Test
    @DisplayName("Budget.utilizationPercent - should calculate correctly")
    void budgetEntity_utilizationPercent_calculatesCorrectly() {
        Budget budget = Budget.builder()
                .amount(new BigDecimal("1000.00"))
                .spent(new BigDecimal("800.00"))
                .alertThreshold(new BigDecimal("80.00"))
                .build();

        assertThat(budget.utilizationPercent()).isEqualByComparingTo("80.0000");
        assertThat(budget.isAlertThresholdReached()).isTrue();
        assertThat(budget.isOverBudget()).isFalse();
    }

    @Test
    @DisplayName("Budget.isOverBudget - should return true when spent exceeds amount")
    void budgetEntity_isOverBudget_returnsTrueWhenExceeded() {
        Budget budget = Budget.builder()
                .amount(new BigDecimal("500.00"))
                .spent(new BigDecimal("600.00"))
                .build();

        assertThat(budget.isOverBudget()).isTrue();
    }

    // ---- Helper ----

    private BudgetRequest buildRequest(String categoryUuid, BigDecimal amount,
                                       LocalDate start, LocalDate end) {
        BudgetRequest req = new BudgetRequest();
        req.setName("Test Budget");
        req.setCategoryUuid(categoryUuid);
        req.setAmount(amount);
        req.setPeriod(BudgetPeriod.MONTHLY);
        req.setStartDate(start);
        req.setEndDate(end);
        req.setAlertThreshold(new BigDecimal("80.00"));
        return req;
    }
}
package com.financetracker.service;

import com.financetracker.dto.request.TransactionRequest;
import com.financetracker.entity.*;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.*;
import com.financetracker.service.impl.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService.
 *
 * <p>Uses Mockito to isolate the service from infrastructure.
 * All collaborators (repositories) are mocked.
 *
 * <p>Test naming convention: methodName_stateUnderTest_expectedBehavior
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Account testAccount;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .uuid("user-uuid-1")
                .email("test@example.com")
                .fullName("Test User")
                .currency("USD")
                .build();

        testAccount = Account.builder()
                .id(10L)
                .uuid("account-uuid-1")
                .user(testUser)
                .name("Main Wallet")
                .accountType(Account.AccountType.CASH)
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .isActive(true)
                .build();

        testCategory = Category.builder()
                .id(5L)
                .name("Food & Dining")
                .type(Category.CategoryType.EXPENSE)
                .isSystem(true)
                .build();
    }

    @Nested
    @DisplayName("createTransaction")
    class CreateTransaction {

        @Test
        @DisplayName("should create EXPENSE transaction and decrease account balance")
        void createTransaction_expense_decreasesBalance() {
            // Arrange
            TransactionRequest request = buildRequest(
                    new BigDecimal("50.00"), Transaction.TransactionType.EXPENSE);

            when(userRepository.findActiveByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(accountRepository.findByUuidAndUserEmail(
                    testAccount.getUuid(), testUser.getEmail()))
                    .thenReturn(Optional.of(testAccount));
            when(categoryRepository.findById(5L))
                    .thenReturn(Optional.of(testCategory));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> {
                        Transaction t = inv.getArgument(0);
                        t.setId(100L);
                        return t;
                    });
            when(accountRepository.adjustBalance(
                    eq(testAccount.getId()), eq(testUser.getId()), any()))
                    .thenReturn(1);

            // Act
            Transaction result = transactionService.createTransaction(
                    testUser.getEmail(), request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualByComparingTo("50.00");
            assertThat(result.getType()).isEqualTo(Transaction.TransactionType.EXPENSE);

            // Verify balance was decreased (negative delta for EXPENSE)
            verify(accountRepository).adjustBalance(
                    eq(testAccount.getId()),
                    eq(testUser.getId()),
                    eq(new BigDecimal("50.00").negate()));
        }

        @Test
        @DisplayName("should create INCOME transaction and increase account balance")
        void createTransaction_income_increasesBalance() {
            // Arrange
            TransactionRequest request = buildRequest(
                    new BigDecimal("2000.00"), Transaction.TransactionType.INCOME);
            request.setCategoryId(null);

            when(userRepository.findActiveByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(accountRepository.findByUuidAndUserEmail(any(), any()))
                    .thenReturn(Optional.of(testAccount));
            when(transactionRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.adjustBalance(any(), any(), any())).thenReturn(1);

            // Act
            transactionService.createTransaction(testUser.getEmail(), request);

            // Assert — positive delta for INCOME
            verify(accountRepository).adjustBalance(
                    eq(testAccount.getId()),
                    eq(testUser.getId()),
                    eq(new BigDecimal("2000.00")));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when account not found")
        void createTransaction_accountNotFound_throwsException() {
            // Arrange
            TransactionRequest request = buildRequest(
                    new BigDecimal("50.00"), Transaction.TransactionType.EXPENSE);

            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
            when(accountRepository.findByUuidAndUserEmail(any(), any()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    transactionService.createTransaction(testUser.getEmail(), request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void createTransaction_userNotFound_throwsException() {
            // Arrange
            TransactionRequest request = buildRequest(
                    new BigDecimal("50.00"), Transaction.TransactionType.EXPENSE);

            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    transactionService.createTransaction("unknown@example.com", request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw BusinessException when balance update fails (concurrent modification)")
        void createTransaction_balanceUpdateFails_throwsBusinessException() {
            // Arrange
            TransactionRequest request = buildRequest(
                    new BigDecimal("50.00"), Transaction.TransactionType.EXPENSE);
            request.setCategoryId(null);

            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
            when(accountRepository.findByUuidAndUserEmail(any(), any()))
                    .thenReturn(Optional.of(testAccount));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.adjustBalance(any(), any(), any())).thenReturn(0); // 0 rows updated

            // Act & Assert
            assertThatThrownBy(() ->
                    transactionService.createTransaction(testUser.getEmail(), request))
                    .isInstanceOf(com.financetracker.exception.BusinessException.class)
                    .hasMessageContaining("Failed to update account balance");
        }
    }

    @Nested
    @DisplayName("getTransactions")
    class GetTransactions {

        @Test
        @DisplayName("should return paginated transactions for user")
        void getTransactions_validUser_returnsPaginatedResults() {
            // Arrange
            LocalDate start = LocalDate.now().withDayOfMonth(1);
            LocalDate end = LocalDate.now();
            PageRequest pageable = PageRequest.of(0, 20);

            Transaction t1 = Transaction.builder()
                    .id(1L).uuid("txn-1").user(testUser)
                    .amount(new BigDecimal("25.00"))
                    .type(Transaction.TransactionType.EXPENSE)
                    .transactionDate(LocalDate.now())
                    .build();

            Page<Transaction> mockPage = new PageImpl<>(List.of(t1));

            when(userRepository.findActiveByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(transactionRepository.findByUserAndDateRange(
                    testUser.getId(), start, end, pageable))
                    .thenReturn(mockPage);

            // Act
            Page<Transaction> result = transactionService.getTransactions(
                    testUser.getEmail(), start, end, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUuid()).isEqualTo("txn-1");
        }
    }

    @Nested
    @DisplayName("deleteTransaction")
    class DeleteTransaction {

        @Test
        @DisplayName("should delete transaction and reverse account balance")
        void deleteTransaction_validTransaction_reversesBalance() {
            // Arrange
            Transaction existing = Transaction.builder()
                    .id(100L).uuid("txn-uuid-1")
                    .user(testUser).account(testAccount)
                    .amount(new BigDecimal("75.00"))
                    .type(Transaction.TransactionType.EXPENSE)
                    .transactionDate(LocalDate.now())
                    .build();

            when(transactionRepository.findByUuidAndUserEmail("txn-uuid-1", testUser.getEmail()))
                    .thenReturn(Optional.of(existing));
            when(accountRepository.adjustBalance(any(), any(), any())).thenReturn(1);

            // Act
            transactionService.deleteTransaction(testUser.getEmail(), "txn-uuid-1");

            // Assert — reverse of EXPENSE = +75
            verify(accountRepository).adjustBalance(
                    eq(testAccount.getId()),
                    eq(testUser.getId()),
                    eq(new BigDecimal("75.00"))); // Positive (reversing expense)
            verify(transactionRepository).delete(existing);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when transaction not found")
        void deleteTransaction_notFound_throwsException() {
            when(transactionRepository.findByUuidAndUserEmail(any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    transactionService.deleteTransaction(testUser.getEmail(), "nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("should return income, expense and net savings")
        void getSummary_validPeriod_returnsSummary() {
            // Arrange
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 1, 31);

            when(userRepository.findActiveByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(transactionRepository.sumByUserAndTypeAndDateRange(
                    testUser.getId(), Transaction.TransactionType.INCOME, start, end))
                    .thenReturn(new BigDecimal("5000.00"));
            when(transactionRepository.sumByUserAndTypeAndDateRange(
                    testUser.getId(), Transaction.TransactionType.EXPENSE, start, end))
                    .thenReturn(new BigDecimal("3200.00"));
            when(transactionRepository.getCategoryBreakdown(testUser.getId(), start, end))
                    .thenReturn(List.of());
            when(transactionRepository.getDailyTrend(testUser.getId(), start, end))
                    .thenReturn(List.of());

            // Act
            var summary = transactionService.getSummary(testUser.getEmail(), start, end);

            // Assert
            assertThat(summary.get("totalIncome")).isEqualTo(new BigDecimal("5000.00"));
            assertThat(summary.get("totalExpense")).isEqualTo(new BigDecimal("3200.00"));
            assertThat(summary.get("netSavings")).isEqualTo(new BigDecimal("1800.00"));
        }

        @Test
        @DisplayName("should handle null income/expense (no transactions) gracefully")
        void getSummary_noTransactions_returnsZeros() {
            // Arrange
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 1, 31);

            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(testUser));
            when(transactionRepository.sumByUserAndTypeAndDateRange(any(), any(), any(), any()))
                    .thenReturn(null); // DB returns NULL for SUM with no rows
            when(transactionRepository.getCategoryBreakdown(any(), any(), any()))
                    .thenReturn(List.of());
            when(transactionRepository.getDailyTrend(any(), any(), any()))
                    .thenReturn(List.of());

            // Act
            var summary = transactionService.getSummary(testUser.getEmail(), start, end);

            // Assert — NullPointerException should NOT be thrown
            assertThat(summary.get("totalIncome")).isEqualTo(BigDecimal.ZERO);
            assertThat(summary.get("totalExpense")).isEqualTo(BigDecimal.ZERO);
            assertThat(summary.get("netSavings")).isEqualTo(BigDecimal.ZERO);
        }
    }

    // ---- Helpers ----

    private TransactionRequest buildRequest(BigDecimal amount, Transaction.TransactionType type) {
        TransactionRequest req = new TransactionRequest();
        req.setAccountUuid(testAccount.getUuid());
        req.setCategoryId(String.valueOf(testCategory.getId()));
        req.setAmount(amount);
        req.setType(type);
        req.setDescription("Test transaction");
        req.setTransactionDate(LocalDate.now());
        return req;
    }
}
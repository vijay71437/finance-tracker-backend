package com.financetracker.repository;

import com.financetracker.dto.response.CategorySpendSummary;
import com.financetracker.dto.response.DailySpendSummary;
import com.financetracker.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Transaction repository.
 *
 * <p>All queries include {@code user_id} and {@code transaction_date} range
 * to leverage MySQL partition pruning for maximum performance.
 *
 * <p>{@link JpaSpecificationExecutor} enables dynamic filter queries
 * (by date, category, amount range, type) without combinatorial query methods.
 */
@Repository
public interface TransactionRepository extends
        JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByUuidAndUserEmail(String uuid, String email);

    /**
     * Paginated transaction list for a user within a date range.
     * ALWAYS pass date bounds for partition pruning.
     */
    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.account
        WHERE t.user.id = :userId
          AND t.transactionDate BETWEEN :startDate AND :endDate
        ORDER BY t.transactionDate DESC, t.transactionTime DESC
        """)
    Page<Transaction> findByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Sum of expenses per category for analytics.
     * Returns projection interface for lightweight data transfer.
     */
    @Query("""
        SELECT c.name AS categoryName,
               c.icon AS categoryIcon,
               c.color AS categoryColor,
               SUM(t.amount) AS totalAmount,
               COUNT(t.id) AS transactionCount
        FROM Transaction t
        JOIN t.category c
        WHERE t.user.id = :userId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :startDate AND :endDate
        GROUP BY c.id, c.name, c.icon, c.color
        ORDER BY totalAmount DESC
        """)
    List<CategorySpendSummary> getCategoryBreakdown(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Daily spend aggregation for trend charts.
     */
    @Query("""
        SELECT t.transactionDate AS date,
               SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate BETWEEN :startDate AND :endDate
        GROUP BY t.transactionDate
        ORDER BY t.transactionDate ASC
        """)
    List<DailySpendSummary> getDailyTrend(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Total income / expense for the period (dashboard summary card).
     */
    @Query("""
        SELECT SUM(t.amount) FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = :type
          AND t.transactionDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumByUserAndTypeAndDateRange(
            @Param("userId") Long userId,
            @Param("type") Transaction.TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Total expense for a category in a period — used to update budget.spent.
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.user.id = :userId
          AND t.category.id = :categoryId
          AND t.type = 'EXPENSE'
          AND t.transactionDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumExpenseByCategory(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByUuidAndUserEmail(String uuid, String email);
}
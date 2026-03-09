package com.financetracker.repository;

import com.financetracker.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    Optional<Budget> findByUuidAndUserId(String uuid, Long userId);

    Optional<Budget> findByUuidAndUserEmail(String uuid, String email);

    @Query("""
        SELECT b FROM Budget b
        WHERE b.user.id = :userId
          AND b.isActive = true
          AND b.startDate <= :date
          AND b.endDate >= :date
        """)
    List<Budget> findActiveBudgetsForDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Find budgets matching a specific category (for auto-update after transaction).
     */
    @Query("""
        SELECT b FROM Budget b
        WHERE b.user.id = :userId
          AND b.isActive = true
          AND (b.category.id = :categoryId OR b.category IS NULL)
          AND b.startDate <= :date
          AND b.endDate >= :date
        """)
    List<Budget> findActiveBudgetsForCategoryAndDate(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE Budget b SET b.spent = :spent, b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :id")
    void updateSpent(@Param("id") Long id, @Param("spent") BigDecimal spent);
}
package com.financetracker.repository;



import com.financetracker.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdAndIsActiveTrue(Long userId);

    Optional<Account> findByUuidAndUserId(String uuid, Long userId);

    Optional<Account> findByUuidAndUserEmail(String uuid, String email);

    boolean existsByUuidAndUserEmail(String uuid, String email);

    /**
     * Atomic balance update — avoids loading the entity to prevent lost updates.
     * The @Version check in the entity prevents concurrent overwrites.
     */
    @Modifying
    @Query("""
        UPDATE Account a SET a.balance = a.balance + :delta, a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.id = :accountId AND a.user.id = :userId
        """)
    int adjustBalance(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("delta") BigDecimal delta);
}

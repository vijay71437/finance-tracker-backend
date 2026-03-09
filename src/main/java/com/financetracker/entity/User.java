package com.financetracker.entity;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity — represents an authenticated application user.
 *
 * <p>Uses optimistic locking ({@code @Version}) to handle concurrent updates
 * safely without database-level locks, critical at scale.
 *
 * <p>UUID is exposed externally; internal auto-increment ID is used
 * for FK relationships and joins (integer PKs are faster for MySQL).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "uuid")
@ToString(exclude = "passwordHash")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false, length = 36)
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ROLE_USER";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

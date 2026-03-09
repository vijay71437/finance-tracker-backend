-- ============================================================
-- V1__init_schema.sql
-- Finance Tracker Initial Schema
-- Optimized for millions of users with proper indexing
-- ============================================================

-- Users table
CREATE TABLE users (
                       id              BIGINT          NOT NULL AUTO_INCREMENT,
                       uuid            CHAR(36)        NOT NULL,
                       email           VARCHAR(255)    NOT NULL,
                       password_hash   VARCHAR(255)    NOT NULL,
                       full_name       VARCHAR(100)    NOT NULL,
                       phone           VARCHAR(20),
                       currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
                       timezone        VARCHAR(50)     NOT NULL DEFAULT 'UTC',
                       role            VARCHAR(20)     NOT NULL DEFAULT 'ROLE_USER',
                       is_active       TINYINT(1)      NOT NULL DEFAULT 1,
                       is_verified     TINYINT(1)      NOT NULL DEFAULT 0,
                       created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                       updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                       version         BIGINT          NOT NULL DEFAULT 0,   -- Optimistic locking
                       PRIMARY KEY (id),
                       UNIQUE KEY uq_users_uuid    (uuid),
                       UNIQUE KEY uq_users_email   (email),
                       INDEX idx_users_active      (is_active),
                       INDEX idx_users_created_at  (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Refresh tokens
CREATE TABLE refresh_tokens (
                                id          BIGINT          NOT NULL AUTO_INCREMENT,
                                user_id     BIGINT          NOT NULL,
                                token       VARCHAR(512)    NOT NULL,
                                expires_at  DATETIME(3)     NOT NULL,
                                revoked     TINYINT(1)      NOT NULL DEFAULT 0,
                                created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                PRIMARY KEY (id),
                                UNIQUE KEY uq_refresh_token (token),
                                INDEX idx_refresh_user_id   (user_id),
                                INDEX idx_refresh_expires   (expires_at),
                                CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Categories (system + user-defined)
CREATE TABLE categories (
                            id          BIGINT          NOT NULL AUTO_INCREMENT,
                            user_id     BIGINT,                                  -- NULL = system category
                            name        VARCHAR(100)    NOT NULL,
                            icon        VARCHAR(50),
                            color       VARCHAR(7),
                            type        VARCHAR(10)     NOT NULL,                -- INCOME / EXPENSE
                            is_system   TINYINT(1)      NOT NULL DEFAULT 0,
                            created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            PRIMARY KEY (id),
                            INDEX idx_cat_user_id       (user_id),
                            INDEX idx_cat_type          (type),
                            CONSTRAINT fk_cat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Accounts (wallets, bank accounts, credit cards)
CREATE TABLE accounts (
                          id              BIGINT          NOT NULL AUTO_INCREMENT,
                          uuid            VARCHAR(36)        NOT NULL,
                          user_id         BIGINT          NOT NULL,
                          name            VARCHAR(100)    NOT NULL,
                          account_type    VARCHAR(20)     NOT NULL,            -- CASH, BANK, CREDIT, SAVINGS, INVESTMENT
                          balance         DECIMAL(19,4)   NOT NULL DEFAULT 0.0000,
                          currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
                          color           VARCHAR(7),
                          icon            VARCHAR(50),
                          is_active       TINYINT(1)      NOT NULL DEFAULT 1,
                          created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                          updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                          version         BIGINT          NOT NULL DEFAULT 0,
                          PRIMARY KEY (id),
                          UNIQUE KEY uq_accounts_uuid (uuid),
                          INDEX idx_accounts_user_id  (user_id),
                          INDEX idx_accounts_active   (user_id, is_active),
                          CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Transactions (partitioned by month — NO foreign keys, MySQL limitation)
-- Referential integrity enforced in the application service layer
CREATE TABLE transactions (
                              id               BIGINT          NOT NULL AUTO_INCREMENT,
                              uuid             CHAR(36)        NOT NULL,
                              user_id          BIGINT          NOT NULL,
                              account_id       BIGINT          NOT NULL,
                              category_id      BIGINT,
                              amount           DECIMAL(19,4)   NOT NULL,
                              type             VARCHAR(10)     NOT NULL,
                              description      VARCHAR(500),
                              note             TEXT,
                              transaction_date DATE            NOT NULL,
                              transaction_time DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                              reference_id     VARCHAR(100),
                              is_recurring     TINYINT(1)      NOT NULL DEFAULT 0,
                              recurring_id     BIGINT,
                              created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                              updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                              version          BIGINT          NOT NULL DEFAULT 0,
                              PRIMARY KEY (id, transaction_date),
                              UNIQUE KEY uq_txn_uuid (uuid, transaction_date),
                              INDEX idx_txn_user_date           (user_id, transaction_date),
                              INDEX idx_txn_user_account        (user_id, account_id),
                              INDEX idx_txn_user_category       (user_id, category_id),
                              INDEX idx_txn_user_type_date      (user_id, type, transaction_date),
                              INDEX idx_txn_created_at          (created_at)
    -- NO FOREIGN KEY constraints — MySQL error 1506:
    -- foreign keys are incompatible with partitioned tables
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY RANGE (YEAR(transaction_date) * 100 + MONTH(transaction_date)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    PARTITION p202403 VALUES LESS THAN (202404),
    PARTITION p202404 VALUES LESS THAN (202405),
    PARTITION p202405 VALUES LESS THAN (202406),
    PARTITION p202406 VALUES LESS THAN (202407),
    PARTITION p202407 VALUES LESS THAN (202408),
    PARTITION p202408 VALUES LESS THAN (202409),
    PARTITION p202409 VALUES LESS THAN (202410),
    PARTITION p202410 VALUES LESS THAN (202411),
    PARTITION p202411 VALUES LESS THAN (202412),
    PARTITION p202412 VALUES LESS THAN (202501),
    PARTITION p_future  VALUES LESS THAN MAXVALUE
  );
-- Budgets
CREATE TABLE budgets (
                         id              BIGINT          NOT NULL AUTO_INCREMENT,
                         uuid            CHAR(36)        NOT NULL,
                         user_id         BIGINT          NOT NULL,
                         category_id     BIGINT,
                         name            VARCHAR(100)    NOT NULL,
                         amount          DECIMAL(19,4)   NOT NULL,
                         spent           DECIMAL(19,4)   NOT NULL DEFAULT 0.0000,
                         period          VARCHAR(10)     NOT NULL,            -- WEEKLY, MONTHLY, YEARLY
                         start_date      DATE            NOT NULL,
                         end_date        DATE            NOT NULL,
                         alert_threshold DECIMAL(5,2)    NOT NULL DEFAULT 80.00, -- Alert at 80% by default
                         is_active       TINYINT(1)      NOT NULL DEFAULT 1,
                         created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                         updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                         version         BIGINT          NOT NULL DEFAULT 0,
                         PRIMARY KEY (id),
                         UNIQUE KEY uq_budgets_uuid      (uuid),
                         INDEX idx_budgets_user_id       (user_id),
                         INDEX idx_budgets_period_dates  (user_id, period, start_date, end_date),
                         CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                         CONSTRAINT fk_budgets_cat  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Recurring transaction rules
CREATE TABLE recurring_rules (
                                 id              BIGINT          NOT NULL AUTO_INCREMENT,
                                 uuid            CHAR(36)        NOT NULL,
                                 user_id         BIGINT          NOT NULL,
                                 account_id      BIGINT          NOT NULL,
                                 category_id     BIGINT,
                                 amount          DECIMAL(19,4)   NOT NULL,
                                 type            VARCHAR(10)     NOT NULL,
                                 description     VARCHAR(500),
                                 frequency       VARCHAR(20)     NOT NULL,           -- DAILY, WEEKLY, BIWEEKLY, MONTHLY, YEARLY
                                 start_date      DATE            NOT NULL,
                                 end_date        DATE,
                                 next_run_date   DATE            NOT NULL,
                                 is_active       TINYINT(1)      NOT NULL DEFAULT 1,
                                 created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                                 PRIMARY KEY (id),
                                 UNIQUE KEY uq_recurring_uuid    (uuid),
                                 INDEX idx_recurring_user        (user_id),
                                 INDEX idx_recurring_next_run    (next_run_date, is_active),
                                 CONSTRAINT fk_recurring_user    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audit log for compliance
CREATE TABLE audit_logs (
                            id          BIGINT          NOT NULL AUTO_INCREMENT,
                            user_id     BIGINT,
                            action      VARCHAR(100)    NOT NULL,
                            entity_type VARCHAR(50)     NOT NULL,
                            entity_id   BIGINT,
                            old_value   JSON,
                            new_value   JSON,
                            ip_address  VARCHAR(45),
                            user_agent  VARCHAR(255),
                            created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            PRIMARY KEY (id),
                            INDEX idx_audit_user_id     (user_id),
                            INDEX idx_audit_entity      (entity_type, entity_id),
                            INDEX idx_audit_created_at  (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed system categories
INSERT INTO categories (name, icon, color, type, is_system) VALUES
                                                                ('Salary',        'briefcase',      '#4CAF50', 'INCOME',  1),
                                                                ('Freelance',     'laptop',         '#8BC34A', 'INCOME',  1),
                                                                ('Investments',   'trending-up',    '#009688', 'INCOME',  1),
                                                                ('Other Income',  'plus-circle',    '#00BCD4', 'INCOME',  1),
                                                                ('Food & Dining', 'utensils',       '#FF5722', 'EXPENSE', 1),
                                                                ('Transport',     'car',            '#795548', 'EXPENSE', 1),
                                                                ('Shopping',      'shopping-bag',   '#E91E63', 'EXPENSE', 1),
                                                                ('Entertainment', 'film',           '#9C27B0', 'EXPENSE', 1),
                                                                ('Health',        'heart',          '#F44336', 'EXPENSE', 1),
                                                                ('Education',     'book',           '#3F51B5', 'EXPENSE', 1),
                                                                ('Utilities',     'zap',            '#FF9800', 'EXPENSE', 1),
                                                                ('Rent',          'home',           '#607D8B', 'EXPENSE', 1),
                                                                ('Travel',        'map',            '#2196F3', 'EXPENSE', 1),
                                                                ('Subscriptions', 'repeat',         '#673AB7', 'EXPENSE', 1),
                                                                ('Gifts',         'gift',           '#FF4081', 'EXPENSE', 1),
                                                                ('Other Expense', 'more-horizontal','#9E9E9E', 'EXPENSE', 1);
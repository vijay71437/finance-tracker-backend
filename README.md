# Finance Tracker API — Architecture & Developer Guide

**Version:** 1.0.0 | **Stack:** Spring Boot 3.2 · MySQL 8 · Redis · Java 21

---

## Table of Contents

1. [System Architecture](#1-system-architecture)
2. [Project Structure](#2-project-structure)
3. [API Reference](#3-api-reference)
4. [Database Design](#4-database-design)
5. [Security Architecture](#5-security-architecture)
6. [Caching Strategy](#6-caching-strategy)
7. [Scalability Design](#7-scalability-design)
8. [Development Workflow](#8-development-workflow)
9. [Testing Strategy](#9-testing-strategy)
10. [Configuration Reference](#10-configuration-reference)
11. [Deployment Guide](#11-deployment-guide)
12. [Operational Runbook](#12-operational-runbook)

---

## 1. System Architecture

### High-Level Architecture

```
                         ┌─────────────────────────┐
                         │      API Clients          │
                         │  (Mobile / Web / Third-  │
                         │   party integrations)     │
                         └────────────┬────────────┘
                                      │ HTTPS
                         ┌────────────▼────────────┐
                         │     Load Balancer         │
                         │  (AWS ALB / Nginx)        │
                         │  - TLS termination        │
                         │  - Health checks          │
                         └────────────┬────────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
   ┌──────────▼──────────┐ ┌──────────▼──────────┐ ┌──────────▼──────────┐
   │  API Pod (Spring    │ │  API Pod (Spring    │ │  API Pod (Spring    │
   │  Boot Instance 1)   │ │  Boot Instance 2)   │ │  Boot Instance N)   │
   │  - JWT Filter       │ │  - JWT Filter       │ │  - JWT Filter       │
   │  - Rate Limiting    │ │  - Rate Limiting    │ │  - Rate Limiting    │
   │  - Controllers      │ │  - Controllers      │ │  - Controllers      │
   │  - Services         │ │  - Services         │ │  - Services         │
   └──────────┬──────────┘ └──────────┬──────────┘ └──────────┬──────────┘
              └───────────────────────┼───────────────────────┘
                                      │
           ┌──────────────────────────┼────────────────────────┐
           │                          │                        │
┌──────────▼──────────┐  ┌────────────▼───────────┐ ┌─────────▼────────────┐
│   MySQL Cluster      │  │   Redis Cluster         │ │  Message Queue       │
│   (AWS RDS)          │  │   (AWS ElastiCache)     │ │  (future: SNS/SQS   │
│  - Primary (writes)  │  │  - L2 Cache (TTL)       │ │   for notifications) │
│  - Read Replica x2  │  │  - Session-less state   │ │                      │
│  - Partitioned txns  │  │  - Rate limit counters  │ │                      │
└─────────────────────┘  └────────────────────────┘ └─────────────────────┘
```

### Technology Decisions

| Concern | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot 3.2 | Production-grade, huge ecosystem, excellent observability |
| Language | Java 21 | Virtual threads (Project Loom) for high concurrency at low memory cost |
| Database | MySQL 8.0 | Range partitioning for transactions, mature ecosystem |
| Cache | Redis | Sub-millisecond latency, cluster mode for HA |
| Auth | JWT (HS256) | Stateless — scales horizontally without shared session store |
| Migrations | Flyway | Version-controlled, repeatable, team-safe schema changes |
| Docs | SpringDoc OpenAPI 3 | Auto-generated, always in sync with code |
| Metrics | Micrometer + Prometheus | Industry standard, pairs with Grafana |
| Testing | JUnit 5 + Mockito + Testcontainers | Real DB for integration tests, no mocking surprises |

---

## 2. Project Structure

```
finance-tracker-api/
├── src/main/java/com/financetracker/
│   ├── FinanceTrackerApplication.java     # Entry point
│   ├── config/                            # All @Configuration classes
│   │   ├── SecurityConfig.java            # Spring Security + CORS + headers
│   │   ├── CacheConfig.java               # Redis TTL per cache name
│   │   ├── AppConfig.java                 # Async executor + OpenAPI bean
│   │   └── RateLimitingFilter.java        # Per-IP token bucket
│   ├── controller/                        # REST controllers (thin — no business logic)
│   │   ├── AuthController.java
│   │   ├── TransactionController.java
│   │   ├── AccountController.java
│   │   └── BudgetController.java
│   ├── service/impl/                      # Business logic layer
│   │   ├── AuthService.java
│   │   ├── TransactionService.java
│   │   ├── AccountService.java
│   │   └── BudgetService.java
│   ├── repository/                        # Spring Data JPA interfaces
│   │   ├── UserRepository.java
│   │   ├── TransactionRepository.java     # Custom JPQL with partition-aware queries
│   │   ├── AccountRepository.java
│   │   ├── BudgetRepository.java
│   │   ├── CategoryRepository.java
│   │   └── RefreshTokenRepository.java
│   ├── entity/                            # JPA entities (domain model)
│   │   ├── User.java
│   │   ├── Account.java
│   │   ├── Transaction.java
│   │   ├── Category.java
│   │   ├── Budget.java
│   │   └── RefreshToken.java
│   ├── dto/
│   │   ├── request/                       # Input DTOs with Bean Validation
│   │   └── response/                      # Output DTOs + projections
│   ├── security/
│   │   ├── CustomUserDetailsService.java
│   │   └── jwt/
│   │       ├── JwtTokenProvider.java
│   │       └── JwtAuthenticationFilter.java
│   └── exception/
│       ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│       ├── ResourceNotFoundException.java
│       ├── ConflictException.java
│       ├── UnauthorizedException.java
│       └── BusinessException.java
├── src/main/resources/
│   ├── application.yml                    # Main config
│   ├── application-dev.yml                # Dev overrides
│   ├── application-prod.yml               # Prod overrides (secrets via env vars)
│   └── db/migration/
│       ├── V1__init_schema.sql            # Initial schema + seed data
│       └── V2__add_indexes.sql            # Example future migration
├── src/test/java/com/financetracker/
│   ├── service/
│   │   ├── TransactionServiceTest.java    # Unit tests (Mockito)
│   │   ├── AuthServiceTest.java
│   │   ├── BudgetServiceTest.java
│   │   └── JwtTokenProviderTest.java
│   └── controller/
│       └── TransactionControllerTest.java # Web layer slice tests
└── pom.xml
```

### Layered Architecture Principle

```
Controller  →  Service  →  Repository  →  Database
   (HTTP)    (Business)    (Data Access)   (MySQL)
              ↕ Cache (Redis)
```

**Rule: Never skip layers.**
- Controllers must not call repositories directly.
- Services own transactions (`@Transactional`).
- Repositories must not contain business logic.

---

## 3. API Reference

### Base URL
```
https://api.financetracker.com/api/v1
```

### Authentication

All endpoints (except `/auth/**`) require:
```
Authorization: Bearer <access_token>
```

### Endpoints

#### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Create new account |
| POST | `/auth/login` | Get access + refresh tokens |
| POST | `/auth/refresh` | Rotate tokens |
| POST | `/auth/logout` | Revoke all tokens |

#### Transactions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/transactions` | Create transaction |
| GET | `/transactions?startDate=&endDate=&page=&size=` | List (paginated) |
| GET | `/transactions/:uuid` | Get single |
| PUT | `/transactions/:uuid` | Update |
| DELETE | `/transactions/:uuid` | Delete |
| GET | `/transactions/summary?startDate=&endDate=` | Analytics summary |

#### Accounts
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/accounts` | Create account |
| GET | `/accounts` | List active accounts |
| GET | `/accounts/:uuid` | Get account |
| PUT | `/accounts/:uuid` | Update |
| DELETE | `/accounts/:uuid` | Soft delete |

#### Budgets
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/budgets` | Create budget |
| GET | `/budgets` | List active budgets |
| GET | `/budgets/:uuid` | Get budget |
| DELETE | `/budgets/:uuid` | Soft delete |

### Response Envelope

All responses follow a consistent structure:

```json
{
  "success": true,
  "message": "Transaction created",
  "data": { ... },
  "timestamp": "2024-03-15T10:30:00Z"
}
```

Error response:
```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": {
      "amount": "Amount must be positive",
      "transactionDate": "Transaction date cannot be in the future"
    }
  },
  "timestamp": "2024-03-15T10:30:00Z"
}
```

### HTTP Status Codes
| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Resource created |
| 400 | Validation error |
| 401 | Missing or invalid JWT |
| 403 | Forbidden (access to another user's resource) |
| 404 | Resource not found |
| 409 | Conflict (e.g., email already exists) |
| 422 | Business rule violation |
| 429 | Rate limit exceeded |
| 500 | Server error |

---

## 4. Database Design

### Schema Overview

```
users (1) ──< accounts (1) ──< transactions (many)
  │                                    │
  └──< categories (shared/user)  ──────┘
  │
  └──< budgets (1) ──── category (optional)
  │
  └──< recurring_rules
  │
  └──< refresh_tokens
```

### Transaction Table Partitioning

The `transactions` table is partitioned by year-month using MySQL RANGE partitioning.

**Why:** At 1M users with 10 transactions/day = 10M rows/day = ~365M rows/year. Without partitioning, full-table scans become prohibitively slow.

**How it helps:**
- Query `WHERE transaction_date BETWEEN '2024-01-01' AND '2024-01-31'` only scans the Jan 2024 partition.
- Old partitions can be dropped or archived without `DELETE` (instant operation).
- New partitions are added monthly via a scheduled maintenance job.

**Critical rule:** All transaction queries MUST include `transaction_date` in the WHERE clause to benefit from partition pruning.

```sql
-- GOOD — MySQL prunes to one partition
SELECT * FROM transactions
WHERE user_id = 123 AND transaction_date BETWEEN '2024-01-01' AND '2024-01-31';

-- BAD — Full scan across all partitions
SELECT * FROM transactions WHERE user_id = 123;
```

### Index Strategy

```sql
-- Transactions: primary query pattern (user + date range)
INDEX idx_txn_user_date (user_id, transaction_date)

-- Transactions: filter by type
INDEX idx_txn_user_type_date (user_id, type, transaction_date)

-- Users: auth lookup
UNIQUE KEY uq_users_email (email)

-- Accounts: filter active accounts per user
INDEX idx_accounts_active (user_id, is_active)
```

### Optimistic Locking

All entities with concurrent write potential (User, Account, Budget, Transaction) use `@Version` for optimistic locking. This avoids database-level `SELECT ... FOR UPDATE` locks which don't scale.

```java
@Version
private Long version;
```

If two requests update the same record simultaneously, one gets an `OptimisticLockingFailureException`. The `GlobalExceptionHandler` returns HTTP 409 with a "please retry" message.

---

## 5. Security Architecture

### JWT Flow

```
Client              API Server             MySQL
  │                      │                   │
  │─── POST /auth/login ─►│                   │
  │                      │── validate creds ─►│
  │                      │◄─ user record ─────│
  │◄── accessToken ───────│                   │
  │    refreshToken       │                   │
  │                       │                   │
  │─── GET /transactions ─►│                   │
  │    Bearer <token>     │                   │
  │                      │── validate JWT ──┐ │
  │                      │  (no DB call)    │ │
  │                      │◄─────────────────┘ │
  │                      │── query data ─────►│
  │◄── 200 OK ────────────│                   │
```

Access tokens are stateless — validation requires only the JWT secret (no DB lookup). This is what enables horizontal scaling.

### Security Hardening Checklist

- **Passwords:** BCrypt with strength 12 (approx. 400ms/hash on modern hardware — expensive enough to resist brute force).
- **JWT secret:** Minimum 256 bits. Rotate via rolling deployment.
- **HTTPS only:** Enforce at load balancer. Set HSTS header.
- **CORS:** Whitelist specific origins. Never use `*` in production.
- **Security headers:** X-Frame-Options: DENY, CSP, Referrer-Policy configured.
- **Rate limiting:** 100 req/min per IP (adjust per tier).
- **Input validation:** All request bodies validated with Bean Validation constraints.
- **No sensitive data in JWT:** Never put passwords, PII beyond email in claims.
- **Token rotation:** Refresh tokens are single-use (rotated on each refresh).
- **IDOR prevention:** User identity always derived from JWT, never from request body.

---

## 6. Caching Strategy

### Cache Names and TTLs

| Cache | Key Pattern | TTL | Invalidation |
|-------|------------|-----|--------------|
| `accounts` | `{userEmail}` | 10 min | On create/update/delete |
| `transactions` | `{userEmail}:{startDate}:{endDate}:{page}` | 2 min | On create/update/delete |
| `dashboard` | `{userEmail}` | 5 min | On transaction mutation |
| `categories` | Global | 30 min | Manual (rarely changes) |

### Cache-Aside Pattern

The application uses cache-aside (lazy loading):

```
1. Request comes in
2. Check Redis cache
   ├── Hit → return cached value
   └── Miss → query MySQL, store in Redis, return value
3. On mutation → evict affected cache keys
```

### Redis Key Namespace

All keys are prefixed with `ft:` to avoid conflicts in shared Redis instances:
```
ft:accounts::user@example.com
ft:transactions::user@example.com:2024-01-01:2024-01-31:0
```

---

## 7. Scalability Design

### Scaling to Millions of Users

#### Horizontal Scaling
- API pods are stateless (JWT auth, no server-side sessions).
- Add pods behind the load balancer — no code changes needed.
- All shared state lives in MySQL or Redis.

#### Database Scaling
1. **Connection pooling:** HikariCP with pool size 50 per pod. At 10 pods = 500 total connections. Tune MySQL `max_connections` accordingly.
2. **Read replicas:** Route `@Transactional(readOnly = true)` queries to read replicas via `spring.datasource.replica.url`.
3. **Table partitioning:** Transactions partitioned by month. New partition added monthly.
4. **Archival:** Partitions older than 2 years moved to cold storage (S3 + Athena).

#### Rate Limiting at Scale
The current in-process Bucket4j implementation works for single-node deployments. For multi-pod:
1. Migrate to `bucket4j-redis` backed store.
2. Or use AWS API Gateway / Nginx rate limiting at the edge.

#### Future Sharding Strategy (at 100M+ users)
Shard `transactions` by `user_id % N` across MySQL clusters. Use a consistent hashing router. This is a major migration — plan early.

### Performance Benchmarks (Target)

| Metric | Target | Notes |
|--------|--------|-------|
| P99 latency (read) | < 50ms | With Redis cache hit |
| P99 latency (write) | < 100ms | MySQL write + balance update |
| Throughput | 10,000 req/s | Per pod on 4 CPU |
| DB connections | 50/pod | HikariCP max pool |

---

## 8. Development Workflow

### How We Work (Team Process)

#### Git Branching Strategy (GitHub Flow)
```
main  ──────────────────────────────────────────► (always deployable)
         │                        │
         └── feature/FT-123-add ──┘
             - Branch from main
             - Open PR when ready
             - Minimum 1 reviewer approval
             - All CI checks must pass
             - Squash merge to main
```

#### Branch Naming Convention
```
feature/FT-{ticket}-{short-description}   # New feature
fix/FT-{ticket}-{short-description}       # Bug fix
hotfix/FT-{ticket}-{description}          # Production hotfix
chore/update-dependencies                 # Non-functional
```

#### Commit Message Format (Conventional Commits)
```
feat(transactions): add category breakdown to summary endpoint
fix(auth): prevent token reuse after logout
perf(db): add composite index on transactions user_id + date
test(budget): add unit tests for utilizationPercent calculation
docs: update API reference with new query params
chore: bump spring-boot to 3.2.4
```

### Definition of Done

A feature is **done** only when:
- [ ] Code is written and peer-reviewed
- [ ] Unit tests pass (coverage ≥ 80% for new code)
- [ ] Integration tests pass
- [ ] No new Checkstyle violations
- [ ] Database migration script added (if schema change)
- [ ] API documentation updated
- [ ] Performance impact assessed for queries
- [ ] Security impact reviewed (new endpoints, data exposure)
- [ ] Deployed to staging, smoke-tested

### Adding a New Feature (Step-by-Step)

**Example: Adding a "Notes" feature to transactions**

1. **Create migration**
   ```sql
   -- V3__add_transaction_notes.sql
   ALTER TABLE transactions ADD COLUMN note TEXT;
   ALTER TABLE transactions ADD COLUMN tags JSON;
   ```

2. **Update entity**
   ```java
   @Column(columnDefinition = "TEXT")
   private String note;
   ```

3. **Update DTO**
   ```java
   @Size(max = 5000)
   private String note;
   ```

4. **Update service** — map DTO to entity in `createTransaction`.

5. **Write tests first (TDD preferred)**
   ```java
   @Test
   void createTransaction_withNote_savesNote() { ... }
   ```

6. **Run tests**
   ```bash
   mvn test -pl . -Dtest=TransactionServiceTest
   ```

7. **Create PR** → request review → merge after approval.

---

## 9. Testing Strategy

### Testing Pyramid

```
         ┌──────────────┐
         │   E2E Tests   │  ← Postman / Newman (CI smoke tests)
         │  (Few, slow)  │
        ┌┴──────────────┴┐
        │ Integration    │  ← @SpringBootTest + Testcontainers
        │ Tests          │
       ┌┴────────────────┴┐
       │   Unit Tests      │  ← JUnit 5 + Mockito (most tests here)
       │   (Many, fast)    │
       └──────────────────┘
```

### Test Types and When to Use Them

#### Unit Tests (`src/test/java/.../service/`, `.../security/`)
- **What:** Test a single class in isolation. All dependencies mocked.
- **When:** For every service method, every util function, every JWT operation.
- **Speed:** ~5ms per test.

```java
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock TransactionRepository transactionRepository;
    @InjectMocks TransactionService transactionService;
    
    @Test
    void createTransaction_expense_decreasesBalance() { ... }
}
```

#### Web Layer Tests (`src/test/.../controller/`)
- **What:** Test HTTP request/response without starting the full server.
- **When:** Validate request validation, response format, HTTP status codes, auth.
- **Speed:** ~50ms per test.

```java
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean TransactionService transactionService;
    
    @Test
    @WithMockUser
    void createTransaction_missingAmount_returns400() { ... }
}
```

#### Integration Tests (add `src/test/.../integration/`)
- **What:** Full Spring context + real MySQL via Testcontainers.
- **When:** End-to-end flows: register → login → create transaction → verify balance.
- **Speed:** ~2–5s per test (DB startup amortized across suite).

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class TransactionIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    
    @Test
    void fullTransactionFlow_createAndDelete_balanceConsistent() { ... }
}
```

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=TransactionServiceTest

# Skip tests (build only)
mvn package -DskipTests

# With coverage report (outputs to target/site/jacoco/)
mvn test jacoco:report
```

### Coverage Requirements
- Service layer: ≥ 80% line coverage
- Controller layer: ≥ 70% (focus on happy/sad paths)
- Utility classes: ≥ 90%

---

## 10. Configuration Reference

### Environment Variables (Production)

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_HOST` | ✓ | MySQL host |
| `DB_PORT` | ✓ | MySQL port (default: 3306) |
| `DB_NAME` | ✓ | Database name |
| `DB_USER` | ✓ | MySQL username |
| `DB_PASSWORD` | ✓ | MySQL password (use secrets manager) |
| `REDIS_HOST` | ✓ | Redis host |
| `REDIS_PORT` | ✓ | Redis port |
| `REDIS_PASSWORD` | ✓ | Redis auth password |
| `JWT_SECRET` | ✓ | HMAC secret (min 32 chars, 256-bit key) |
| `JWT_EXPIRATION` | | Access token TTL in ms (default: 86400000) |
| `JWT_REFRESH_EXPIRATION` | | Refresh token TTL in ms (default: 604800000) |
| `RATE_LIMIT_RPM` | | Requests per minute per IP (default: 100) |
| `SERVER_PORT` | | HTTP port (default: 8080) |

### HikariCP Tuning Guide

```yaml
hikari:
  maximum-pool-size: 50   # Start here; adjust based on DB CPU usage
  minimum-idle: 10        # Keep connections warm
  idle-timeout: 600000    # Release idle connections after 10 min
  max-lifetime: 1800000   # Recycle connections every 30 min (before MySQL timeout)
```

**Formula:** `max-pool-size = (DB max_connections / pod count) - 10` (reserve 10 for admin)

---

## 11. Deployment Guide

### Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/finance-tracker-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

```bash
# Build
mvn clean package -DskipTests
docker build -t finance-tracker-api:1.0.0 .

# Run
docker run -p 8080:8080 \
  -e DB_HOST=mysql-host \
  -e DB_PASSWORD=secret \
  -e REDIS_HOST=redis-host \
  -e JWT_SECRET=your-256-bit-secret \
  finance-tracker-api:1.0.0
```

### Docker Compose (Local Development)

```yaml
version: '3.8'
services:
  api:
    build: .
    ports: ["8080:8080"]
    environment:
      DB_HOST: mysql
      DB_USER: root
      DB_PASSWORD: secret
      REDIS_HOST: redis
      JWT_SECRET: dev-secret-must-be-at-least-32-chars-long
    depends_on: [mysql, redis]

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: secret
      MYSQL_DATABASE: finance_tracker
    ports: ["3306:3306"]
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  mysql_data:
```

### Kubernetes (Production)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: finance-tracker-api
spec:
  replicas: 3                     # Start with 3, auto-scale on CPU
  selector:
    matchLabels:
      app: finance-tracker-api
  template:
    spec:
      containers:
      - name: api
        image: finance-tracker-api:1.0.0
        resources:
          requests: { memory: "512Mi", cpu: "500m" }
          limits:   { memory: "1Gi",  cpu: "2" }
        readinessProbe:
          httpGet: { path: /actuator/health/readiness, port: 8080 }
          initialDelaySeconds: 20
        livenessProbe:
          httpGet: { path: /actuator/health/liveness, port: 8080 }
          initialDelaySeconds: 30
        env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef: { name: db-secret, key: password }
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef: { name: jwt-secret, key: value }
```

---

## 12. Operational Runbook

### Health Check Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | App + DB + Redis health |
| `GET /actuator/metrics` | All Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |

### Key Metrics to Monitor (Grafana)

| Metric | Alert Threshold |
|--------|----------------|
| `http_server_requests_seconds_p99` | > 500ms |
| `hikaricp_connections_active` | > 80% of pool |
| `jvm_memory_used_bytes` | > 85% of heap |
| `redis_commands_failed_total` | Any increase |
| HTTP 5xx error rate | > 0.1% of requests |
| JWT validation failures | Spike (may indicate attack) |

### Common Operations

**Rotate JWT secret (zero-downtime):**
1. Generate new secret.
2. Update secret in secrets manager.
3. Rolling restart API pods (existing tokens valid until expiry ~24h).
4. After 24h, all tokens signed with new secret.

**Add monthly transaction partition:**
```sql
ALTER TABLE transactions
  REORGANIZE PARTITION p_future INTO (
    PARTITION p202502 VALUES LESS THAN (202503),
    PARTITION p_future VALUES LESS THAN MAXVALUE
  );
```

**Emergency: disable specific user**
```sql
UPDATE users SET is_active = 0 WHERE email = 'bad-actor@example.com';
-- Active JWTs will still work for up to 24h unless using token blocklist
```

---

## Appendix: Architecture Decision Records (ADRs)

### ADR-001: UUID vs Auto-increment for Public IDs
**Decision:** Use internal auto-increment `id` for DB relationships (faster joins), expose `uuid` externally.

**Rationale:** Sequential integer IDs in URLs allow enumeration attacks. UUIDs are random and safe to expose. JOINs on integers (8 bytes) are faster than on UUIDs (36 chars).

### ADR-002: Optimistic vs Pessimistic Locking for Balance Updates
**Decision:** Optimistic locking (`@Version`) + atomic `UPDATE balance = balance + delta` SQL.

**Rationale:** Pessimistic locking (SELECT ... FOR UPDATE) blocks other transactions and doesn't scale. Our atomic UPDATE + version check gives correctness without blocking. On conflict, return 409 and let the client retry.

### ADR-003: Sync vs Async Budget Updates
**Decision:** Budget `spent` amounts updated asynchronously after transactions.

**Rationale:** Updating budgets synchronously would double the transaction latency and add failure modes. Brief eventual consistency (< 1s) is acceptable for budget tracking. Nightly recalculation corrects any drift.

### ADR-004: Flyway for Schema Management
**Decision:** All schema changes via Flyway migrations. `ddl-auto: validate` in production.

**Rationale:** Manual schema changes in production are the #1 cause of incidents. Flyway ensures migrations are version-controlled, reviewed in PRs, and executed exactly once. Never use `ddl-auto: create/update` outside development.

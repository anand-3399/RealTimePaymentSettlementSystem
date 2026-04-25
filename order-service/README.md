# Order Service - Real-Time Payment Settlement System (RTPS)

## Overview
The **Order Service** is the entry point of the RTPS ecosystem. It is responsible for managing user accounts, authenticating requests via JWT, and capturing payment orders with a strict guarantee of idempotency and financial consistency.

---

## 🚀 Implemented Concepts (Production Hardened)

### 1. User Management & Security
- **JWT Refresh Token Rotation**: Implemented a state-of-the-art token rotation system. Every refresh call invalidates the old token and issues a new one, mitigating replay attacks.
- **Data Encryption at Rest**: Sensitive fields like `recipientBankAccount` are encrypted using **AES-256** before hitting the Oracle DB, ensuring compliance and data protection.
- **Password Reset Flow**: Secure account recovery with 15-minute time-limited tokens.
- **Centralized CORS**: Restricted access to trusted origins and headers, managed via `WebSecurityConfig`.

### 2. Idempotency & Reliability
- **Synchronous Kafka Transactions**: The service uses synchronous Kafka publishing within database transactions. If Kafka is unavailable, the entire transaction (Order + Idempotency Key) is rolled back.
- **Idempotency Engine**: Prevents duplicate payments using a unique key per request, with an automated cleanup task to purge expired keys.

### 3. Observability & Auditing
- **Correlation ID Tracing**: Integrated `X-Correlation-ID` across all logs and responses via `MDC`.
- **Audit Logging**: Explicit logging of security events, idempotency hits, and transaction failures for audit trails.
- **Metrics & Health**: Real-time monitoring via **Spring Boot Actuator** and **Prometheus**.

### 4. Advanced Persistence
- **Oracle SQL Consistency**: Leverages Oracle's ACID properties for high-integrity payment records.
- **Pagination & Sorting**: Robust history API with `Pageable` support for scalability.

---

## 🛠️ Error Handling & Custom Exceptions
- **`InvalidOrderException`**: For business/validation errors.
- **`UserNotFoundException`**: For account-related errors.
- **GlobalExceptionHandler**: Ensures all errors return a standardized JSON structure.

---

## 📁 Project Structure
```text
com.payment.order
├── config/         # Security, Web Interceptors, and CORS
├── controller/     # REST Controllers (Auth, Orders)
├── dto/            # Request/Response data objects
├── entity/         # JPA Entities with @Convert encryption
├── event/          # Synchronous Kafka Producer
├── exception/      # Custom Exceptions and Global Handler
├── repository/     # Repositories with Paging & Cleanup
└── service/        # Business Logic & Scheduled Tasks
```

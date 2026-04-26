# Real-Time Payment Settlement System (RTPS)

## Overview
RTPS is a high-performance, production-grade microservices ecosystem designed for reliable financial transactions. It ensures 100% message delivery, data encryption at rest, and distributed tracing across all services.

## Architecture
- **Order Service (Port 8081)**: Entry point for creating payment orders. Implements JWT auth, idempotency, and the Transactional Outbox pattern.
- **Payment Processor (Port 8082)**: Orchestrates payment execution with external banks (AJBank).
- **AJBank Service (Port 8083)**:
    - **Core Banking**: ACID-compliant internal transfers with pessimistic locking.
    - **Permanent Idempotency**: Lifetime deduplication of requests from the Order Service.
    - **Security**: Protected via `X-Internal-Secret` header.
- **Settlement Worker**: (Planned/In Progress) Handles bulk settlement and reconciliation.
- **UI Portal (Port 8000)**: Management dashboard built with Oracle JET.


## 🛠️ Global Tech Stack
- **Languages**: Java 17, Javascript
- **Frameworks**: Spring Boot 3.2, Oracle JET
- **Messaging**: Apache Kafka (Topic: `order-events`, `payment-events`)
- **Database**: Oracle SQL (Flyway managed)
- **Security**: Spring Security, JJWT, AES-256 Encryption

## 🚀 Deployment (Local)
1. Ensure **Kafka** and **Oracle DB** are running.
2. Set `spring.profiles.active=local` for both services.
3. Run `OrderServiceApplication` and `PaymentProcessorApplication`.
4. Access UI at `http://localhost:8000`.

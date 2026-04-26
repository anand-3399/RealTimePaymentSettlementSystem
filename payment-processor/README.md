# Payment Processor Service

The **Payment Processor** is the central orchestration service in the RTPS system. It consumes order events from Kafka, executes bank transfers via AJBank, and guarantees that no payment is ever silently lost — even in the face of infrastructure failures.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ORDER SERVICE (8081)                        │
│  Creates order → saves to DB → writes to outbox_events → Kafka     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                    topic: order-events
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   PAYMENT PROCESSOR (8082)                          │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ OrderEventConsumer                                           │   │
│  │  - Receives OrderCreatedEvent from Kafka                     │   │
│  │  - Calls PaymentService.processPayment(...)                  │   │
│  │  - On failure: re-throws → triggers KafkaConsumerConfig      │   │
│  └──────────────────────┬───────────────────────────────────────┘   │
│                         │ failure                                   │
│  ┌──────────────────────▼───────────────────────────────────────┐   │
│  │ KafkaConsumerConfig (DefaultErrorHandler)                     │   │
│  │  - Retry 1 → wait 5s → Retry 2 → wait 5s → Retry 3          │   │
│  │  - All retries exhausted → DeadLetterPublishingRecoverer      │   │
│  │  - Publishes to: order-events.DLT                            │   │
│  └──────────────────────┬───────────────────────────────────────┘   │
│                         │                                           │
│               topic: order-events.DLT                              │
│                         │                                           │
│  ┌──────────────────────▼───────────────────────────────────────┐   │
│  │ OrderEventDltConsumer                                         │   │
│  │  - Saves payment as status=PENDING_RETRY in DB               │   │
│  │  - Ensures no message is permanently lost                    │   │
│  └──────────────────────┬───────────────────────────────────────┘   │
│                         │                                           │
│  ┌──────────────────────▼───────────────────────────────────────┐   │
│  │ PaymentRetryScheduler (runs every 60s)                        │   │
│  │  - Polls DB for PENDING_RETRY payments                       │   │
│  │  - Respects nextRetryAt and maxRetries limits                 │   │
│  │  - Re-invokes paymentService.retryPaymentExecution(payment)  │   │
│  └──────────────────────┬───────────────────────────────────────┘   │
│                         │                                           │
│  ┌──────────────────────▼───────────────────────────────────────┐   │
│  │ PaymentService.processPayment(...)                            │   │
│  │  - Idempotency check: skips if payment already exists        │   │
│  │  - Saves payment record with status=PENDING                  │   │
│  │  - Calls AJBankClient.transferMoney(...)                     │   │
│  │  - Updates status: COMPLETED / PENDING_RETRY / FAILED        │   │
│  │  - Publishes PaymentProcessedEvent back to Kafka             │   │
│  └──────────────────────┬───────────────────────────────────────┘   │
│                         │                                           │
└─────────────────────────┼───────────────────────────────────────────┘
                          │
                 HTTPS + X-Internal-Secret
                 (rtps-internal-secret-2026-PPTAJ)
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       AJBANK SERVICE (8083)                         │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ AJBankClient (Resilience4j)                                   │   │
│  │  - @Retry: 3 attempts, exponential backoff (2s → 4s → 8s)   │   │
│  │  - @CircuitBreaker: opens after failures, 60s cool-down      │   │
│  │  - Fallback: returns PENDING_RETRY on AJBank unavailability  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ TransferService                                               │   │
│  │  - ACID transaction: SELECT FOR UPDATE on both accounts      │   │
│  │  - Validates sender balance before debit                     │   │
│  │  - Debit sender → Credit receiver (all-or-nothing)           │   │
│  │  - Idempotency check: rejects duplicate idempotencyKey       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Failure Recovery Matrix

| Failure Point | Behaviour | Recovery |
|---|---|---|
| Kafka down during publish (Order Service) | Event stays in `outbox_events` as `PENDING` | `OutboxPublisher` retries every 10s |
| Payment Processor fails to process Kafka message | Exception re-thrown; `DefaultErrorHandler` retries 3×, 5s apart | After 3 failures → published to `order-events.DLT` |
| All Kafka retries exhausted (DLT) | `OrderEventDltConsumer` saves payment as `PENDING_RETRY` | `PaymentRetryScheduler` retries every 60s |
| AJBank unavailable during transfer | Resilience4j retries 3× with exponential backoff | Fallback sets status to `PENDING_RETRY`; scheduler recovers |
| AJBank down for extended period | Circuit Breaker opens; fast-fail prevents resource waste | Circuit Breaker re-probes after 60s cool-down period |
| Duplicate request sent | Idempotency key checked before processing in both services | Duplicate silently rejected; original result returned |

---

## Security

Service-to-service authentication uses **separate shared secrets per hop** — if one secret is compromised, the other hop remains secure.

| Hop | Header | Secret (local default) |
|---|---|---|
| Order Service → Payment Processor | `X-Internal-Secret` | `rtps-internal-secret-2026-OTP` |
| Payment Processor → AJBank | `X-Internal-Secret` | `rtps-internal-secret-2026-PPTAJ` |

Secrets are injected via environment variables:
- `ORDER_TO_PROCESSOR_SECRET`
- `PROCESSOR_TO_AJBANK_SECRET`

---

## Key Components

| Class | Role |
|---|---|
| `OrderEventConsumer` | Kafka listener for `order-events`; delegates to `PaymentService` |
| `PaymentConsumer` | Kafka listener for `payment-initiated` (alternative entry point) |
| `OrderEventDltConsumer` | Kafka listener for `order-events.DLT`; persists failed events as `PENDING_RETRY` |
| `KafkaConsumerConfig` | Configures retry policy (3×, 5s) and Dead Letter Topic routing |
| `PaymentService` | Core business logic: idempotency, payment persistence, AJBank orchestration |
| `AJBankClient` | HTTP client to AJBank with Resilience4j `@Retry` and `@CircuitBreaker` |
| `PaymentRetryScheduler` | Background job (every 60s); recovers `PENDING_RETRY` payments from DB |
| `PaymentRetryScheduler` | Background job (every 60s); recovers `PENDING_RETRY` payments from DB |
| `InternalPaymentController` | Internal endpoint for Order Service to query payment status by order ID |
| `PaymentController` | Public endpoints for payment details, listing, analytics, and webhook |

---

## Running Locally

Ensure the following services are running before starting:
- Oracle DB (port `1521`, SID: `XEPDB1`)
- Apache Kafka (port `9092`)
- AJBank Service (port `8083`)

```bash
# From the payment-processor directory
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The service starts on **port 8082**.

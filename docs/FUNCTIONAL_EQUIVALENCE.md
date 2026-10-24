# Functional equivalence: monolith vs microservices

This document compares the microservices implementation with the monolith in this repository. Source-level observations and runtime verification results are distinguished below. Verification results reflect the local setup and the cases tested.

## Verified behavior

- `GET /products` and `GET /products/{id}` return equivalent normalized content for the tested data. A missing product ID produces the same status in both implementations.
- Checkout accepts `userId` and `items[{productId, quantity}]`.
- Checkout calculates the total using catalog prices multiplied by quantities and sets status to `PAID`.
- A missing product during checkout produces the same error status in both implementations, with no order persisted in the tested case.
- Adding to and retrieving cart items works equivalently for the tested requests.
- Both implementations expose actuator health endpoints.
- `GET /orders/user/{userId}` returns the user's orders, including an empty list when no orders exist.

## Intentional or known differences

| Area | Monolith | Microservices | Explanation |
|---|---|---|---|
| Cart owner | `userId` | `sessionId` | Existing microservices contract uses a different name. |
| Cart `price` | No field | Optional, unused, nullable field | The microservices entity retains this field. |
| Order fields | `totalAmount`, `createdAt` (`LocalDateTime`) | `totalPrice`, `orderDate` (`Instant`) | Existing response/entity contract differs. |
| Invalid order requests | Tested invalid requests can return HTTP 200 | Tested invalid requests return HTTP 500 | Validation behavior differs; see verification notes. |
| `GET /orders/{id}` | Unfinished stub returning `null` | Returns the order or an error for a missing ID | The unfinished stub was not reproduced. |
| `GET /orders` | Absent | Present | Extra microservices endpoint; exclude it from equivalent-workload comparisons. |
| `DELETE /cart/{sessionId}` | No corresponding HTTP endpoint | Present | Used by the order service during checkout. |
| `GET /orders/user/{userId}` | Present | Added in this change set | Returns all matching orders without pagination. |
| Checkout atomicity | Order save and cart clearing run in one database transaction | Order save and cart clearing are separate operations over HTTP | There is no distributed transaction. |
| Framework | Spring Boot 4.1.0 | Spring Boot 4.1.0 | Same framework version in the current configuration. |

## Checkout atomicity limitation

The monolith performs checkout within a single database transaction, allowing order creation and cart clearing to roll back together when the transaction fails.

In the microservices implementation, the order is saved first and the cart is cleared through a separate HTTP request. If cart clearing fails or times out after the order has been saved, the request can fail even though the order persists and the cart remains uncleared. Retrying may create a duplicate order.

This is a known limitation. The current implementation does not use a saga, outbox, or idempotency key to mitigate this failure mode.

## Verification results

1. `mvn test` succeeded for all three microservices.
2. The order service had 7 passing tests.
3. The cart and catalog services had no dedicated tests in this change set.
4. The read-only equivalence run passed all 11 checks.
5. The write-mode equivalence run passed its checks for cart addition, checkout, matching normalized order totals, cart clearing, and order history.
6. For the three tested invalid order requests, the monolith returned HTTP 200 while the microservices implementation returned HTTP 500.

These results cover the tested cases; they do not prove complete equivalence for every possible request or failure condition.

## Benchmark script change

The current `microservices-load.js` cart payload no longer sends `price: 999.99`. Its order payload uses `userId` and `items`, matching the monolith checkout request shape. The write-mode equivalence test passed the check that adds a cart item without a price.

Benchmark Run 1 used an earlier microservices payload that differed from the monolith request payloads. Keep those measurements as preliminary historical results, not as a final apples-to-apples comparison.

Collect fresh measurements using the reviewed scripts after confirming equivalent operations, starting data, database state, and test conditions.

## Remaining work before a defensible benchmark

- Confirm equivalent starting data.
- Reset or recreate scratch database state between runs where necessary.
- Confirm equivalent workloads, request payloads, virtual users, and test durations.
- Repeat benchmark runs.
- Capture CPU and memory utilization alongside latency, throughput, and error rates.

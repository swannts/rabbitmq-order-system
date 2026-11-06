# Order Service (Spring Boot)

Java 21 / Spring Boot 4.1 starter. It stores orders in PostgreSQL and demonstrates two messaging paths:

- RabbitMQ queues carry `payments.authorize` and `inventory.reserve` commands.
- Kafka topic `order-events` carries the `OrderCreatedEvent` event for downstream consumers.

Run locally after starting PostgreSQL, RabbitMQ, and Kafka:

```bash
./mvnw spring-boot:run
```

Create an order with `POST /api/v1/orders` and an `Idempotency-Key` header:

```json
{"customerName":"Ada","itemName":"Keyboard","quantity":1}
```

Read an order at `GET /api/v1/orders/{orderId}` and list orders at `GET /api/v1/orders?page=0&size=20`. Requests are validated and errors use RFC 9457 problem details. Repeating a request with the same idempotency key and body returns the same order; reusing that key with a different body returns `409 Conflict`.

Order creation writes its messages to PostgreSQL's outbox in the same transaction as the order. A scheduled publisher sends payment and inventory commands to RabbitMQ and publishes the `order.created` event to Kafka. Delivery is at least once, so message consumers must be idempotent. Actuator health is available at `/actuator/health`.

# Order Service (Spring Boot)

Java 21 / Spring Boot 4.1 starter. It stores orders in PostgreSQL and demonstrates two messaging paths:

- RabbitMQ queues carry `payments.authorize` and `inventory.reserve` commands.
- Kafka topic `order-events` carries the `OrderCreatedEvent` event for downstream consumers.

Run locally after starting PostgreSQL, RabbitMQ, and Kafka:

```bash
./mvnw spring-boot:run
```

Create an order with `POST /api/orders`:

```json
{"customerName":"Ada","itemName":"Keyboard","quantity":1}
```

Actuator health is available at `/actuator/health`. This starter publishes directly after saving the order; add a transactional outbox before treating the database write and messages as reliable atomic work.

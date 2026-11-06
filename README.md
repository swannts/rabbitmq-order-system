# Order Platform Microservices

Learning project for microservices, RabbitMQ, and Kafka using four different service stacks. The repository currently contains the folder skeleton; app implementations and build manifests will be added service by service.

## Service and technology map

| Service | Stack | Responsibility | Messaging role |
|---|---|---|---|
| `order-service-spring-boot` | Java / Spring Boot / PostgreSQL | Accept orders and manage order state | Publishes work to RabbitMQ and business events to Kafka |
| `payment-service-laravel` | PHP / Laravel / MySQL | Authorize or reject payments | RabbitMQ worker |
| `inventory-service-nestjs` | TypeScript / NestJS / MongoDB | Reserve or release stock | RabbitMQ worker |
| `order-history-service-go` | Go / ClickHouse | Build a queryable event history and analytics view | Kafka consumer |

Each service owns its data. Services must not read or write another service's tables directly. `contracts/` is for versioned API and event schemas, not shared business logic.

The services deliberately use different data stores: PostgreSQL for transactional orders, MySQL for payments, MongoDB for inventory documents, and ClickHouse for the Kafka-fed history/analytics read model. ClickHouse is for analytical/event-history queries here; it is not the source of truth for orders or payments.

## Layout

```text
services/
  order-service-spring-boot/       # Maven-style src/main and src/test
  payment-service-laravel/         # Laravel app, routes, database, tests
  inventory-service-nestjs/        # NestJS src modules and e2e tests
  order-history-service-go/        # Go cmd and internal packages
infrastructure/
  docker/
  rabbitmq/
  kafka/
contracts/
  openapi/
  asyncapi/
docs/architecture/
```

The initial workflow will use RabbitMQ for payment and inventory commands. Kafka will carry business events such as `OrderPlaced`, `PaymentAuthorized`, and `InventoryReserved` to history and analytics consumers.

# Order Platform Microservices

Four independently buildable service starters for learning microservices, RabbitMQ, and Kafka with different languages and frameworks.

| Service | Stack | Owns | Messaging role |
|---|---|---|---|
| `order-service-spring-boot` | Java 21 / Spring Boot 4.1 | Orders in PostgreSQL | Sends payment and inventory commands through RabbitMQ; publishes `OrderCreated` to Kafka |
| `payment-service-laravel` | PHP 8.4 / Laravel 13 | Payments in MySQL | Consumes `payments.commands` from RabbitMQ |
| `inventory-service-nestjs` | Node.js 24 / NestJS 12 | Reservation requests in MongoDB | Consumes `inventory.commands` from RabbitMQ |
| `order-history-service-go` | Go 1.27 | Event history in ClickHouse | Consumes `order-events` from Kafka |

Each service owns its data. Do not query or update another service's database directly. ClickHouse is the analytical history projection, not the source of truth for orders or payments.

## Start local dependencies

RabbitMQ, Kafka, and the four separate databases are configured in separate Compose files. Start them together:

```bash
docker compose -p order-platform-local \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml up -d
```

RabbitMQ Management is at <http://localhost:15672> (`learner` / `learner`). Kafka is at `localhost:29092`. Database ports and local credentials are in [infrastructure/docker/README.md](infrastructure/docker/README.md).

## Start the application containers

The starter Dockerfiles and service Compose file build each stack separately:

```bash
docker compose -p order-platform-local --profile apps \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml \
  -f infrastructure/docker/compose.services.yaml up --build -d
```

The order API accepts `POST /api/orders` on port `8080`; the inventory and history services expose health endpoints on ports `3000` and `8081`. The payment container runs a RabbitMQ worker.

## Current starter behavior

The Order service stores an order, sends commands to both worker queues, and publishes an event to Kafka. The Payment worker logs and acknowledges the command without charging a payment method. Inventory records a reservation request but does not yet check stock. The Go consumer writes Kafka events into ClickHouse and commits their offsets after successful inserts.

These starters are for learning. The Order service does not yet use a transactional outbox, and the worker workflows do not yet implement retries, dead-letter queues, compensation, or business-level failure events. Add those as follow-on exercises.

## Production template

The production Compose file deploys built service images and connects to managed PostgreSQL, MySQL, MongoDB, ClickHouse, RabbitMQ, and Kafka endpoints. See [infrastructure/docker/README.md](infrastructure/docker/README.md) and `.env.production.example`. It is a single-host reference template, not a high-availability production deployment.

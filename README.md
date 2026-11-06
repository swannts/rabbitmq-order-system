# Order Platform Microservices

Four independently buildable service starters for learning microservices, RabbitMQ, and Kafka with different languages and frameworks.

| Service | Stack | Owns | Messaging role |
|---|---|---|---|
| `order-service-spring-boot` | Java 21 / Spring Boot 4.1 | Orders in PostgreSQL | Sends payment and inventory commands through RabbitMQ; publishes `OrderCreated` to Kafka |
| `payment-service-laravel` | PHP 8.4 / Laravel 13 | Payments in MySQL | Consumes `payments.commands.v1` from RabbitMQ |
| `inventory-service-nestjs` | Node.js 24 / NestJS 12 | Stock and reservations in MongoDB | Consumes `inventory.commands.v1` from RabbitMQ |
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

Start the local monitoring stack in a separate Compose layer:

```bash
docker compose -p order-platform-local --profile apps \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml \
  -f infrastructure/docker/compose.services.yaml \
  -f infrastructure/docker/compose.monitoring.yaml up --build -d
```

Grafana is at <http://localhost:3001> (`admin` / `admin` by default; set `GRAFANA_ADMIN_PASSWORD` to change it). Prometheus is at <http://localhost:9090>, Alertmanager at <http://localhost:9093>, and the local alert email inbox at <http://localhost:8025>. Prometheus scrapes the application metrics and RabbitMQ, probes service readiness, and collects container resource usage through cAdvisor. Alerts cover failed scrape/health checks and containers nearing memory limits.

The Orders API provides `POST /api/v1/orders`, `GET /api/v1/orders/{orderId}`, and paginated `GET /api/v1/orders` on port `8080`. Creating an order requires an `Idempotency-Key` header. Payment APIs are at port `8002`, Inventory APIs at `3000`, and Order History APIs at `8081`. The payment worker runs separately from its API. API contracts are in [contracts/openapi](contracts/openapi/). Prometheus metrics are exposed at `/actuator/prometheus` by Order and `/metrics` by Inventory and Order History. Payment worker liveness is observed through RabbitMQ and container health/resource metrics.

## Current starter behavior

The Order service stores an order and its outbox messages in one PostgreSQL transaction; a background publisher sends commands to RabbitMQ and lifecycle events to Kafka. Payment authorization is simulated and persisted in MySQL without charging a payment method. Inventory stock is managed through its API and reservations atomically decrement available quantity in MongoDB. Worker outcomes update the order to `CONFIRMED` or `REJECTED`. Order History stores Kafka events in ClickHouse and exposes read-only query endpoints.

These starters are for learning. The Order service does not yet use a transactional outbox, and the worker workflows do not yet implement retries, dead-letter queues, compensation, or business-level failure events. Add those as follow-on exercises.

## Production template

The production Compose file deploys built service images and connects to managed PostgreSQL, MySQL, MongoDB, ClickHouse, RabbitMQ, and Kafka endpoints. See [infrastructure/docker/README.md](infrastructure/docker/README.md) and `.env.production.example`. It is a single-host reference template, not a high-availability production deployment.

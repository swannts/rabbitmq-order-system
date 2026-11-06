# Docker environments

## Local development

The local setup is split by concern. Start brokers and databases together:

```bash
docker compose -p order-platform-local \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml up -d
```

RabbitMQ Management: <http://localhost:15672> (`learner` / `learner`). Kafka is available to host applications at `localhost:29092` and to containers on the Compose network at `kafka:9092`. The Compose setup creates `order-events` and `payment-events` topics. Each service has a database that fits its data and stack:

| Service | Host port | Database / user | Local password |
|---|---:|---|---|
| Order | 5433 | PostgreSQL: `orders` / `orders` | `orders_dev` |
| Payment | 3306 | MySQL: `payments` / `payments` | `payments_dev` |
| Inventory | 27017 | MongoDB: `inventory` (admin user `inventory_admin`) | `inventory_dev` |
| Order history | 8123 (HTTP), 9000 (native) | ClickHouse: `order_history` / `history_app` | `history_dev` |

Local credentials are for development only.

Containers on the Compose network connect using service DNS names: Order → `order-db:5432`, Payment → `payment-db:3306`, Inventory → `inventory-db:27017`, and History → `history-db:8123` (HTTP) or `history-db:9000` (native protocol). The brokers are `rabbitmq:5672` and `kafka:9092` from other containers. Use `localhost` ports only from applications running directly on your host.

The app containers have their own `compose.services.yaml`, with one Dockerfile per service. Start the full local stack with:

```bash
docker compose -p order-platform-local --profile apps \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml \
  -f infrastructure/docker/compose.services.yaml up --build -d
```

The monitoring stack is a separate Compose layer. Include it when starting the full stack or bring it up independently with the same project name and Compose network:

```bash
docker compose -p order-platform-local --profile apps \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml \
  -f infrastructure/docker/compose.services.yaml \
  -f infrastructure/docker/compose.monitoring.yaml up --build -d
```

Grafana is exposed at `localhost:3001`, Prometheus at `localhost:9090`, Alertmanager at `localhost:9093`, and the local Mailpit alert inbox at `localhost:8025`. Set `GRAFANA_ADMIN_PASSWORD` before startup to replace the local default. Prometheus collects application, RabbitMQ, Kafka, PostgreSQL, MySQL, MongoDB, ClickHouse, and container metrics. The provisioned Grafana dashboard shows service availability, request rates, RabbitMQ backlog, Kafka consumer lag, order-history processing, database target health, and container memory. Alertmanager sends local email notifications to Mailpit. The Laravel worker has no standalone metrics endpoint yet; its process/container state and RabbitMQ broker metrics are collected.

Stop only brokers and databases while preserving local data:

```bash
docker compose -p order-platform-local \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml down
```

Remove local database and broker volumes only when you want to reset the learning environment:

```bash
docker compose -p order-platform-local \
  -f infrastructure/docker/compose.messaging.yaml \
  -f infrastructure/docker/compose.databases.yaml down -v
```

## Production image deployment template

Build and publish a versioned image for each completed service, then copy `.env.production.example` to `.env.production`, replace every example value, and inject real credentials from your deployment secret manager. The example file is only a variable checklist. Compose environment variables are visible to Docker administrators, so do not treat a committed or broadly readable env file as secure secret storage.

```bash
docker compose --env-file infrastructure/docker/.env.production \
  -f infrastructure/docker/compose.production.yaml up -d
```

The production Compose file runs application images only. It expects managed PostgreSQL for orders, MySQL for payments, MongoDB for inventory, ClickHouse for history, RabbitMQ over TLS, and Kafka using SASL/TLS. It deliberately does not run single-node brokers or databases as production infrastructure. Put a TLS reverse proxy or platform ingress in front of the order service; its host port is bound to loopback.

This is a single-host deployment template, not a highly available production platform. For production availability, use an orchestrator or managed container platform, multiple application replicas, managed databases/brokers, secret injection, image scanning/signing, and centralized health/metrics/logging. Application Dockerfiles are starter versions; review runtime and security settings before production use.

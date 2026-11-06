# Order Platform Microservices

Learning project for Spring Boot microservices, RabbitMQ, and Kafka. This commit establishes the repository layout; service implementations will be added in stages.

## Services

- `order-service` owns order creation and status.
- `payment-service` owns payment processing.
- `inventory-service` owns stock reservations.
- `order-history-service` consumes Kafka events to build a read-only order timeline.

Each service is intended to be an independently buildable and deployable Spring Boot application with its own data boundary. Do not share database tables between services. Keep business rules in `domain`, use cases in `application`, transport adapters in `api`, and broker/database integrations in `infrastructure`.

## Repository map

- `services/` — independently deployable applications.
- `infrastructure/` — local Docker and broker configuration.
- `contracts/` — versioned HTTP and event contracts; do not put shared business logic here.
- `docs/architecture/` — diagrams and design decisions.

Planned message roles: RabbitMQ carries payment and inventory work commands; Kafka carries business events for history and analytics.

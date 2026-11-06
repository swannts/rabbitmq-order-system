# Inventory Service (NestJS)

NestJS 12 / TypeScript inventory service. It serves `GET /healthz` on port 3000, consumes reservation commands from `inventory.commands.v1`, checks and decrements available stock atomically in MongoDB, and publishes success or rejection outcomes to `orders.outcomes.v1`. Reservation processing is idempotent by order ID.

Set or restock quantity with `PUT /api/v1/inventory/items/{itemName}` and `{"available": 25}`. Read stock with `GET /api/v1/inventory/items/{itemName}` and reservation state with `GET /api/v1/inventory/reservations/{orderId}`.

The starter records requests but does not yet validate stock availability or publish a reservation result.

Install dependencies and run locally with MongoDB and RabbitMQ available:

```bash
npm ci
cp .env.example .env
npm run start:dev
```

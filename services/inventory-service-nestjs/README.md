# Inventory Service (NestJS)

NestJS 12 / TypeScript starter. It serves `GET /healthz` on port 3000 and consumes `inventory.reserve` events from the durable RabbitMQ queue `inventory.commands`. It records the received reservation request in its own MongoDB database, using the order ID as an idempotency key.

The starter records requests but does not yet validate stock availability or publish a reservation result.

Install dependencies and run locally with MongoDB and RabbitMQ available:

```bash
npm ci
cp .env.example .env
npm run start:dev
```

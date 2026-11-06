# Order History Service (Go)

Go 1.27 service that consumes the Kafka `order-events` topic, writes idempotent lifecycle event records into its own ClickHouse database, and exposes `/healthz` and `/readyz` on port 8081.

Query an order's event list at `GET /api/v1/order-history/{orderId}`. Search with optional `orderId`, `from`, and `until` RFC3339 filters and bounded `limit`/`offset` pagination at `GET /api/v1/order-history`. Malformed events are sent to `order-events.dlq` before their source offset is committed.

The consumer commits each Kafka offset after the corresponding ClickHouse insert succeeds. The table is a read model for event history and analytics, not the source of truth for orders.

Run it in the local Compose network after the broker and database containers are up:

```bash
go run ./cmd/order-history
```

Set `KAFKA_BROKERS=kafka:9092`, `CLICKHOUSE_URL=http://history-db:8123/order_history`, `CLICKHOUSE_USER=history_app`, and `CLICKHOUSE_PASSWORD=history_dev` when running as a container.

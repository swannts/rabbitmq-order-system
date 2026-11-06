# Order History Service (Go)

Go 1.27 starter that consumes the Kafka `order-events` topic, writes event records into its own ClickHouse database, and exposes `/healthz` and `/readyz` on port 8081.

The consumer commits each Kafka offset after the corresponding ClickHouse insert succeeds. The table is a read model for event history and analytics, not the source of truth for orders.

Run it in the local Compose network after the broker and database containers are up:

```bash
go run ./cmd/order-history
```

Set `KAFKA_BROKERS=kafka:9092`, `CLICKHOUSE_URL=http://history-db:8123/order_history`, `CLICKHOUSE_USER=history_app`, and `CLICKHOUSE_PASSWORD=history_dev` when running as a container.

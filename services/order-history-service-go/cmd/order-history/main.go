package main

import (
	"context"
	"crypto/tls"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	clickhouse "github.com/ClickHouse/clickhouse-go/v2"
	"github.com/google/uuid"
	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/pkg/sasl/plain"
)

type OrderCreatedEvent struct {
	EventType     string    `json:"eventType"`
	SchemaVersion uint16    `json:"schemaVersion"`
	EventID       string    `json:"eventId"`
	OrderID       string    `json:"orderId"`
	CustomerName  string    `json:"customerName"`
	ItemName      string    `json:"itemName"`
	Quantity      uint32    `json:"quantity"`
	Status        string    `json:"status"`
	OccurredAt    time.Time `json:"occurredAt"`
	CreatedAt     time.Time `json:"createdAt"`
}

var httpRequests atomic.Uint64
var orderEventsProcessed atomic.Uint64
var orderEventFailures atomic.Uint64

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	db, err := openClickHouse(ctx)
	if err != nil {
		logger.Error("connect to ClickHouse", "error", err)
		os.Exit(1)
	}
	defer db.Close()

	if err := createSchema(ctx, db); err != nil {
		logger.Error("create ClickHouse schema", "error", err)
		os.Exit(1)
	}

	consumer, err := newKafkaConsumer()
	if err != nil {
		logger.Error("create Kafka consumer", "error", err)
		os.Exit(1)
	}
	defer consumer.Close()

	go consumeOrders(ctx, logger, consumer, db)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{"service": "order-history-service", "status": "ok"})
	})
	mux.HandleFunc("GET /readyz", func(w http.ResponseWriter, r *http.Request) {
		checkCtx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()
		if err := db.PingContext(checkCtx); err != nil {
			http.Error(w, "ClickHouse unavailable", http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})
	mux.HandleFunc("GET /api/v1/order-history", func(w http.ResponseWriter, r *http.Request) {
		listOrderHistory(w, r, db)
	})
	mux.HandleFunc("GET /api/v1/order-history/{orderId}", func(w http.ResponseWriter, r *http.Request) {
		getOrderHistory(w, r, db)
	})
	mux.HandleFunc("GET /metrics", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
		_, _ = fmt.Fprintf(w, "# HELP order_history_http_requests_total Total HTTP requests handled.\n# TYPE order_history_http_requests_total counter\norder_history_http_requests_total %d\n# HELP order_history_events_processed_total Kafka order events stored in ClickHouse.\n# TYPE order_history_events_processed_total counter\norder_history_events_processed_total %d\n# HELP order_history_event_failures_total Kafka order events that failed processing.\n# TYPE order_history_event_failures_total counter\norder_history_event_failures_total %d\n", httpRequests.Load(), orderEventsProcessed.Load(), orderEventFailures.Load())
	})

	server := &http.Server{Addr: ":8081", Handler: countRequests(mux), ReadHeaderTimeout: 5 * time.Second}
	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdownCtx)
	}()

	logger.Info("order history service started", "address", server.Addr)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Error("HTTP server stopped", "error", err)
		os.Exit(1)
	}
}

func openClickHouse(ctx context.Context) (*sql.DB, error) {
	endpoint := env("CLICKHOUSE_URL", "http://localhost:8123/order_history")
	u, err := url.Parse(endpoint)
	if err != nil {
		return nil, err
	}
	database := strings.TrimPrefix(u.Path, "/")
	if database == "" {
		database = "order_history"
	}
	protocol := clickhouse.HTTP
	options := &clickhouse.Options{
		Addr:     []string{u.Host},
		Protocol: protocol,
		Auth: clickhouse.Auth{
			Database: database,
			Username: env("CLICKHOUSE_USER", "history_app"),
			Password: env("CLICKHOUSE_PASSWORD", "history_dev"),
		},
	}
	if u.Scheme == "https" {
		options.TLS = &tls.Config{MinVersion: tls.VersionTLS12}
	}
	db := clickhouse.OpenDB(options)
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	return db, nil
}

func createSchema(ctx context.Context, db *sql.DB) error {
	_, err := db.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS order_events_v2 (
		event_id UUID,
		event_type LowCardinality(String),
		schema_version UInt16,
		order_id UUID,
		customer_name String,
		item_name String,
		quantity UInt32,
		status LowCardinality(String),
		occurred_at DateTime64(3, 'UTC')
	) ENGINE = ReplacingMergeTree ORDER BY (order_id, occurred_at, event_id)`)
	return err
}

func newKafkaConsumer() (*kgo.Client, error) {
	brokers := strings.Split(env("KAFKA_BROKERS", "localhost:29092"), ",")
	options := []kgo.Opt{
		kgo.SeedBrokers(brokers...),
		kgo.ConsumerGroup(env("KAFKA_GROUP_ID", "order-history-service")),
		kgo.ConsumeTopics(env("KAFKA_ORDER_EVENTS_TOPIC", "order-events")),
	}
	if strings.EqualFold(os.Getenv("KAFKA_SECURITY_PROTOCOL"), "SASL_SSL") {
		options = append(options,
			kgo.DialTLSConfig(&tls.Config{MinVersion: tls.VersionTLS12}),
			kgo.SASL(plain.Auth{
				User: env("KAFKA_SASL_USERNAME", ""),
				Pass: env("KAFKA_SASL_PASSWORD", ""),
			}.AsMechanism()),
		)
	}
	return kgo.NewClient(options...)
}

func consumeOrders(ctx context.Context, logger *slog.Logger, consumer *kgo.Client, db *sql.DB) {
	for ctx.Err() == nil {
		fetches := consumer.PollFetches(ctx)
		fetches.EachError(func(topic string, partition int32, err error) {
			orderEventFailures.Add(1)
			logger.Error("Kafka fetch failed", "topic", topic, "partition", partition, "error", err)
		})
		fetches.EachRecord(func(record *kgo.Record) {
			var event OrderCreatedEvent
			if err := json.Unmarshal(record.Value, &event); err != nil {
				orderEventFailures.Add(1)
				logger.Error("invalid order event", "error", err, "offset", record.Offset)
				deadLetter := &kgo.Record{Topic: "order-events.dlq", Key: record.Key, Value: record.Value, Headers: append(record.Headers, kgo.RecordHeader{Key: "source-topic", Value: []byte(record.Topic)})}
				if produceErr := consumer.ProduceSync(ctx, deadLetter).FirstErr(); produceErr != nil {
					logger.Error("dead-letter invalid Kafka event", "error", produceErr, "offset", record.Offset)
					return
				}
				if commitErr := consumer.CommitRecords(ctx, record); commitErr != nil {
					logger.Error("commit dead-lettered Kafka offset", "error", commitErr, "offset", record.Offset)
				}
				return
			}
			if event.EventID == "" {
				event.EventID = uuid.NewSHA1(uuid.NameSpaceOID, []byte(fmt.Sprintf("%s:%d:%d", record.Topic, record.Partition, record.Offset))).String()
			}
			if event.EventType == "" {
				event.EventType = "order.created"
			}
			if event.SchemaVersion == 0 {
				event.SchemaVersion = 1
			}
			if event.OccurredAt.IsZero() {
				event.OccurredAt = event.CreatedAt
			}
			if event.Status == "" {
				event.Status = "PENDING"
			}
			if event.OrderID == "" || event.OccurredAt.IsZero() {
				orderEventFailures.Add(1)
				logger.Error("invalid order event fields", "offset", record.Offset)
				if err := deadLetterRecord(ctx, consumer, record); err != nil {
					logger.Error("dead-letter invalid event fields", "error", err, "offset", record.Offset)
					return
				}
				if err := consumer.CommitRecords(ctx, record); err != nil {
					logger.Error("commit dead-lettered offset", "error", err, "offset", record.Offset)
				}
				return
			}
			if _, err := db.ExecContext(ctx,
				"INSERT INTO order_events_v2 (event_id, event_type, schema_version, order_id, customer_name, item_name, quantity, status, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				event.EventID, event.EventType, event.SchemaVersion, event.OrderID, event.CustomerName, event.ItemName, event.Quantity, event.Status, event.OccurredAt,
			); err != nil {
				orderEventFailures.Add(1)
				logger.Error("persist order event", "error", err, "order_id", event.OrderID)
				return
			}
			if err := consumer.CommitRecords(ctx, record); err != nil {
				orderEventFailures.Add(1)
				logger.Error("commit Kafka offset", "error", err, "offset", record.Offset)
				return
			}
			logger.Info("stored order event", "order_id", event.OrderID, "offset", record.Offset)
			orderEventsProcessed.Add(1)
		})
	}
}

func deadLetterRecord(ctx context.Context, producer *kgo.Client, source *kgo.Record) error {
	deadLetter := &kgo.Record{Topic: "order-events.dlq", Key: source.Key, Value: source.Value,
		Headers: append(source.Headers, kgo.RecordHeader{Key: "source-topic", Value: []byte(source.Topic)})}
	return producer.ProduceSync(ctx, deadLetter).FirstErr()
}

type historyEvent struct {
	EventID       string    `json:"eventId"`
	EventType     string    `json:"eventType"`
	SchemaVersion uint16    `json:"schemaVersion"`
	OrderID       string    `json:"orderId"`
	CustomerName  string    `json:"customerName"`
	ItemName      string    `json:"itemName"`
	Quantity      uint32    `json:"quantity"`
	Status        string    `json:"status"`
	OccurredAt    time.Time `json:"occurredAt"`
}

func getOrderHistory(w http.ResponseWriter, r *http.Request, db *sql.DB) {
	orderID := r.PathValue("orderId")
	if _, err := uuid.Parse(orderID); err != nil {
		writeAPIError(w, http.StatusBadRequest, "orderId must be a UUID")
		return
	}
	items, err := queryHistory(r.Context(), db, orderID, "", "", 100, 0)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "Could not read order history")
		return
	}
	if len(items) == 0 {
		writeAPIError(w, http.StatusNotFound, "Order history not found")
		return
	}
	writeJSON(w, http.StatusOK, items)
}

func listOrderHistory(w http.ResponseWriter, r *http.Request, db *sql.DB) {
	query := r.URL.Query()
	orderID := query.Get("orderId")
	if orderID != "" {
		if _, err := uuid.Parse(orderID); err != nil {
			writeAPIError(w, http.StatusBadRequest, "orderId must be a UUID")
			return
		}
	}
	from, until := query.Get("from"), query.Get("until")
	for _, value := range []string{from, until} {
		if value != "" {
			if _, err := time.Parse(time.RFC3339, value); err != nil {
				writeAPIError(w, http.StatusBadRequest, "from and until must use RFC3339 timestamps")
				return
			}
		}
	}
	limit := 50
	if value := query.Get("limit"); value != "" {
		parsed, err := strconv.Atoi(value)
		if err != nil || parsed < 1 {
			writeAPIError(w, http.StatusBadRequest, "limit must be between 1 and 100")
			return
		}
		limit = min(parsed, 100)
	}
	offset := 0
	if value := query.Get("offset"); value != "" {
		parsed, err := strconv.Atoi(value)
		if err != nil || parsed < 0 {
			writeAPIError(w, http.StatusBadRequest, "offset must be a non-negative integer")
			return
		}
		offset = parsed
	}
	items, err := queryHistory(r.Context(), db, orderID, from, until, limit, offset)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "Could not read order history")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items, "limit": limit, "offset": offset})
}

func queryHistory(ctx context.Context, db *sql.DB, orderID, from, until string, limit, offset int) ([]historyEvent, error) {
	statement := "SELECT event_id, event_type, schema_version, order_id, customer_name, item_name, quantity, status, occurred_at FROM order_events_v2 FINAL WHERE 1 = 1"
	args := make([]any, 0, 5)
	if orderID != "" {
		statement += " AND order_id = ?"
		args = append(args, orderID)
	}
	if from != "" {
		statement += " AND occurred_at >= ?"
		args = append(args, from)
	}
	if until != "" {
		statement += " AND occurred_at <= ?"
		args = append(args, until)
	}
	statement += " ORDER BY occurred_at ASC LIMIT ? OFFSET ?"
	args = append(args, limit, offset)
	rows, err := db.QueryContext(ctx, statement, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]historyEvent, 0)
	for rows.Next() {
		var item historyEvent
		if err := rows.Scan(&item.EventID, &item.EventType, &item.SchemaVersion, &item.OrderID, &item.CustomerName, &item.ItemName, &item.Quantity, &item.Status, &item.OccurredAt); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeAPIError(w http.ResponseWriter, status int, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{"type": "about:blank", "title": http.StatusText(status), "status": status, "detail": detail})
}

func countRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		httpRequests.Add(1)
		next.ServeHTTP(w, r)
	})
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

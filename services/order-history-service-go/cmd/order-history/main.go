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
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	clickhouse "github.com/ClickHouse/clickhouse-go/v2"
	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/pkg/sasl/plain"
)

type OrderCreatedEvent struct {
	OrderID      string    `json:"orderId"`
	CustomerName string    `json:"customerName"`
	ItemName     string    `json:"itemName"`
	Quantity     uint32    `json:"quantity"`
	CreatedAt    time.Time `json:"createdAt"`
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
	_, err := db.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS order_events (
		order_id UUID,
		customer_name String,
		item_name String,
		quantity UInt32,
		created_at DateTime64(3, 'UTC')
	) ENGINE = MergeTree ORDER BY (order_id, created_at)`)
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
				return
			}
			if _, err := db.ExecContext(ctx,
				"INSERT INTO order_events (order_id, customer_name, item_name, quantity, created_at) VALUES (?, ?, ?, ?, ?)",
				event.OrderID, event.CustomerName, event.ItemName, event.Quantity, event.CreatedAt,
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

# Payment Service (Laravel)

Laravel 13 / PHP 8.4 payment service. It consumes authorization commands from `payments.commands.v1`, stores one payment attempt per order in its MySQL database, and publishes outcomes to `orders.outcomes.v1`. Authorization is simulated; no payment provider is called.

Read a payment by order ID at `GET /api/v1/payments/{orderId}` on <http://localhost:8002>. Compose runs the API and consumer as separate containers and runs migrations once in a dedicated job. In production, run migrations as a deployment step.

The current command logs a received command and acknowledges it; it does not charge a payment method. Malformed messages are rejected. Add a dead-letter exchange before using this behavior beyond local learning.

Run the HTTP app locally after installing dependencies:

```bash
composer install
cp .env.example .env
php artisan key:generate
php artisan payments:consume
```

The consumer command is `php artisan payments:consume`. The health endpoint is `GET /healthz` when serving the Laravel HTTP app. The Compose container starts the long-running RabbitMQ consumer.

# Payment Service (Laravel)

Laravel 13 / PHP 8.4 starter for the payment worker. It consumes JSON commands from `payments.commands` using RabbitMQ and stores its future payment records in its own MySQL database.

The current command logs a received command and acknowledges it; it does not charge a payment method. Malformed messages are rejected. Add a dead-letter exchange before using this behavior beyond local learning.

Run the HTTP app locally after installing dependencies:

```bash
composer install
cp .env.example .env
php artisan key:generate
php artisan payments:consume
```

The consumer command is `php artisan payments:consume`. The health endpoint is `GET /healthz` when serving the Laravel HTTP app. The Compose container starts the long-running RabbitMQ consumer.

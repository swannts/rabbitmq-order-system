FROM composer:2.10.3 AS build
WORKDIR /app
COPY . .
RUN composer install --no-interaction --no-dev --prefer-dist --optimize-autoloader --no-scripts --ignore-platform-req=ext-sockets

FROM php:8.4-cli-bookworm
RUN apt-get update \
    && apt-get install -y --no-install-recommends libonig-dev \
    && docker-php-ext-install pdo_mysql mbstring pcntl sockets \
    && apt-get purge -y --auto-remove libonig-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=www-data:www-data /app /app
RUN php artisan package:discover --ansi
USER www-data
CMD ["php", "artisan", "payments:consume"]

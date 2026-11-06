FROM composer:2.10.3 AS build
WORKDIR /app
COPY . .
RUN composer install --no-interaction --no-dev --prefer-dist --optimize-autoloader --no-scripts

FROM php:8.4-cli-bookworm
RUN docker-php-ext-install pdo_mysql mbstring pcntl
WORKDIR /app
COPY --from=build --chown=www-data:www-data /app /app
RUN php artisan package:discover --ansi
USER www-data
CMD ["php", "artisan", "payments:consume"]

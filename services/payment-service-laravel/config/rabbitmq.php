<?php

return [
    'host' => env('RABBITMQ_HOST', 'localhost'),
    'port' => (int) env('RABBITMQ_PORT', 5672),
    'user' => env('RABBITMQ_USER', 'learner'),
    'password' => env('RABBITMQ_PASSWORD', 'learner'),
    'vhost' => env('RABBITMQ_VHOST', '/'),
    'scheme' => env('RABBITMQ_SCHEME', 'amqp'),
    'queue' => env('PAYMENT_COMMAND_QUEUE', 'payments.commands'),
];

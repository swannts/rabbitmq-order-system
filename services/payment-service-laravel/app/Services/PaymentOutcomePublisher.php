<?php

namespace App\Services;

use PhpAmqpLib\Connection\AMQPSSLConnection;
use PhpAmqpLib\Connection\AMQPStreamConnection;
use PhpAmqpLib\Message\AMQPMessage;

class PaymentOutcomePublisher
{
    public function publish(array $outcome): void
    {
        $config = config('rabbitmq');
        $connection = $config['scheme'] === 'amqps'
            ? new AMQPSSLConnection($config['host'], $config['port'], $config['user'], $config['password'], $config['vhost'], [
                'verify_peer' => true,
                'verify_peer_name' => true,
            ])
            : new AMQPStreamConnection($config['host'], $config['port'], $config['user'], $config['password'], $config['vhost']);
        try {
            $channel = $connection->channel();
            $channel->confirm_select();
            $channel->queue_declare('orders.outcomes.v1', false, true, false, false, false, new \PhpAmqpLib\Wire\AMQPTable([
                'x-dead-letter-exchange' => 'orders.dlx',
                'x-dead-letter-routing-key' => 'orders.dead.v1',
            ]));
            $packet = ['pattern' => 'orders.payment-result', 'data' => $outcome];
            $message = new AMQPMessage(json_encode($packet, JSON_THROW_ON_ERROR), [
                'content_type' => 'application/json',
                'delivery_mode' => AMQPMessage::DELIVERY_MODE_PERSISTENT,
                'message_id' => $outcome['orderId'].':payment:'.$outcome['status'],
            ]);
            $channel->basic_publish($message, '', 'orders.outcomes.v1');
            $channel->wait_for_pending_acks_returns(10);
            $channel->close();
        } finally {
            $connection->close();
        }
    }
}

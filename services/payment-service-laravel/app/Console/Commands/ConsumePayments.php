<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;
use Illuminate\Support\Facades\Log;
use PhpAmqpLib\Connection\AMQPSSLConnection;
use PhpAmqpLib\Connection\AMQPStreamConnection;
use PhpAmqpLib\Message\AMQPMessage;
use Throwable;

class ConsumePayments extends Command
{
    protected $signature = 'payments:consume';

    protected $description = 'Consume payment commands from RabbitMQ';

    public function handle(): int
    {
        $config = config('rabbitmq');
        $connection = $config['scheme'] === 'amqps'
            ? new AMQPSSLConnection($config['host'], $config['port'], $config['user'], $config['password'], $config['vhost'], [
                'verify_peer' => true,
                'verify_peer_name' => true,
            ])
            : new AMQPStreamConnection($config['host'], $config['port'], $config['user'], $config['password'], $config['vhost']);

        $channel = $connection->channel();
        $channel->queue_declare($config['queue'], false, true, false, false);
        $channel->basic_qos(null, 1, null);
        $this->info("Waiting for payment commands on {$config['queue']}...");

        $channel->basic_consume($config['queue'], '', false, false, false, false, function (AMQPMessage $message): void {
            try {
                $packet = json_decode($message->getBody(), true, 512, JSON_THROW_ON_ERROR);
                $command = $packet['data'] ?? $packet;

                // Replace this demo acknowledgement with real payment authorization logic.
                Log::info('Payment command received', [
                    'pattern' => $packet['pattern'] ?? 'payments.authorize',
                    'orderId' => $command['orderId'] ?? null,
                ]);
                $message->ack();
            } catch (Throwable $exception) {
                Log::error('Payment command could not be processed', ['error' => $exception->getMessage()]);
                $message->nack(false, false);
            }
        });

        try {
            while ($channel->is_consuming()) {
                $channel->wait();
            }
        } finally {
            $channel->close();
            $connection->close();
        }

        return self::SUCCESS;
    }
}

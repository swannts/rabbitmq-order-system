import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { connect, type ChannelModel, type ConfirmChannel } from 'amqplib';

export interface InventoryOutcome {
  orderId: string;
  itemName: string;
  quantity: number;
  status: 'RESERVED' | 'REJECTED';
  reason?: string;
  occurredAt: string;
}

@Injectable()
export class RabbitOutcomePublisher implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RabbitOutcomePublisher.name);
  private connection?: ChannelModel;
  private channel?: ConfirmChannel;

  async onModuleInit(): Promise<void> {
    const url = process.env.RABBITMQ_URL ?? 'amqp://learner:learner@localhost:5672';
    const connection = await connect(url);
    const channel = await connection.createConfirmChannel();
    this.connection = connection;
    this.channel = channel;
    await channel.assertQueue('orders.outcomes.v1', {
      durable: true,
      arguments: { 'x-dead-letter-exchange': 'orders.dlx', 'x-dead-letter-routing-key': 'orders.dead.v1' },
    });
  }

  async publishInventoryOutcome(outcome: InventoryOutcome): Promise<void> {
    if (!this.channel) throw new Error('RabbitMQ outcome publisher is not ready');
    const channel = this.channel;
    if (!channel) throw new Error('RabbitMQ outcome publisher is not ready');
    const packet = Buffer.from(JSON.stringify({ pattern: 'orders.inventory-result', data: outcome }));
    channel.sendToQueue('orders.outcomes.v1', packet, {
      persistent: true,
      contentType: 'application/json',
      messageId: `${outcome.orderId}:inventory:${outcome.status}`,
    });
    await channel.waitForConfirms();
    this.logger.log(`Published inventory outcome for order ${outcome.orderId}: ${outcome.status}`);
  }

  async onModuleDestroy(): Promise<void> {
    await this.channel?.close();
    await this.connection?.close();
  }
}

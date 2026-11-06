import { NestFactory } from '@nestjs/core';
import { MicroserviceOptions, Transport } from '@nestjs/microservices';
import * as metricsClient from '@prometheus-io/client';
import type { NextFunction, Request, Response } from 'express';
import { AppModule } from './app.module.js';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const metrics = new metricsClient.Registry();
  metrics.setDefaultLabels({ service: 'inventory-service' });
  metricsClient.collectDefaultMetrics({ register: metrics, prefix: 'inventory_' });
  const requests = new metricsClient.Counter({
    name: 'http_requests_total',
    help: 'Total HTTP requests handled by the inventory service',
    labelNames: ['method', 'status_code'],
    registers: [metrics],
  });
  const latency = new metricsClient.Histogram({
    name: 'http_request_duration_seconds',
    help: 'HTTP request duration in seconds',
    labelNames: ['method', 'status_code'],
    buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5],
    registers: [metrics],
  });
  app.use((req: Request, res: Response, next: NextFunction) => {
    if (req.path === '/metrics') return next();
    const end = latency.startTimer();
    res.on('finish', () => {
      const labels = { method: req.method, status_code: String(res.statusCode) };
      requests.inc(labels);
      end(labels);
    });
    next();
  });
  app.use('/metrics', async (_req: Request, res: Response) => {
    res.setHeader('Content-Type', metrics.contentType);
    res.end(await metrics.metrics());
  });

  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.RMQ,
    options: {
      urls: [process.env.RABBITMQ_URL ?? 'amqp://learner:learner@localhost:5672'],
      queue: 'inventory.commands',
      queueOptions: { durable: true },
      noAck: false,
      prefetchCount: 1,
    },
  });

  await app.startAllMicroservices();
  await app.listen(process.env.PORT ?? 3000);
}
await bootstrap();

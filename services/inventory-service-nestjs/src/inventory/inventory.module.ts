import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { InventoryController } from './inventory.controller.js';
import { InventoryService } from './inventory.service.js';
import { Reservation, ReservationSchema } from './reservation.schema.js';
import { StockItem, StockItemSchema } from './stock-item.schema.js';
import { RabbitOutcomePublisher } from './rabbit-outcome-publisher.js';

@Module({
  imports: [MongooseModule.forFeature([
    { name: Reservation.name, schema: ReservationSchema },
    { name: StockItem.name, schema: StockItemSchema },
  ])],
  controllers: [InventoryController],
  providers: [InventoryService, RabbitOutcomePublisher],
})
export class InventoryModule {}

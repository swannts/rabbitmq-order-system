import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { InventoryController } from './inventory.controller.js';
import { InventoryService } from './inventory.service.js';
import { Reservation, ReservationSchema } from './reservation.schema.js';

@Module({
  imports: [MongooseModule.forFeature([{ name: Reservation.name, schema: ReservationSchema }])],
  controllers: [InventoryController],
  providers: [InventoryService],
})
export class InventoryModule {}

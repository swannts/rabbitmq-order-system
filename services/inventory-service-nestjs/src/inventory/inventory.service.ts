import { Injectable, Logger } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Reservation, ReservationDocument } from './reservation.schema.js';
import { ReserveInventoryCommand } from './reserve-inventory.command.js';

@Injectable()
export class InventoryService {
  private readonly logger = new Logger(InventoryService.name);

  constructor(
    @InjectModel(Reservation.name)
    private readonly reservations: Model<ReservationDocument>,
  ) {}

  async reserve(command: ReserveInventoryCommand): Promise<void> {
    await this.reservations.updateOne(
      { orderId: command.orderId },
      {
        $setOnInsert: {
          orderId: command.orderId,
          itemName: command.itemName,
          quantity: command.quantity,
          status: 'RECEIVED',
        },
      },
      { upsert: true },
    );

    this.logger.log(`Recorded inventory reservation request for order ${command.orderId}`);
  }
}

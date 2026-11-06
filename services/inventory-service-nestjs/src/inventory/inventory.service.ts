import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Reservation, ReservationDocument } from './reservation.schema.js';
import { ReserveInventoryCommand } from './reserve-inventory.command.js';
import { StockItem, StockItemDocument } from './stock-item.schema.js';
import { InventoryOutcome, RabbitOutcomePublisher } from './rabbit-outcome-publisher.js';

@Injectable()
export class InventoryService {
  private readonly logger = new Logger(InventoryService.name);

  constructor(
    @InjectModel(Reservation.name)
    private readonly reservations: Model<ReservationDocument>,
    @InjectModel(StockItem.name)
    private readonly stock: Model<StockItemDocument>,
    private readonly outcomes: RabbitOutcomePublisher,
  ) {}

  async setAvailable(itemName: string, available: number) {
    const name = itemName.trim();
    if (!name || name.length > 160) throw new BadRequestException('itemName must contain 1 to 160 characters');
    if (!Number.isSafeInteger(available) || available < 0) throw new BadRequestException('available must be a non-negative integer');
    return this.stock.findOneAndUpdate(
      { itemName: name },
      { $set: { available } },
      { upsert: true, new: true, runValidators: true },
    ).lean();
  }

  async getStock(itemName: string) {
    const item = await this.stock.findOne({ itemName }).lean();
    if (!item) throw new NotFoundException('Inventory item not found');
    return item;
  }

  async getReservation(orderId: string) {
    const reservation = await this.reservations.findOne({ orderId }).lean();
    if (!reservation) throw new NotFoundException('Reservation not found');
    return reservation;
  }

  async reserve(command: ReserveInventoryCommand): Promise<void> {
    if (!command.orderId || !command.itemName?.trim() || !Number.isSafeInteger(command.quantity) || command.quantity < 1) {
      throw new BadRequestException('Inventory command must include orderId, itemName, and a positive integer quantity');
    }
    const itemName = command.itemName.trim();
    const session = await this.reservations.db.startSession();
    let result: { status: 'RESERVED' | 'REJECTED'; reason?: string } | undefined;
    try {
      await session.withTransaction(async () => {
        const existing = await this.reservations.findOne({ orderId: command.orderId }).session(session).lean();
        if (existing) {
          if (existing.itemName !== itemName || existing.quantity !== command.quantity) {
            throw new BadRequestException('Order ID was already reserved with different item data');
          }
          result = { status: existing.status, reason: existing.reason };
          return;
        }

        const stock = await this.stock.findOneAndUpdate(
          { itemName, available: { $gte: command.quantity } },
          { $inc: { available: -command.quantity } },
          { new: true, session },
        ).lean();
        const status = stock ? 'RESERVED' : 'REJECTED';
        const reason = stock ? undefined : 'INSUFFICIENT_STOCK';
        await this.reservations.create([{
          orderId: command.orderId,
          itemName,
          quantity: command.quantity,
          status,
          ...(reason ? { reason } : {}),
        }], { session });
        result = { status, reason };
      });
    } catch (exception) {
      const duplicate = typeof exception === 'object' && exception !== null && 'code' in exception && exception.code === 11000;
      if (!duplicate) throw exception;
      const existing = await this.reservations.findOne({ orderId: command.orderId }).lean();
      if (!existing || existing.itemName !== itemName || existing.quantity !== command.quantity) throw exception;
      result = { status: existing.status, reason: existing.reason };
    } finally {
      await session.endSession();
    }

    if (!result) throw new Error('Inventory transaction completed without a reservation result');
    this.logger.log(`Inventory reservation ${result.status.toLowerCase()} for order ${command.orderId}`);
    await this.publishOutcome(command, result.status, result.reason);
  }

  private async publishOutcome(command: ReserveInventoryCommand, status: 'RESERVED' | 'REJECTED', reason?: string) {
    const outcome: InventoryOutcome = {
      orderId: command.orderId,
      itemName: command.itemName.trim(),
      quantity: command.quantity,
      status,
      ...(reason ? { reason } : {}),
      occurredAt: new Date().toISOString(),
    };
    await this.outcomes.publishInventoryOutcome(outcome);
  }
}

import { Body, Controller, Get, Logger, Param, Put } from '@nestjs/common';
import { EventPattern, Payload } from '@nestjs/microservices';
import { InventoryService } from './inventory.service.js';
import type { ReserveInventoryCommand } from './reserve-inventory.command.js';

@Controller()
export class InventoryController {
  private readonly logger = new Logger(InventoryController.name);

  constructor(private readonly inventory: InventoryService) {}

  @Put('api/v1/inventory/items/:itemName')
  setStock(@Param('itemName') itemName: string, @Body() body: { available?: number }) {
    return this.inventory.setAvailable(itemName, body?.available as number);
  }

  @Get('api/v1/inventory/items/:itemName')
  getStock(@Param('itemName') itemName: string) {
    return this.inventory.getStock(itemName);
  }

  @Get('api/v1/inventory/reservations/:orderId')
  getReservation(@Param('orderId') orderId: string) {
    return this.inventory.getReservation(orderId);
  }

  @EventPattern('inventory.reserve')
  async reserve(@Payload() command: ReserveInventoryCommand): Promise<void> {
    this.logger.log(`Received inventory.reserve for order ${command.orderId}`);
    await this.inventory.reserve(command);
  }
}

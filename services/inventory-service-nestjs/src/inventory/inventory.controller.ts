import { Controller, Logger } from '@nestjs/common';
import { EventPattern, Payload } from '@nestjs/microservices';
import { InventoryService } from './inventory.service.js';
import type { ReserveInventoryCommand } from './reserve-inventory.command.js';

@Controller()
export class InventoryController {
  private readonly logger = new Logger(InventoryController.name);

  constructor(private readonly inventory: InventoryService) {}

  @EventPattern('inventory.reserve')
  async reserve(@Payload() command: ReserveInventoryCommand): Promise<void> {
    this.logger.log(`Received inventory.reserve for order ${command.orderId}`);
    await this.inventory.reserve(command);
  }
}

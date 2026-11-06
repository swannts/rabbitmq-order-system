import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { AppController } from './app.controller.js';
import { InventoryModule } from './inventory/inventory.module.js';

@Module({
  imports: [
    MongooseModule.forRoot(
      process.env.MONGODB_URI ?? 'mongodb://inventory_admin:inventory_dev@localhost:27017/inventory?authSource=admin',
    ),
    InventoryModule,
  ],
  controllers: [AppController],
})
export class AppModule {}

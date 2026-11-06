import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';

export type StockItemDocument = HydratedDocument<StockItem>;

@Schema({ timestamps: true })
export class StockItem {
  @Prop({ required: true, unique: true })
  itemName!: string;

  @Prop({ required: true, min: 0, default: 0 })
  available!: number;
}

export const StockItemSchema = SchemaFactory.createForClass(StockItem);

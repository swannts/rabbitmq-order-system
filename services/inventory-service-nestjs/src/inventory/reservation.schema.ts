import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';

export type ReservationDocument = HydratedDocument<Reservation>;

@Schema({ timestamps: true })
export class Reservation {
  @Prop({ required: true, unique: true })
  orderId!: string;

  @Prop({ required: true })
  itemName!: string;

  @Prop({ required: true, min: 1 })
  quantity!: number;

  @Prop({ required: true, enum: ['RESERVED', 'REJECTED'] })
  status!: 'RESERVED' | 'REJECTED';

  @Prop()
  reason?: string;
}

export const ReservationSchema = SchemaFactory.createForClass(Reservation);

<?php

namespace App\Services;

use App\Models\Payment;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use InvalidArgumentException;

class PaymentProcessor
{
    public function __construct(private readonly PaymentOutcomePublisher $publisher) {}

    public function authorize(array $command): void
    {
        $orderId = $command['orderId'] ?? null;
        $itemName = trim((string) ($command['itemName'] ?? ''));
        $quantity = filter_var($command['quantity'] ?? null, FILTER_VALIDATE_INT);
        if (! is_string($orderId) || ! Str::isUuid($orderId) || $itemName === '' || strlen($itemName) > 160 || $quantity === false || $quantity < 1) {
            throw new InvalidArgumentException('Payment command is missing a valid orderId, itemName, or positive quantity');
        }

        $payment = DB::transaction(fn () => Payment::firstOrCreate(
            ['order_id' => $orderId],
            [
                'id' => (string) Str::uuid(),
                'item_name' => $itemName,
                'quantity' => $quantity,
                'status' => 'AUTHORIZED',
            ],
        ));

        // Authorization is simulated for this learning project; no payment provider is called.
        $this->publisher->publish([
            'orderId' => $payment->order_id,
            'paymentId' => $payment->id,
            'status' => $payment->status,
            'reason' => $payment->reason,
            'occurredAt' => $payment->updated_at->toISOString(),
        ]);
    }
}

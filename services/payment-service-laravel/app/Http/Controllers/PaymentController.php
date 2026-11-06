<?php

namespace App\Http\Controllers;

use App\Models\Payment;
use Illuminate\Http\JsonResponse;

class PaymentController extends Controller
{
    public function show(string $orderId): JsonResponse
    {
        $payment = Payment::query()->where('order_id', $orderId)->firstOrFail();

        return response()->json([
            'paymentId' => $payment->id,
            'orderId' => $payment->order_id,
            'itemName' => $payment->item_name,
            'quantity' => $payment->quantity,
            'status' => $payment->status,
            'reason' => $payment->reason,
            'createdAt' => $payment->created_at,
            'updatedAt' => $payment->updated_at,
        ]);
    }
}

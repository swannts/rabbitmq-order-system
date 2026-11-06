<?php

use App\Http\Controllers\PaymentController;
use Illuminate\Support\Facades\Route;

Route::get('/v1/payments/{orderId}', [PaymentController::class, 'show']);

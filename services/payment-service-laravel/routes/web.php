<?php

use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return ['service' => 'payment-service', 'status' => 'ready'];
});

Route::get('/healthz', function () {
    return response()->json(['status' => 'ok']);
});

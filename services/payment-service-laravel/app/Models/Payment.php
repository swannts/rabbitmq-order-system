<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

class Payment extends Model
{
    use HasUuids;

    protected $fillable = ['order_id', 'item_name', 'quantity', 'status', 'reason'];

    protected function casts(): array
    {
        return ['quantity' => 'integer'];
    }
}

package com.swann.orders.api;

import com.swann.orders.application.OrderApplicationService;
import com.swann.orders.application.OrderView;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orderService;

    public OrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderView> create(@Valid @RequestBody CreateOrderRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        OrderView order = orderService.create(request, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(order.orderId()).toUri();
        return ResponseEntity.accepted().location(location).body(order);
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) {
        return orderService.get(id);
    }

    @org.springframework.web.bind.annotation.GetMapping
    public Page<OrderView> list(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                                @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return orderService.list(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("createdAt").descending()));
    }
}

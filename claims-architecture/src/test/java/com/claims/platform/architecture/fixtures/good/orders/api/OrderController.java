package com.claims.platform.architecture.fixtures.good.orders.api;

import com.claims.platform.architecture.fixtures.good.orders.application.PlaceOrder;

public class OrderController {
    private final PlaceOrder placeOrder = new PlaceOrder();

    public String post(String id) {
        return placeOrder.place(id).id();
    }
}

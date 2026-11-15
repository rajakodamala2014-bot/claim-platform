package com.claims.platform.architecture.fixtures.good.orders.application;

import com.claims.platform.architecture.fixtures.good.orders.domain.Order;

public class PlaceOrder {
    public Order place(String id) {
        return new Order(id);
    }
}

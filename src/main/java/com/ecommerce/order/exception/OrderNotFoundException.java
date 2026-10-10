package com.ecommerce.order.exception;

import com.ecommerce.common.exception.BusinessException;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(Long id) {
        super("Order not found with id: " + id, "ORDER_NOT_FOUND");
    }
}


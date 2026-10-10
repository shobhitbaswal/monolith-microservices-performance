package com.ecommerce.inventory.exception;

import com.ecommerce.common.exception.BusinessException;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("Insufficient stock for product " + productId + ": requested " + requested + ", available " + available, "INSUFFICIENT_STOCK");
    }
}


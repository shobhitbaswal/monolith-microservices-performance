package com.ecommerce.inventory.exception;

import com.ecommerce.common.exception.BusinessException;

public class InventoryNotFoundException extends BusinessException {

    public InventoryNotFoundException(Long id) {
        super("Inventory not found with id: " + id, "INVENTORY_NOT_FOUND");
    }
}


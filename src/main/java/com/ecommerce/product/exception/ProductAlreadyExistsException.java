package com.ecommerce.product.exception;

import com.ecommerce.common.exception.BusinessException;

public class ProductAlreadyExistsException extends BusinessException {

    public ProductAlreadyExistsException(String identifier) {
        super("Product already exists with identifier: " + identifier, "PRODUCT_ALREADY_EXISTS");
    }
}


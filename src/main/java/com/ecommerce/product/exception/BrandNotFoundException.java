package com.ecommerce.product.exception;

import com.ecommerce.common.exception.BusinessException;

public class BrandNotFoundException extends BusinessException {

    public BrandNotFoundException(Long id) {
        super("Brand not found with id: " + id, "BRAND_NOT_FOUND");
    }
}


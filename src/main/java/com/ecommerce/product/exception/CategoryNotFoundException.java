package com.ecommerce.product.exception;

import com.ecommerce.common.exception.BusinessException;

public class CategoryNotFoundException extends BusinessException {

    public CategoryNotFoundException(Long id) {
        super("Category not found with id: " + id, "CATEGORY_NOT_FOUND");
    }
}


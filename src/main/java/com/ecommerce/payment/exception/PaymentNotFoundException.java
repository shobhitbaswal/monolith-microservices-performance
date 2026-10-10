package com.ecommerce.payment.exception;

import com.ecommerce.common.exception.BusinessException;

public class PaymentNotFoundException extends BusinessException {

    public PaymentNotFoundException(Long id) {
        super("Payment not found with id: " + id, "PAYMENT_NOT_FOUND");
    }
}


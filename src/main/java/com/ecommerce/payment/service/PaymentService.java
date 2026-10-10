package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.request.PaymentCreateRequestDto;
import com.ecommerce.payment.dto.response.PaymentResponseDto;

public interface PaymentService {

    PaymentResponseDto createPayment(PaymentCreateRequestDto requestDto);

    PaymentResponseDto getPaymentById(Long paymentId);

    PaymentResponseDto getPaymentByOrderId(Long orderId);

    PaymentResponseDto markPaymentSuccess(Long paymentId);

    PaymentResponseDto refundPayment(Long paymentId);

    /**
     * Confirm payment by admin/seller (for COD orders after delivery)
     * Only updates payment status to CONFIRMED, does NOT change order status
     */
    PaymentResponseDto confirmPaymentByAdmin(Long orderId);
}


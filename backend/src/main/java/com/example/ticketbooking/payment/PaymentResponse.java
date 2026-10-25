package com.example.ticketbooking.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(long id, long bookingId, String txnRef, BigDecimal amount,
        PaymentStatus status, Instant expiresAt, Instant paidAt, boolean reconciliationRequired,
        String reconciliationReason, String responseCode, String paymentUrl) {
    public static PaymentResponse from(Payment payment, String url) {
        return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getTxnRef(),
                payment.getAmount(), payment.getStatus(), payment.getExpiresAt(), payment.getPaidAt(),
                payment.isReconciliationRequired(), payment.getReconciliationReason(), payment.getResponseCode(), url);
    }
}

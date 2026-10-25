package com.example.ticketbooking.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;
    @Column(name = "txn_ref", nullable = false, length = 100)
    private String txnRef;
    @Column(nullable = false, length = 30)
    private String provider = "VNPAY";
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "response_code", length = 2)
    private String responseCode;
    @Column(name = "transaction_status", length = 2)
    private String transactionStatus;
    @Column(name = "reconciliation_required", nullable = false)
    private boolean reconciliationRequired;
    @Column(name = "reconciliation_reason", length = 80)
    private String reconciliationReason;

    protected Payment() { }
    public Payment(long bookingId, String txnRef, BigDecimal amount, Instant now, Instant expiresAt) {
        this.bookingId = bookingId;
        this.txnRef = txnRef;
        this.amount = amount;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void confirm(boolean success, String responseCode, String transactionStatus,
                        String providerId, Instant paidAt, String reason) {
        this.status = success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        this.responseCode = responseCode;
        this.transactionStatus = transactionStatus;
        this.providerTransactionId = providerId;
        this.paidAt = paidAt;
        this.reconciliationRequired = reason != null;
        this.reconciliationReason = reason;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getTxnRef() { return txnRef; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getResponseCode() { return responseCode; }
    public String getTransactionStatus() { return transactionStatus; }
    public boolean isReconciliationRequired() { return reconciliationRequired; }
    public String getReconciliationReason() { return reconciliationReason; }
}

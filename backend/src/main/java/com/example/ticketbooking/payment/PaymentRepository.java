package com.example.ticketbooking.payment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTxnRef(String txnRef);
    Optional<Payment> findByBookingIdAndStatus(long bookingId, PaymentStatus status);
    boolean existsByBookingIdAndStatus(long bookingId, PaymentStatus status);
    boolean existsByBookingIdAndReconciliationRequiredTrue(long bookingId);
    org.springframework.data.domain.Page<Payment> findByReconciliationRequiredTrue(org.springframework.data.domain.Pageable pageable);
    List<Payment> findByBookingIdOrderByIdDesc(long bookingId);
    boolean existsByProviderTransactionIdAndIdNot(String providerId, long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findLockedById(long id);
}

package com.example.ticketbooking.booking;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    @EntityGraph(attributePaths = "seat")
    List<BookingSeat> findByBookingIdOrderByIdAsc(long bookingId);

    boolean existsByTripIdAndSeatIdInAndActiveTrue(long tripId, List<Long> seatIds);
}

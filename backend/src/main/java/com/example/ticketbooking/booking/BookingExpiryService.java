package com.example.ticketbooking.booking;

import com.example.ticketbooking.common.CatalogGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingExpiryService {
    private final BookingRepository bookings;
    private final BookingSeatRepository bookingSeats;
    private final CatalogGuard guard;
    private final Clock clock;

    public BookingExpiryService(BookingRepository bookings, BookingSeatRepository bookingSeats,
                                CatalogGuard guard, Clock clock) {
        this.bookings = bookings;
        this.bookingSeats = bookingSeats;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional
    public void expireTrip(long tripId) {
        guard.lockTrip(tripId);
        expireLockedTrip(tripId, clock.instant());
    }

    // Caller phải giữ khóa Trip. Dùng cùng transaction với tạo/hủy booking.
    @Transactional(propagation = Propagation.MANDATORY)
    public void expireLockedTrip(long tripId, Instant now) {
        List<Booking> expired = bookings.findExpiredForUpdate(tripId, now);
        for (Booking booking : expired) {
            booking.expire();
            releaseSeats(booking.getId());
        }
        // Ghi is_active=false trước khi request mới thử giữ lại cùng ghế.
        bookingSeats.flush();
        bookings.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSeats(long bookingId) {
        List<BookingSeat> seats = bookingSeats.findByBookingIdOrderByIdAsc(bookingId);
        for (BookingSeat seat : seats) {
            seat.release();
        }
    }
}

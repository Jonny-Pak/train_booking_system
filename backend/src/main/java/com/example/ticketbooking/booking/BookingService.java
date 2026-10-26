package com.example.ticketbooking.booking;

import com.example.ticketbooking.common.CatalogGuard;
import com.example.ticketbooking.common.PageResponse;
import com.example.ticketbooking.trip.Trip;
import com.example.ticketbooking.trip.TripRepository;
import com.example.ticketbooking.trip.TripStatus;
import com.example.ticketbooking.vehicle.Seat;
import com.example.ticketbooking.vehicle.SeatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingService {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");

    private final BookingRepository bookings;
    private final BookingSeatRepository bookingSeats;
    private final TripRepository trips;
    private final SeatRepository seats;
    private final BookingExpiryService expiry;
    private final CatalogGuard guard;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final long holdSeconds;

    public BookingService(BookingRepository bookings, BookingSeatRepository bookingSeats,
                          TripRepository trips, SeatRepository seats, BookingExpiryService expiry,
                          CatalogGuard guard, JdbcTemplate jdbc, Clock clock,
                          @Value("${booking.hold-seconds:600}") long holdSeconds) {
        if (holdSeconds < 30 || holdSeconds > 1800) {
            throw new IllegalArgumentException("booking.hold-seconds must be 30..1800");
        }
        this.bookings = bookings;
        this.bookingSeats = bookingSeats;
        this.trips = trips;
        this.seats = seats;
        this.expiry = expiry;
        this.guard = guard;
        this.jdbc = jdbc;
        this.clock = clock;
        this.holdSeconds = holdSeconds;
    }

    @Transactional
    public BookingResponse create(long userId, BookingRequest request) {
        checkDuplicateSeats(request.seatIds());

        // Cùng khóa Trip với catalog, cancel, expiry và payment ở pha sau.
        guard.lockTrip(request.tripId());
        Trip trip = findTrip(request.tripId());
        Instant now = clock.instant();
        checkTripOpen(trip, now);
        List<Seat> selectedSeats = checkSelectedSeats(trip, request.seatIds());

        expiry.expireLockedTrip(trip.getId(), now);
        if (bookingSeats.existsByTripIdAndSeatIdInAndActiveTrue(trip.getId(), request.seatIds())) {
            throw CatalogGuard.conflict("SEAT_UNAVAILABLE", "One or more seats are no longer available");
        }

        BigDecimal total = trip.getBasePrice().multiply(BigDecimal.valueOf(selectedSeats.size()));
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw CatalogGuard.badRequest("BOOKING_TOTAL_TOO_LARGE", "Booking total exceeds the supported amount");
        }

        Instant holdExpiresAt = now.plusSeconds(holdSeconds);
        if (holdExpiresAt.isAfter(trip.getDepartureAt())) {
            holdExpiresAt = trip.getDepartureAt();
        }

        String code = UUID.randomUUID().toString().replace("-", "");
        Booking booking = new Booking(code, userId, trip.getId(), trip.getVehicle().getId(),
                total, now, holdExpiresAt);
        bookings.saveAndFlush(booking);

        for (Seat seat : selectedSeats) {
            BookingSeat reservedSeat = new BookingSeat(booking, seat, trip.getBasePrice());
            bookingSeats.save(reservedSeat);
        }
        // Unique index PostgreSQL vẫn là lớp bảo vệ cuối cùng nếu có writer khác.
        bookingSeats.flush();
        return toResponse(booking);
    }

    public PageResponse<BookingResponse> myBookings(long userId, int page, int size) {
        Page<Booking> result = bookings.findByUserId(userId, historyPage(page, size));
        return toPage(result);
    }

    public PageResponse<BookingResponse> adminBookings(BookingStatus status, int page, int size) {
        PageRequest paging = historyPage(page, size);
        Page<Booking> result;
        if (status == null) {
            result = bookings.findAll(paging);
        } else {
            result = bookings.findByStatus(status, paging);
        }
        return toPage(result);
    }

    public BookingResponse get(long id, long userId, boolean admin) {
        Booking booking = findBooking(id);
        checkOwner(booking, userId, admin);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancel(long id, long userId, boolean admin) {
        CatalogGuard.positiveId(id);
        // Chỉ đọc trip_id trước khi khóa; chưa tải entity vào persistence context.
        List<Long> tripIds = jdbc.queryForList(
                "SELECT trip_id FROM bookings WHERE id = ?", Long.class, id);
        if (tripIds.isEmpty()) {
            throw CatalogGuard.notFound("Booking");
        }
        guard.lockTrip(tripIds.getFirst());
        Optional<Booking> result = bookings.findLockedById(id);
        if (result.isEmpty()) {
            throw CatalogGuard.notFound("Booking");
        }
        Booking booking = result.get();
        checkOwner(booking, userId, admin);

        expiry.expireLockedTrip(booking.getTripId(), clock.instant());
        if (booking.getStatus() == BookingStatus.PAID) {
            throw CatalogGuard.conflict("BOOKING_ALREADY_PAID", "Paid bookings cannot be cancelled without a refund");
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.cancel();
            expiry.releaseSeats(booking.getId());
            bookingSeats.flush();
            bookings.flush();
        }
        // Gọi lại cancel cho CANCELLED/EXPIRED trả trạng thái hiện tại.
        return toResponse(booking);
    }

    private void checkDuplicateSeats(List<Long> seatIds) {
        Set<Long> uniqueIds = new HashSet<>();
        for (Long seatId : seatIds) {
            if (!uniqueIds.add(seatId)) {
                throw CatalogGuard.badRequest("DUPLICATE_SEAT", "seatIds must not contain duplicates");
            }
        }
    }

    private List<Seat> checkSelectedSeats(Trip trip, List<Long> seatIds) {
        List<Seat> selected = seats.findAllById(seatIds);
        if (selected.size() != seatIds.size()) {
            throw CatalogGuard.notFound("Seat");
        }
        for (Seat seat : selected) {
            if (!seat.getVehicle().getId().equals(trip.getVehicle().getId())) {
                throw CatalogGuard.badRequest("SEAT_WRONG_VEHICLE", "All seats must belong to the trip vehicle");
            }
        }
        return selected;
    }

    private void checkTripOpen(Trip trip, Instant now) {
        if (trip.getStatus() != TripStatus.SCHEDULED || !trip.getDepartureAt().isAfter(now)) {
            throw CatalogGuard.conflict("TRIP_NOT_BOOKABLE", "Trip is closed or has already departed");
        }
        if (!trip.getRoute().isActive() || !trip.getVehicle().isActive()) {
            throw CatalogGuard.conflict("CATALOG_INACTIVE", "Route and vehicle must be active");
        }
    }

    private Trip findTrip(long id) {
        Optional<Trip> result = trips.findById(id);
        if (result.isEmpty()) {
            throw CatalogGuard.notFound("Trip");
        }
        return result.get();
    }

    private Booking findBooking(long id) {
        CatalogGuard.positiveId(id);
        Optional<Booking> result = bookings.findById(id);
        if (result.isEmpty()) {
            throw CatalogGuard.notFound("Booking");
        }
        return result.get();
    }

    private void checkOwner(Booking booking, long userId, boolean admin) {
        if (!admin && !booking.getUserId().equals(userId)) {
            // Không tiết lộ đơn của người khác có tồn tại hay không.
            throw CatalogGuard.notFound("Booking");
        }
    }

    private PageRequest historyPage(int page, int size) {
        CatalogGuard.page(page, size, "id");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private PageResponse<BookingResponse> toPage(Page<Booking> page) {
        List<BookingResponse> content = new ArrayList<>();
        for (Booking booking : page.getContent()) {
            content.add(toResponse(booking));
        }
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private BookingResponse toResponse(Booking booking) {
        List<BookingSeatResponse> seatResponses = new ArrayList<>();
        List<BookingSeat> reservedSeats = bookingSeats.findByBookingIdOrderByIdAsc(booking.getId());
        for (BookingSeat reservedSeat : reservedSeats) {
            seatResponses.add(BookingSeatResponse.from(reservedSeat));
        }
        boolean holdExpired = booking.getStatus() == BookingStatus.EXPIRED
                || booking.holdExpired(clock.instant());
        return new BookingResponse(
                booking.getId(), booking.getBookingCode(), booking.getUserId(),
                booking.getTripId(), booking.getVehicleId(), booking.getStatus(),
                booking.getTotalAmount(), booking.getHoldExpiresAt(), booking.getCreatedAt(),
                holdExpired, seatResponses);
    }
}

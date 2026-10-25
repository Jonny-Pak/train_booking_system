package com.example.ticketbooking.payment;

import com.example.ticketbooking.booking.*;
import com.example.ticketbooking.common.CatalogGuard;
import com.example.ticketbooking.common.PageResponse;
import com.example.ticketbooking.ticket.TicketService;
import com.example.ticketbooking.trip.Trip;
import com.example.ticketbooking.trip.TripRepository;
import com.example.ticketbooking.trip.TripStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final BookingSeatRepository seats;
    private final BookingExpiryService expiry;
    private final TripRepository trips;
    private final CatalogGuard guard;
    private final JdbcTemplate jdbc;
    private final TicketService tickets;
    private final VnpayGateway gateway;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, BookingSeatRepository seats,
            BookingExpiryService expiry, TripRepository trips, CatalogGuard guard, JdbcTemplate jdbc,
            TicketService tickets, VnpayGateway gateway, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.seats = seats;
        this.expiry = expiry;
        this.trips = trips;
        this.guard = guard;
        this.jdbc = jdbc;
        this.tickets = tickets;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse create(long bookingId, long userId, String ipAddress) {
        Booking booking = lockBooking(bookingId);
        checkOwner(booking, userId, false);
        gateway.requireEnabled();
        Instant now = clock.instant();
        Trip trip = trips.findById(booking.getTripId()).orElseThrow(() -> CatalogGuard.notFound("Trip"));
        if (booking.getStatus() != BookingStatus.PENDING || !booking.getHoldExpiresAt().isAfter(now.plusSeconds(5))) {
            throw CatalogGuard.conflict("BOOKING_NOT_PAYABLE", "Booking is closed or its hold is ending");
        }
        if (!tripOpen(trip, now)) throw CatalogGuard.conflict("TRIP_NOT_BOOKABLE", "Trip is closed");
        if (payments.existsByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESS)
                || payments.existsByBookingIdAndReconciliationRequiredTrue(bookingId)) {
            throw CatalogGuard.conflict("PAYMENT_RECONCILIATION_REQUIRED", "Payment needs reconciliation");
        }
        VnpayGateway.amount(booking.getTotalAmount());
        Payment payment = payments.findByBookingIdAndStatus(bookingId, PaymentStatus.PENDING).orElse(null);
        if (payment == null) {
            payment = new Payment(bookingId, UUID.randomUUID().toString().replace("-", ""),
                    booking.getTotalAmount(), now.truncatedTo(ChronoUnit.SECONDS),
                    booking.getHoldExpiresAt().truncatedTo(ChronoUnit.SECONDS));
            payments.saveAndFlush(payment);
        }
        return PaymentResponse.from(payment, gateway.paymentUrl(payment, ipAddress));
    }

    public List<PaymentResponse> list(long bookingId, long userId, boolean admin) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> CatalogGuard.notFound("Booking"));
        checkOwner(booking, userId, admin);
        List<PaymentResponse> result = new ArrayList<>();
        for (Payment payment : payments.findByBookingIdOrderByIdDesc(bookingId)) result.add(PaymentResponse.from(payment, null));
        return result;
    }

    public PaymentResponse get(String txnRef, long userId, boolean admin) {
        Payment payment = payments.findByTxnRef(txnRef).orElseThrow(() -> CatalogGuard.notFound("Payment"));
        Booking booking = bookings.findById(payment.getBookingId()).orElseThrow(() -> CatalogGuard.notFound("Booking"));
        checkOwner(booking, userId, admin);
        return PaymentResponse.from(payment, null);
    }

    public PageResponse<PaymentResponse> reconciliation(int page, int size) {
        CatalogGuard.page(page, size);
        var result = payments.findByReconciliationRequiredTrue(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<PaymentResponse> content = new ArrayList<>();
        for (Payment payment : result) content.add(PaymentResponse.from(payment, null));
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public IpnResponse confirm(MultiValueMap<String, String> query) {
        Map<String, String> fields = gateway.verifiedFields(query);
        if (fields == null) return IpnResponse.invalid();
        String ref = fields.get("vnp_TxnRef");
        if (ref == null || ref.length() > 100) return IpnResponse.missing();
        // Read only IDs first. Load entities after the Trip -> Booking -> Payment locks.
        List<Long> ids = jdbc.queryForList("SELECT booking_id FROM payments WHERE txn_ref = ?", Long.class, ref);
        if (ids.isEmpty()) return IpnResponse.missing();
        Booking booking = lockBooking(ids.getFirst());
        long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE txn_ref = ?", Long.class, ref);
        Payment payment = payments.findLockedById(paymentId).orElseThrow();
        String amount = fields.get("vnp_Amount");
        if (amount == null || !amount.matches("[0-9]{1,12}")
                || !new java.math.BigDecimal(amount).equals(new java.math.BigDecimal(VnpayGateway.amount(payment.getAmount())))
                || payment.getAmount().compareTo(booking.getTotalAmount()) != 0) return IpnResponse.amount();
        if (fields.containsKey("vnp_CurrCode") && !"VND".equals(fields.get("vnp_CurrCode"))) return IpnResponse.amount();
        String response = fields.get("vnp_ResponseCode");
        String status = fields.get("vnp_TransactionStatus");
        if (response == null || status == null || !response.matches("[0-9]{2}") || !status.matches("[0-9]{2}")) return IpnResponse.error();
        boolean success = "00".equals(response) && "00".equals(status);
        String providerId = fields.get("vnp_TransactionNo");
        if (providerId != null && providerId.matches("0+")) providerId = null;
        if (providerId != null && !providerId.matches("[1-9][0-9]{0,99}")) return IpnResponse.error();
        if (success && providerId == null) return IpnResponse.error();
        Instant paidAt = null;
        String payDate = fields.get("vnp_PayDate");
        if (payDate != null && !payDate.isEmpty()) {
            try { paidAt = VnpayGateway.parseDate(payDate); }
            catch (java.time.DateTimeException exception) { return IpnResponse.error(); }
        }
        if (success && paidAt == null) return IpnResponse.error();
        if (providerId != null && payments.existsByProviderTransactionIdAndIdNot(providerId, paymentId)) return IpnResponse.error();

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (success && Objects.equals(providerId, payment.getProviderTransactionId())
                    && Objects.equals(paidAt, payment.getPaidAt())) return IpnResponse.duplicate();
            return IpnResponse.error(); // Never downgrade a completed payment.
        }
        if (payment.getStatus() == PaymentStatus.FAILED && !success) {
            if (Objects.equals(response, payment.getResponseCode()) && Objects.equals(status, payment.getTransactionStatus())
                    && Objects.equals(providerId, payment.getProviderTransactionId())) return IpnResponse.duplicate();
            return IpnResponse.error();
        }

        Instant now = clock.instant();
        if (booking.holdExpired(now)) {
            booking.expire();
            expiry.releaseSeats(booking.getId());
        }
        String reason = null;
        if (success) {
            if (payment.getStatus() == PaymentStatus.FAILED) reason = "CONFLICTING_CALLBACK";
            else if (booking.getStatus() != BookingStatus.PENDING) reason = "BOOKING_" + booking.getStatus();
            else if (!payment.getExpiresAt().isAfter(now) || !payment.getExpiresAt().isAfter(paidAt)) reason = "PAYMENT_EXPIRED";
            else if (paidAt.isBefore(payment.getCreatedAt()) || paidAt.isAfter(now.plusSeconds(60))) reason = "INVALID_PAY_DATE";
            else if (!tripOpen(trips.findById(booking.getTripId()).orElseThrow(), now)) reason = "TRIP_CLOSED";
            else {
                List<BookingSeat> reserved = seats.findByBookingIdOrderByIdAsc(booking.getId());
                if (reserved.isEmpty()) reason = "SEATS_NOT_RESERVED";
                for (BookingSeat seat : reserved) if (!seat.isActive()) reason = "SEATS_NOT_RESERVED";
            }
            if (reason == null) {
                booking.pay();
                tickets.issue(booking, now);
            }
        } else if ("07".equals(response) || List.of("01", "04", "05", "06", "07", "09").contains(status)) {
            // Pending, reversal, refund and suspected-fraud results need manual review.
            reason = "PROVIDER_REVIEW_REQUIRED";
        } else if ("00".equals(response) || "00".equals(status)) {
            reason = "PROVIDER_STATUS_CONFLICT";
        }
        payment.confirm(success, response, status, providerId, success ? paidAt : null, reason);
        payments.flush();
        bookings.flush();
        return IpnResponse.confirmed();
    }

    private Booking lockBooking(long id) {
        CatalogGuard.positiveId(id);
        List<Long> tripIds = jdbc.queryForList("SELECT trip_id FROM bookings WHERE id = ?", Long.class, id);
        if (tripIds.isEmpty()) throw CatalogGuard.notFound("Booking");
        guard.lockTrip(tripIds.getFirst());
        return bookings.findLockedById(id).orElseThrow(() -> CatalogGuard.notFound("Booking"));
    }
    private void checkOwner(Booking booking, long userId, boolean admin) {
        if (!admin && booking.getUserId() != userId) throw CatalogGuard.notFound("Booking");
    }
    private boolean tripOpen(Trip trip, Instant now) {
        return trip.getStatus() == TripStatus.SCHEDULED && trip.getDepartureAt().isAfter(now)
                && trip.getRoute().isActive() && trip.getVehicle().isActive();
    }
}

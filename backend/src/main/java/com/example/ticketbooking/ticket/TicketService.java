package com.example.ticketbooking.ticket;

import com.example.ticketbooking.booking.*;
import com.example.ticketbooking.common.CatalogGuard;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketService {
    private final BookingRepository bookings;
    private final BookingSeatRepository seats;
    private final TicketRepository tickets;
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
        SELECT tk.ticket_code, b.id AS booking_id, b.booking_code, u.full_name,
               r.origin, r.destination, v.display_name, v.vehicle_code, v.vehicle_type,
               tr.departure_at, tr.arrival_at, s.seat_code, s.seat_class, bs.unit_price, tk.issued_at
        FROM tickets tk JOIN booking_seats bs ON bs.id = tk.booking_seat_id
        JOIN bookings b ON b.id = bs.booking_id JOIN users u ON u.id = b.user_id
        JOIN trips tr ON tr.id = b.trip_id JOIN routes r ON r.id = tr.route_id
        JOIN vehicles v ON v.id = b.vehicle_id JOIN seats s ON s.id = bs.seat_id
        WHERE b.id = ? AND b.status = 'PAID'
        """;

    public TicketService(BookingRepository bookings, BookingSeatRepository seats,
                         TicketRepository tickets, JdbcTemplate jdbc) {
        this.bookings = bookings;
        this.seats = seats;
        this.tickets = tickets;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.PAID) throw new IllegalStateException("Booking must be paid");
        for (BookingSeat seat : seats.findByBookingIdOrderByIdAsc(booking.getId())) {
            tickets.save(new Ticket(seat.getId(), now));
        }
        // Any error rolls back tickets, payment and booking together.
        tickets.flush();
    }

    public List<TicketResponse> list(long bookingId, long userId, boolean admin) {
        CatalogGuard.positiveId(bookingId);
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> CatalogGuard.notFound("Booking"));
        if (!admin && booking.getUserId() != userId) throw CatalogGuard.notFound("Booking");
        return jdbc.query(SELECT + " ORDER BY tk.id", (row, index) -> new TicketResponse(
                row.getString("ticket_code"), row.getLong("booking_id"), row.getString("booking_code"),
                row.getString("full_name"), row.getString("origin"), row.getString("destination"),
                row.getString("display_name"), row.getString("vehicle_code"), row.getString("vehicle_type"),
                row.getTimestamp("departure_at").toInstant(), row.getTimestamp("arrival_at").toInstant(),
                row.getString("seat_code"), row.getString("seat_class"), row.getBigDecimal("unit_price"),
                row.getTimestamp("issued_at").toInstant(), "LEN_DUONG:" + row.getString("ticket_code")), bookingId);
    }
}

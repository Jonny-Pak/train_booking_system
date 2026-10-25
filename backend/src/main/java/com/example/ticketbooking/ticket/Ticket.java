package com.example.ticketbooking.ticket;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_code", nullable = false, length = 40)
    private String ticketCode;
    @Column(name = "booking_seat_id", nullable = false)
    private Long bookingSeatId;
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
    protected Ticket() { }
    public Ticket(long bookingSeatId, Instant now) {
        this.ticketCode = "TKT" + UUID.randomUUID().toString().replace("-", "");
        this.bookingSeatId = bookingSeatId;
        this.issuedAt = now;
    }
}

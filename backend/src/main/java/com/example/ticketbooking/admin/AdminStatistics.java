package com.example.ticketbooking.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminStatistics(LocalDate from, LocalDate to, long totalBookings, long paidBookings,
        long pendingBookings, long expiredBookings, long cancelledBookings, BigDecimal revenue,
        long issuedTickets, long reconciliationPayments, BigDecimal reconciliationAmount) { }

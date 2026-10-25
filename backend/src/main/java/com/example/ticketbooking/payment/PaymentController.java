package com.example.ticketbooking.payment;

import com.example.ticketbooking.common.PageResponse;
import com.example.ticketbooking.ticket.TicketResponse;
import com.example.ticketbooking.ticket.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
public class PaymentController {
    private final PaymentService service;
    private final TicketService tickets;
    public PaymentController(PaymentService service, TicketService tickets) {
        this.service = service;
        this.tickets = tickets;
    }
    @PostMapping("/bookings/{id}/payments")
    public ResponseEntity<PaymentResponse> create(@PathVariable long id, Authentication auth, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";
        // Forwarded headers are not trusted without an explicitly configured trusted proxy.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.create(id, Long.parseLong(auth.getName()), ip));
    }
    @GetMapping("/bookings/{id}/payments")
    public ResponseEntity<List<PaymentResponse>> list(@PathVariable long id, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(id, Long.parseLong(auth.getName()), isAdmin(auth)));
    }
    @GetMapping("/payments/{txnRef}")
    public ResponseEntity<PaymentResponse> get(@PathVariable String txnRef, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(txnRef, Long.parseLong(auth.getName()), isAdmin(auth)));
    }
    @GetMapping("/bookings/{id}/tickets")
    public ResponseEntity<List<TicketResponse>> tickets(@PathVariable long id, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tickets.list(id, Long.parseLong(auth.getName()), isAdmin(auth)));
    }
    @GetMapping("/admin/payments/reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<PaymentResponse>> reconciliation(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reconciliation(page, size));
    }
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

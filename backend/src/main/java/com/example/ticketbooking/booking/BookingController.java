package com.example.ticketbooking.booking;

import com.example.ticketbooking.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            Authentication authentication, @Valid @RequestBody BookingRequest request) {
        long userId = Long.parseLong(authentication.getName());
        BookingResponse booking = service.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(booking);
    }

    @GetMapping("/my")
    public ResponseEntity<PageResponse<BookingResponse>> myBookings(
            Authentication authentication, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.myBookings(userId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> get(@PathVariable long id, Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        BookingResponse booking = service.get(id, userId, isAdmin(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(booking);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable long id, Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        BookingResponse booking = service.cancel(id, userId, isAdmin(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(booking);
    }

    private boolean isAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}

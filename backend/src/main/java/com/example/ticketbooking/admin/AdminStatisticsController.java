package com.example.ticketbooking.admin;

import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatisticsController {
    private final AdminStatisticsService service;

    public AdminStatisticsController(AdminStatisticsService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<AdminStatistics> get(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(from, to));
    }
}

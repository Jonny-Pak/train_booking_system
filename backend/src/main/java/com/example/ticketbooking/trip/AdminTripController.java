package com.example.ticketbooking.trip;

import com.example.ticketbooking.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/trips")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTripController {
    private final TripService service;

    public AdminTripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TripResponse> list(@RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.adminList(status, routeId, vehicleId, page, size);
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable long id) {
        return service.adminDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@Valid @RequestBody TripRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TripResponse update(@PathVariable long id,
            @Valid @RequestBody TripRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}

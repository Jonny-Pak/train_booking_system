package com.example.ticketbooking.trip;

import com.example.ticketbooking.common.PageResponse;
import com.example.ticketbooking.vehicle.VehicleType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TripResponse> search(@RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(origin, destination, date, vehicleType, page, size);
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable long id) {
        return service.publicDetail(id);
    }

    @GetMapping("/{id}/seats")
    public List<TripSeatResponse> seats(@PathVariable long id) {
        return service.publicSeats(id);
    }
}

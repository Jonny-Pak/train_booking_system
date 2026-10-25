package com.example.ticketbooking.vehicle;

import com.example.ticketbooking.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/vehicles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVehicleController {
    private final VehicleService service;

    public AdminVehicleController(VehicleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<VehicleResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable long id,
            @Valid @RequestBody VehicleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @GetMapping("/{vehicleId}/seats")
    public PageResponse<SeatResponse> seats(@PathVariable long vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.seats(vehicleId, page, size);
    }

    @GetMapping("/{vehicleId}/seats/{seatId}")
    public SeatResponse seat(@PathVariable long vehicleId,
            @PathVariable long seatId) {
        return service.seat(vehicleId, seatId);
    }

    @PostMapping("/{vehicleId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse createSeat(@PathVariable long vehicleId,
            @Valid @RequestBody SeatRequest request) {
        return service.createSeat(vehicleId, request);
    }

    @PutMapping("/{vehicleId}/seats/{seatId}")
    public SeatResponse updateSeat(@PathVariable long vehicleId,
            @PathVariable long seatId,
            @Valid @RequestBody SeatRequest request) {
        return service.updateSeat(vehicleId, seatId, request);
    }

    @DeleteMapping("/{vehicleId}/seats/{seatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSeat(@PathVariable long vehicleId,
            @PathVariable long seatId) {
        service.deleteSeat(vehicleId, seatId);
    }
}

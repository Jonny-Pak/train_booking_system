package com.example.ticketbooking.route;

import com.example.ticketbooking.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/routes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRouteController {
    private final RouteService service;

    public AdminRouteController(RouteService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<RouteResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public RouteResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteResponse create(@Valid @RequestBody RouteRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public RouteResponse update(@PathVariable long id,
            @Valid @RequestBody RouteRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}

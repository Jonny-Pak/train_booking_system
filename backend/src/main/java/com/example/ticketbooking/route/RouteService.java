package com.example.ticketbooking.route;

import com.example.ticketbooking.common.CatalogGuard;
import com.example.ticketbooking.common.PageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RouteService {
    private final RouteRepository routes;
    private final CatalogGuard guard;
    private final JdbcTemplate jdbc;

    public RouteService(RouteRepository routes, CatalogGuard guard, JdbcTemplate jdbc) {
        this.routes = routes;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    public PageResponse<RouteResponse> list(int page, int size) {
        Page<Route> result = routes.findAll(CatalogGuard.page(page, size, "id"));
        List<RouteResponse> content = new ArrayList<>();
        for (Route route : result.getContent()) {
            content.add(RouteResponse.from(route));
        }
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public RouteResponse get(long id) {
        Route route = find(id);
        return RouteResponse.from(route);
    }

    @Transactional
    public RouteResponse create(RouteRequest request) {
        guard.lockWrites();
        validate(request);
        Route route = new Route(request);
        routes.saveAndFlush(route);
        return RouteResponse.from(route);
    }

    @Transactional
    public RouteResponse update(long id, RouteRequest request) {
        guard.lockWrites();
        guard.lockRouteTrips(id);
        Route route = find(id);
        validate(request);

        boolean originChanged = !route.getOrigin().equals(request.origin());
        boolean destinationChanged = !route.getDestination().equals(request.destination());
        if (hasTrips(id) && (originChanged || destinationChanged)) {
            throw CatalogGuard.conflict("ROUTE_IN_USE", "Endpoints of a route with trips cannot change");
        }

        route.update(request);
        routes.saveAndFlush(route);
        return RouteResponse.from(route);
    }

    @Transactional
    public void delete(long id) {
        guard.lockWrites();
        Route route = find(id);
        if (hasTrips(id)) {
            throw CatalogGuard.conflict("ROUTE_IN_USE", "Deactivate a route with trips instead of deleting it");
        }
        routes.delete(route);
        routes.flush();
    }

    private Route find(long id) {
        CatalogGuard.positiveId(id);
        Optional<Route> result = routes.findById(id);
        if (result.isEmpty()) {
            throw CatalogGuard.notFound("Route");
        }
        return result.get();
    }

    private boolean hasTrips(long id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM trips WHERE route_id = ?)";
        Boolean result = jdbc.queryForObject(sql, Boolean.class, id);
        return Boolean.TRUE.equals(result);
    }

    private void validate(RouteRequest request) {
        if (request.origin().equalsIgnoreCase(request.destination())) {
            throw CatalogGuard.badRequest("INVALID_ROUTE", "Origin and destination must differ");
        }
    }
}

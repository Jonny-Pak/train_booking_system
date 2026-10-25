package com.example.ticketbooking.trip;

import com.example.ticketbooking.common.CatalogGuard;
import com.example.ticketbooking.common.PageResponse;
import com.example.ticketbooking.route.Route;
import com.example.ticketbooking.route.RouteRepository;
import com.example.ticketbooking.vehicle.Seat;
import com.example.ticketbooking.vehicle.SeatRepository;
import com.example.ticketbooking.vehicle.Vehicle;
import com.example.ticketbooking.vehicle.VehicleRepository;
import com.example.ticketbooking.vehicle.VehicleType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TripService {
    public static final ZoneId SEARCH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TripRepository trips;
    private final TripQueryRepository tripQueries;
    private final RouteRepository routes;
    private final VehicleRepository vehicles;
    private final SeatRepository seats;
    private final CatalogGuard guard;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TripService(TripRepository trips, TripQueryRepository tripQueries,
                       RouteRepository routes, VehicleRepository vehicles,
                       SeatRepository seats, CatalogGuard guard, JdbcTemplate jdbc, Clock clock) {
        this.trips = trips;
        this.tripQueries = tripQueries;
        this.routes = routes;
        this.vehicles = vehicles;
        this.seats = seats;
        this.guard = guard;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PageResponse<TripResponse> search(String origin, String destination, LocalDate date,
                                             VehicleType vehicleType, int page, int size) {
        String from = normalizeSearchText(origin);
        String to = normalizeSearchText(destination);
        Instant dayStart = null;
        Instant dayEnd = null;

        if (date != null) {
            dayStart = date.atStartOfDay(SEARCH_ZONE).toInstant();
            dayEnd = date.plusDays(1).atStartOfDay(SEARCH_ZONE).toInstant();
        }

        PageRequest paging = CatalogGuard.page(page, size, "departureAt", "id");
        Page<Trip> result = tripQueries.searchPublic(
                from, to, vehicleType, clock.instant(), dayStart, dayEnd, paging);
        return toTripPage(result);
    }

    public TripResponse publicDetail(long id) {
        Trip trip = findPublicTrip(id);
        return TripResponse.from(trip);
    }

    public List<LocationRouteResponse> publicLocations(VehicleType vehicleType, LocalDate date) {
        Instant start = null;
        Instant end = null;
        if (date != null) {
            start = date.atStartOfDay(SEARCH_ZONE).toInstant();
            end = date.plusDays(1).atStartOfDay(SEARCH_ZONE).toInstant();
        }
        return tripQueries.publicLocations(vehicleType, clock.instant(), start, end);
    }

    public List<TripSeatResponse> publicSeats(long id) {
        Trip trip = findPublicTrip(id);
        Map<Long, TripSeatResponse.SeatAvailability> occupied = loadOccupiedSeats(id);
        List<Seat> vehicleSeats = seats.findByVehicleIdOrderBySeatCodeAscIdAsc(trip.getVehicle().getId());
        List<TripSeatResponse> result = new ArrayList<>();

        for (Seat seat : vehicleSeats) {
            TripSeatResponse.SeatAvailability status = occupied.get(seat.getId());
            if (status == null) {
                status = TripSeatResponse.SeatAvailability.AVAILABLE;
            }

            TripSeatResponse response = new TripSeatResponse(
                    seat.getId(), seat.getSeatCode(), seat.getSeatClass(), status);
            result.add(response);
        }

        return result;
    }

    public PageResponse<TripResponse> adminList(
            TripStatus status, Long routeId, Long vehicleId, int page, int size) {
        if (routeId != null) {
            CatalogGuard.positiveId(routeId);
        }
        if (vehicleId != null) {
            CatalogGuard.positiveId(vehicleId);
        }

        PageRequest paging = CatalogGuard.page(page, size, "departureAt", "id");
        Page<Trip> result = tripQueries.searchAdmin(status, routeId, vehicleId, paging);
        return toTripPage(result);
    }

    public TripResponse adminDetail(long id) {
        Trip trip = findTrip(id);
        return TripResponse.from(trip);
    }

    @Transactional
    public TripResponse create(TripRequest request) {
        guard.lockWrites();

        if (request.status() != TripStatus.SCHEDULED) {
            throw CatalogGuard.badRequest("INVALID_TRIP_STATUS", "A new trip must be SCHEDULED");
        }

        Route route = findRoute(request.routeId());
        Vehicle vehicle = findVehicle(request.vehicleId());
        validateTrip(request, route, vehicle, -1L);

        Trip trip = new Trip(route, vehicle, request);
        Trip savedTrip = trips.saveAndFlush(trip);
        return TripResponse.from(savedTrip);
    }

    @Transactional
    public TripResponse update(long id, TripRequest request) {
        guard.lockWrites();
        guard.lockTrip(id);

        Trip trip = findTrip(id);
        checkTripCanBeEdited(trip);
        checkCompletionTime(trip, request);

        Route route = findRoute(request.routeId());
        Vehicle vehicle = findVehicle(request.vehicleId());
        validateTrip(request, route, vehicle, id);

        trip.update(route, vehicle, request);
        Trip savedTrip = trips.saveAndFlush(trip);
        return TripResponse.from(savedTrip);
    }

    @Transactional
    public void delete(long id) {
        guard.lockWrites();
        guard.lockTrip(id);

        Trip trip = findTrip(id);
        if (guard.tripHasBookings(id)) {
            throw CatalogGuard.conflict("TRIP_HAS_BOOKINGS", "A trip with booking history cannot be deleted");
        }

        trips.delete(trip);
        trips.flush();
    }

    private void checkTripCanBeEdited(Trip trip) {
        if (guard.tripHasBookings(trip.getId())) {
            throw CatalogGuard.conflict("TRIP_HAS_BOOKINGS", "A trip with booking history cannot be changed");
        }
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw CatalogGuard.conflict("TRIP_TERMINAL", "Cancelled/completed trips cannot be changed");
        }
    }

    private void checkCompletionTime(Trip trip, TripRequest request) {
        if (request.status() != TripStatus.COMPLETED) {
            return;
        }

        Instant now = clock.instant();
        boolean originalArrivalIsFuture = trip.getArrivalAt().isAfter(now);
        boolean newArrivalIsFuture = request.arrivalAt().isAfter(now);
        if (originalArrivalIsFuture || newArrivalIsFuture) {
            throw CatalogGuard.conflict("TRIP_NOT_FINISHED", "Trip cannot be completed before its arrival time");
        }
    }

    private void validateTrip(TripRequest request, Route route, Vehicle vehicle, long excludeId) {
        if (!request.arrivalAt().isAfter(request.departureAt())) {
            throw CatalogGuard.badRequest("INVALID_TRIP_TIME", "Arrival must be after departure");
        }

        if (request.status() == TripStatus.SCHEDULED) {
            checkScheduledTrip(request, route, vehicle);
        }

        if (request.status() == TripStatus.CANCELLED) {
            return;
        }

        boolean overlaps = trips.overlaps(
                vehicle.getId(), excludeId, request.departureAt(), request.arrivalAt());
        if (overlaps) {
            throw CatalogGuard.conflict("VEHICLE_SCHEDULE_CONFLICT", "Vehicle is already assigned during this interval");
        }
    }

    private void checkScheduledTrip(TripRequest request, Route route, Vehicle vehicle) {
        if (!request.departureAt().isAfter(clock.instant())) {
            throw CatalogGuard.badRequest("TRIP_IN_PAST", "Scheduled departure must be in the future");
        }
        if (!route.isActive() || !vehicle.isActive()) {
            throw CatalogGuard.conflict("CATALOG_INACTIVE", "Route and vehicle must be active");
        }
        if (!seats.existsByVehicleId(vehicle.getId())) {
            throw CatalogGuard.conflict("VEHICLE_HAS_NO_SEATS", "Vehicle must have seats before scheduling");
        }
    }

    private Trip findPublicTrip(long id) {
        Trip trip = findTrip(id);
        boolean scheduled = trip.getStatus() == TripStatus.SCHEDULED;
        boolean activeCatalog = trip.getRoute().isActive() && trip.getVehicle().isActive();
        boolean notDeparted = trip.getDepartureAt().isAfter(clock.instant());

        if (!scheduled || !activeCatalog || !notDeparted) {
            throw CatalogGuard.notFound("Trip");
        }

        return trip;
    }

    private Trip findTrip(long id) {
        CatalogGuard.positiveId(id);
        Optional<Trip> trip = trips.findById(id);
        if (trip.isEmpty()) {
            throw CatalogGuard.notFound("Trip");
        }
        return trip.get();
    }

    private Route findRoute(long id) {
        Optional<Route> route = routes.findById(id);
        if (route.isEmpty()) {
            throw CatalogGuard.notFound("Route");
        }
        return route.get();
    }

    private Vehicle findVehicle(long id) {
        Optional<Vehicle> vehicle = vehicles.findById(id);
        if (vehicle.isEmpty()) {
            throw CatalogGuard.notFound("Vehicle");
        }
        return vehicle.get();
    }

    private Map<Long, TripSeatResponse.SeatAvailability> loadOccupiedSeats(long tripId) {
        String sql = """
                SELECT bs.seat_id, b.status FROM booking_seats bs
                JOIN bookings b ON b.id = bs.booking_id
                WHERE bs.trip_id = ? AND bs.is_active IS TRUE
                  AND (b.status = 'PAID' OR (b.status = 'PENDING' AND b.hold_expires_at > ?))
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(
                sql, tripId, clock.instant().atOffset(ZoneOffset.UTC));
        Map<Long, TripSeatResponse.SeatAvailability> result = new HashMap<>();

        for (Map<String, Object> row : rows) {
            long seatId = ((Number) row.get("seat_id")).longValue();
            String bookingStatus = (String) row.get("status");

            if ("PAID".equals(bookingStatus)) {
                result.put(seatId, TripSeatResponse.SeatAvailability.BOOKED);
            } else {
                result.put(seatId, TripSeatResponse.SeatAvailability.HELD);
            }
        }

        return result;
    }

    private PageResponse<TripResponse> toTripPage(Page<Trip> page) {
        List<TripResponse> content = new ArrayList<>();
        for (Trip trip : page.getContent()) {
            content.add(TripResponse.from(trip));
        }
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private String normalizeSearchText(String input) {
        if (input == null) {
            return null;
        }

        String text = input.strip();
        if (text.isEmpty() || text.length() > 160) {
            throw CatalogGuard.badRequest("INVALID_SEARCH", "Search endpoint names must have 1..160 characters");
        }
        return text.toLowerCase(Locale.ROOT);
    }
}

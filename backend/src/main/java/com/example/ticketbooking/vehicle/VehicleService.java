package com.example.ticketbooking.vehicle;

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
public class VehicleService {
    private final VehicleRepository vehicles;
    private final SeatRepository seats;
    private final CatalogGuard guard;
    private final JdbcTemplate jdbc;

    public VehicleService(VehicleRepository vehicles, SeatRepository seats, CatalogGuard guard, JdbcTemplate jdbc) {
        this.vehicles = vehicles;
        this.seats = seats;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    public PageResponse<VehicleResponse> list(int page, int size) {
        Page<Vehicle> result = vehicles.findAll(CatalogGuard.page(page, size, "id"));
        List<VehicleResponse> content = new ArrayList<>();
        for (Vehicle vehicle : result.getContent()) {
            content.add(VehicleResponse.from(vehicle));
        }
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public VehicleResponse get(long id) {
        Vehicle vehicle = find(id);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        guard.lockWrites();
        uniqueCode(request.vehicleCode(), -1L);
        Vehicle vehicle = new Vehicle(request);
        vehicles.saveAndFlush(vehicle);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse update(long id, VehicleRequest request) {
        guard.lockWrites();
        guard.lockVehicleTrips(id);
        Vehicle vehicle = find(id);
        uniqueCode(request.vehicleCode(), id);
        if ((hasTrips(id) || !"CUSTOM".equals(vehicle.getSeatLayout())) && vehicle.getVehicleType() != request.vehicleType()) {
            throw CatalogGuard.conflict("VEHICLE_IN_USE", "Type of a vehicle with trips cannot change");
        }
        vehicle.update(request);
        vehicles.saveAndFlush(vehicle);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(long id) {
        guard.lockWrites();
        Vehicle vehicle = find(id);
        if (hasTrips(id) || seats.existsByVehicleId(id)) {
            throw CatalogGuard.conflict("VEHICLE_IN_USE", "Deactivate a referenced vehicle instead of deleting it");
        }
        vehicles.delete(vehicle);
        vehicles.flush();
    }

    public PageResponse<SeatResponse> seats(long vehicleId, int page, int size) {
        find(vehicleId);
        Page<Seat> result = seats.findByVehicleId(vehicleId, CatalogGuard.page(page, size, "seatCode", "id"));
        List<SeatResponse> content = new ArrayList<>();
        for (Seat seat : result.getContent()) {
            content.add(SeatResponse.from(seat));
        }
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public SeatResponse seat(long vehicleId, long seatId) {
        find(vehicleId);
        Seat seat = findSeat(vehicleId, seatId);
        return SeatResponse.from(seat);
    }

    @Transactional
    public SeatResponse createSeat(long vehicleId, SeatRequest request) {
        guard.lockWrites();
        guard.lockVehicleTrips(vehicleId);
        Vehicle vehicle = find(vehicleId);
        editableSeats(vehicleId);
        manualSeatsOnly(vehicle);
        uniqueSeat(vehicleId, request.seatCode(), -1L);
        Seat seat = new Seat(vehicle, request);
        seats.saveAndFlush(seat);
        return SeatResponse.from(seat);
    }

    @Transactional
    public SeatResponse updateSeat(long vehicleId, long seatId, SeatRequest request) {
        guard.lockWrites();
        guard.lockVehicleTrips(vehicleId);
        Vehicle vehicle = find(vehicleId);
        Seat seat = findSeat(vehicleId, seatId);
        editableSeats(vehicleId);
        manualSeatsOnly(vehicle);
        uniqueSeat(vehicleId, request.seatCode(), seatId);
        seat.update(request);
        seats.saveAndFlush(seat);
        return SeatResponse.from(seat);
    }

    @Transactional
    public void deleteSeat(long vehicleId, long seatId) {
        guard.lockWrites();
        guard.lockVehicleTrips(vehicleId);
        Vehicle vehicle = find(vehicleId);
        Seat seat = findSeat(vehicleId, seatId);
        editableSeats(vehicleId);
        manualSeatsOnly(vehicle);
        seats.delete(seat);
        seats.flush();
    }

    private Vehicle find(long id) {
        CatalogGuard.positiveId(id);
        Optional<Vehicle> result = vehicles.findById(id);
        if (result.isEmpty()) {
            throw CatalogGuard.notFound("Vehicle");
        }
        return result.get();
    }

    private Seat findSeat(long vehicleId, long id) {
        CatalogGuard.positiveId(id);
        Optional<Seat> result = seats.findById(id);
        if (result.isEmpty()) {
            throw CatalogGuard.notFound("Seat");
        }
        Seat seat = result.get();
        if (!seat.getVehicle().getId().equals(vehicleId)) {
            throw CatalogGuard.notFound("Seat");
        }
        return seat;
    }

    private void uniqueCode(String code, long id) {
        if (vehicles.existsByVehicleCodeIgnoreCaseAndIdNot(code, id)) {
            throw CatalogGuard.conflict("VEHICLE_CODE_EXISTS", "Vehicle code already exists");
        }
    }

    private void uniqueSeat(long vehicleId, String code, long id) {
        if (seats.existsByVehicleIdAndSeatCodeIgnoreCaseAndIdNot(vehicleId, code, id)) {
            throw CatalogGuard.conflict("SEAT_CODE_EXISTS", "Seat code already exists in this vehicle");
        }
    }

    private boolean hasTrips(long id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM trips WHERE vehicle_id = ?)";
        Boolean result = jdbc.queryForObject(sql, Boolean.class, id);
        return Boolean.TRUE.equals(result);
    }

    private void editableSeats(long id) {
        if (guard.vehicleHasBookings(id)) {
            throw CatalogGuard.conflict("SEATS_IN_USE", "Seat layout of a vehicle with booking history cannot change");
        }
    }

    private void manualSeatsOnly(Vehicle vehicle) {
        if (!"CUSTOM".equals(vehicle.getSeatLayout())) {
            throw CatalogGuard.conflict("STANDARD_LAYOUT_LOCKED", "Standard layouts must retain their fixed capacity and positions");
        }
    }
}

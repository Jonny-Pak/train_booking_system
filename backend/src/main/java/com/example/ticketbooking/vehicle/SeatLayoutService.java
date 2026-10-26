package com.example.ticketbooking.vehicle;

import com.example.ticketbooking.common.CatalogGuard;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatLayoutService {
    private final VehicleRepository vehicles;
    private final SeatRepository seats;
    private final CatalogGuard guard;

    public SeatLayoutService(VehicleRepository vehicles, SeatRepository seats, CatalogGuard guard) {
        this.vehicles = vehicles;
        this.seats = seats;
        this.guard = guard;
    }

    @Transactional
    public VehicleResponse generate(long vehicleId) {
        CatalogGuard.positiveId(vehicleId);
        guard.lockWrites();
        guard.lockVehicleTrips(vehicleId);
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> CatalogGuard.notFound("Vehicle"));
        String layout = vehicle.getVehicleType() == VehicleType.BUS ? "BUS_SLEEPER_34" : "TRAIN_11";
        // Already generated layouts keep their IDs, including when bookings exist.
        if (layout.equals(vehicle.getSeatLayout())) return VehicleResponse.from(vehicle);
        if (guard.vehicleHasBookings(vehicleId)) {
            throw CatalogGuard.conflict("SEATS_IN_USE", "Create a new vehicle; existing booking seat IDs must be preserved");
        }
        seats.deleteAll(seats.findByVehicleIdOrderBySeatCodeAscIdAsc(vehicleId));
        seats.flush();
        List<Seat> generated = new ArrayList<>();
        if (vehicle.getVehicleType() == VehicleType.BUS) {
            for (int position = 1; position <= 34; position++) {
                Seat seat = new Seat(vehicle, new SeatRequest("A" + position, "BUS_SLEEPER"));
                seat.setPosition(0, null, (position - 1) / 17 + 1, position);
                generated.add(seat);
            }
        } else {
            for (int coach = 1; coach <= 11; coach++) {
                int capacity = coach <= 2 ? 56 : coach <= 6 ? 28 : 42;
                int bedsPerRoom = coach <= 6 ? 4 : 6;
                String seatClass = coach <= 2 ? "SEATED" : coach <= 6 ? "SLEEPER_4" : "SLEEPER_6";
                for (int position = 1; position <= capacity; position++) {
                    Seat seat = new Seat(vehicle, new SeatRequest("T%02d-%02d".formatted(coach, position), seatClass));
                    Integer compartment = coach <= 2 ? null : (position - 1) / bedsPerRoom + 1;
                    int deck = coach <= 2 ? 1 : ((position - 1) % bedsPerRoom) / 2 + 1;
                    seat.setPosition(coach, compartment, deck, position);
                    generated.add(seat);
                }
            }
        }
        seats.saveAll(generated);
        seats.flush();
        vehicle.setSeatLayout(layout);
        vehicles.saveAndFlush(vehicle);
        return VehicleResponse.from(vehicle);
    }
}

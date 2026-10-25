package com.example.ticketbooking.vehicle;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    Page<Seat> findByVehicleId(long vehicleId, Pageable pageable);
    List<Seat> findByVehicleIdOrderBySeatCodeAscIdAsc(long vehicleId);
    boolean existsByVehicleIdAndSeatCodeIgnoreCaseAndIdNot(long vehicleId, String code, Long id);
    boolean existsByVehicleId(long vehicleId);
}

package com.example.ticketbooking.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByVehicleCodeIgnoreCaseAndIdNot(String code, Long id);
}

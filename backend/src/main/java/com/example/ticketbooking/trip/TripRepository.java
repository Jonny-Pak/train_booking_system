package com.example.ticketbooking.trip;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @Override
    @EntityGraph(attributePaths = {"route", "vehicle"})
    Optional<Trip> findById(Long id);

    @Query("""
            select count(t) > 0 from Trip t where t.vehicle.id = :vehicleId and t.id <> :excludeId
            and t.status <> com.example.ticketbooking.trip.TripStatus.CANCELLED
            and t.departureAt < :arrival and t.arrivalAt > :departure
            """)
    boolean overlaps(long vehicleId, long excludeId, Instant departure, Instant arrival);
}

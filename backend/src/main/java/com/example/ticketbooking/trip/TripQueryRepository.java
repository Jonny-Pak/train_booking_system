package com.example.ticketbooking.trip;

import com.example.ticketbooking.vehicle.VehicleType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class TripQueryRepository {
    private final EntityManager entityManager;

    public TripQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<Trip> searchPublic(String origin, String destination, VehicleType vehicleType,
                                   Instant now, Instant dayStart, Instant dayEnd, Pageable pageable) {
        StringBuilder where = new StringBuilder("""
                where t.route.active = true
                  and t.vehicle.active = true
                  and t.status = :status
                  and t.departureAt > :now
                """);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", TripStatus.SCHEDULED);
        parameters.put("now", now);

        if (origin != null) {
            where.append(" and lower(t.route.origin) = :origin");
            parameters.put("origin", origin);
        }
        if (destination != null) {
            where.append(" and lower(t.route.destination) = :destination");
            parameters.put("destination", destination);
        }
        if (vehicleType != null) {
            where.append(" and t.vehicle.vehicleType = :vehicleType");
            parameters.put("vehicleType", vehicleType);
        }
        if (dayStart != null) {
            where.append(" and t.departureAt >= :dayStart and t.departureAt < :dayEnd");
            parameters.put("dayStart", dayStart);
            parameters.put("dayEnd", dayEnd);
        }

        return findPage(where.toString(), parameters, pageable);
    }

    public Page<Trip> searchAdmin(TripStatus status, Long routeId, Long vehicleId, Pageable pageable) {
        StringBuilder where = new StringBuilder("where 1 = 1");
        Map<String, Object> parameters = new HashMap<>();

        if (status != null) {
            where.append(" and t.status = :status");
            parameters.put("status", status);
        }
        if (routeId != null) {
            where.append(" and t.route.id = :routeId");
            parameters.put("routeId", routeId);
        }
        if (vehicleId != null) {
            where.append(" and t.vehicle.id = :vehicleId");
            parameters.put("vehicleId", vehicleId);
        }

        return findPage(where.toString(), parameters, pageable);
    }

    private Page<Trip> findPage(String where, Map<String, Object> parameters, Pageable pageable) {
        String select = "select t from Trip t join fetch t.route join fetch t.vehicle ";
        String sort = " order by t.departureAt, t.id";
        TypedQuery<Trip> query = entityManager.createQuery(select + where + sort, Trip.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(t) from Trip t " + where, Long.class);

        // Chỉ ghép các điều kiện cố định ở trên. Dữ liệu người dùng luôn truyền qua tham số.
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            query.setParameter(parameter.getKey(), parameter.getValue());
            countQuery.setParameter(parameter.getKey(), parameter.getValue());
        }

        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());

        List<Trip> trips = query.getResultList();
        long total = countQuery.getSingleResult();
        return new PageImpl<>(trips, pageable, total);
    }
}

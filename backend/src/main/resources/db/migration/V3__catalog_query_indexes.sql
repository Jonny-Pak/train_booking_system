CREATE INDEX idx_routes_endpoints ON routes (lower(origin), lower(destination));
CREATE INDEX idx_trips_public_departure ON trips (departure_at, id) WHERE status = 'SCHEDULED';
CREATE INDEX idx_trips_vehicle_interval ON trips (vehicle_id, departure_at, arrival_at) WHERE status <> 'CANCELLED';
CREATE INDEX idx_bookings_trip ON bookings (trip_id);

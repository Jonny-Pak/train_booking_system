-- Explicit opt-in demo seed for a dedicated demo database, AFTER Flyway migrations.
-- No accounts/passwords; no updates/deletes. Vehicles are visibly marked DEMO-P3-*.
BEGIN;
SELECT pg_advisory_xact_lock(713003);
DO $$
DECLARE
    route_bus BIGINT;
    route_train BIGINT;
    bus BIGINT;
    train BIGINT;
    departure TIMESTAMPTZ;
BEGIN
    IF EXISTS(SELECT 1 FROM bookings b JOIN trips t ON t.id = b.trip_id
              JOIN vehicles v ON v.id = t.vehicle_id WHERE v.vehicle_code LIKE 'DEMO-P3-%') THEN
        RAISE EXCEPTION 'Demo vehicles already have booking history; do not re-seed their seat layout';
    END IF;
    SELECT id INTO route_bus FROM routes WHERE origin = 'Hà Nội' AND destination = 'Hải Phòng' ORDER BY id LIMIT 1;
    IF route_bus IS NULL THEN
        INSERT INTO routes(origin,destination) VALUES ('Hà Nội','Hải Phòng') RETURNING id INTO route_bus;
    END IF;
    SELECT id INTO route_train FROM routes WHERE origin = 'TP Hồ Chí Minh' AND destination = 'Phan Thiết' ORDER BY id LIMIT 1;
    IF route_train IS NULL THEN
        INSERT INTO routes(origin,destination) VALUES ('TP Hồ Chí Minh','Phan Thiết') RETURNING id INTO route_train;
    END IF;
    SELECT id INTO bus FROM vehicles WHERE vehicle_code = 'DEMO-P3-BUS-01';
    IF bus IS NULL THEN
        INSERT INTO vehicles(vehicle_code,vehicle_type,display_name)
        VALUES ('DEMO-P3-BUS-01','BUS','[DEMO P3] Xe Hà Nội - Hải Phòng') RETURNING id INTO bus;
    END IF;
    SELECT id INTO train FROM vehicles WHERE vehicle_code = 'DEMO-P3-TRAIN-01';
    IF train IS NULL THEN
        INSERT INTO vehicles(vehicle_code,vehicle_type,display_name)
        VALUES ('DEMO-P3-TRAIN-01','TRAIN','[DEMO P3] Tàu Phan Thiết') RETURNING id INTO train;
    END IF;
    INSERT INTO seats(vehicle_id,seat_code,seat_class)
        SELECT vehicle, 'A' || seat, 'STANDARD' FROM unnest(ARRAY[bus,train]) AS vehicle CROSS JOIN generate_series(1,8) AS seat
        ON CONFLICT(vehicle_id,seat_code) DO NOTHING;
    departure := (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 7) + TIME '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh';
    IF NOT EXISTS(SELECT 1 FROM trips WHERE vehicle_id = bus) THEN
        INSERT INTO trips(route_id,vehicle_id,departure_at,arrival_at,base_price)
        VALUES (route_bus,bus,departure,departure + INTERVAL '2 hours',150000.00);
    END IF;
    IF NOT EXISTS(SELECT 1 FROM trips WHERE vehicle_id = train) THEN
        INSERT INTO trips(route_id,vehicle_id,departure_at,arrival_at,base_price)
        VALUES (route_train,train,departure + INTERVAL '1 hour',departure + INTERVAL '5 hours',220000.00);
    END IF;
END $$;
COMMIT;

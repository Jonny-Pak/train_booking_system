-- Run explicitly after Flyway migrations. Adds sample data only; never changes existing rows.
-- Dates follow Vietnam time. Repeating on the same day does not duplicate trips.
BEGIN;
SELECT pg_advisory_xact_lock(713003);
DO $$
DECLARE
    sample RECORD;
    route_id_value BIGINT;
    vehicle_id_value BIGINT;
    code TEXT;
    departure TIMESTAMPTZ;
    arrival TIMESTAMPTZ;
    sample_day DATE;
    day_number INTEGER;
    hour_number INTEGER;
    coach INTEGER;
    capacity INTEGER;
    beds_per_room INTEGER;
BEGIN
    FOR sample IN SELECT * FROM (VALUES
        ('BUS-01', 'BUS', 'Hà Nội', 'Hải Phòng', 150000, 2),
        ('BUS-02', 'BUS', 'Hà Nội', 'Ninh Bình', 180000, 3),
        ('BUS-03', 'BUS', 'TP Hồ Chí Minh', 'Vũng Tàu', 200000, 3),
        ('BUS-04', 'BUS', 'TP Hồ Chí Minh', 'Đà Lạt', 350000, 6),
        ('TRAIN-01', 'TRAIN', 'Hà Nội', 'Đà Nẵng', 850000, 16),
        ('TRAIN-02', 'TRAIN', 'TP Hồ Chí Minh', 'Phan Thiết', 220000, 4)
    ) AS data(suffix, vehicle_type, origin, destination, price, duration_hours)
    LOOP
        SELECT id INTO route_id_value FROM routes
        WHERE origin = sample.origin AND destination = sample.destination AND active
        ORDER BY id LIMIT 1;
        IF route_id_value IS NULL THEN
            INSERT INTO routes(origin, destination, active)
            VALUES(sample.origin, sample.destination, TRUE) RETURNING id INTO route_id_value;
        END IF;

        code := 'SAMPLE-' || sample.suffix;
        SELECT id INTO vehicle_id_value FROM vehicles WHERE vehicle_code = code;
        IF vehicle_id_value IS NULL THEN
            INSERT INTO vehicles(vehicle_code, vehicle_type, display_name, active, seat_layout)
            VALUES(code, sample.vehicle_type, '[MẪU] ' || sample.origin || ' - ' || sample.destination, TRUE,
                CASE WHEN sample.vehicle_type = 'BUS' THEN 'BUS_SLEEPER_34' ELSE 'TRAIN_11' END)
            RETURNING id INTO vehicle_id_value;
            IF sample.vehicle_type = 'BUS' THEN
                INSERT INTO seats(vehicle_id, seat_code, seat_class, coach_number, deck_number, position_number)
                SELECT vehicle_id_value, 'A' || number, 'BUS_SLEEPER', 0, (number-1)/17+1, number FROM generate_series(1,34) AS number;
            ELSE
                FOR coach IN 1..11 LOOP
                    capacity := CASE WHEN coach <= 2 THEN 56 WHEN coach <= 6 THEN 28 ELSE 42 END;
                    beds_per_room := CASE WHEN coach <= 6 THEN 4 ELSE 6 END;
                    INSERT INTO seats(vehicle_id, seat_code, seat_class, coach_number, compartment_number, deck_number, position_number)
                    SELECT vehicle_id_value, 'T' || lpad(coach::text,2,'0') || '-' || lpad(number::text,2,'0'),
                        CASE WHEN coach <= 2 THEN 'SEATED' WHEN coach <= 6 THEN 'SLEEPER_4' ELSE 'SLEEPER_6' END,
                        coach, CASE WHEN coach <= 2 THEN NULL ELSE (number - 1) / beds_per_room + 1 END,
                        CASE WHEN coach <= 2 THEN 1 ELSE ((number - 1) % beds_per_room) / 2 + 1 END, number
                    FROM generate_series(1,capacity) AS number;
                END LOOP;
            END IF;
        END IF;
        IF NOT EXISTS(SELECT 1 FROM vehicles WHERE id = vehicle_id_value
            AND vehicle_type = sample.vehicle_type AND active AND display_name LIKE '[MẪU] %') THEN
            RAISE EXCEPTION 'Reserved sample code % is used by another or inactive vehicle', code;
        END IF;

        -- Each bus runs at 08:00 and 16:00. Each train runs at 09:00.
        FOR day_number IN 1..7 LOOP
            sample_day := (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + day_number;
            FOR hour_number IN 0..(CASE WHEN sample.vehicle_type = 'BUS' THEN 1 ELSE 0 END) LOOP
                departure := (sample_day + make_time(
                    CASE WHEN sample.vehicle_type = 'BUS' THEN 8 + hour_number * 8 ELSE 9 END, 0, 0))
                    AT TIME ZONE 'Asia/Ho_Chi_Minh';
                arrival := departure + make_interval(hours => sample.duration_hours);
                IF EXISTS(SELECT 1 FROM trips WHERE vehicle_id = vehicle_id_value AND departure_at = departure) THEN
                    CONTINUE;
                END IF;
                IF EXISTS(SELECT 1 FROM trips WHERE vehicle_id = vehicle_id_value AND status <> 'CANCELLED'
                    AND departure_at < arrival AND arrival_at > departure) THEN
                    RAISE EXCEPTION 'Sample vehicle % has a conflicting schedule', code;
                END IF;
                INSERT INTO trips(route_id, vehicle_id, departure_at, arrival_at, base_price, status)
                VALUES(route_id_value, vehicle_id_value, departure, arrival, sample.price, 'SCHEDULED');
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
COMMIT;

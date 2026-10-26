-- Explicit demo calendar, Vietnam time, inclusive 27/10/2026–10/11/2026.
-- Additive and repeatable: never updates/deletes existing vehicles, seats or bookings.
-- Run after Flyway V10. This is demo data, not a real operator timetable.
BEGIN;
-- Same lock as Admin catalog writes and the existing sample-data script.
SELECT pg_advisory_xact_lock(713003);
DO $$
DECLARE
    sample RECORD;
    route_id_value BIGINT;
    vehicle_id_value BIGINT;
    vehicle_code_value TEXT;
    sample_day DATE;
    departure TIMESTAMPTZ;
    arrival TIMESTAMPTZ;
    coach INTEGER;
    capacity INTEGER;
    beds_per_room INTEGER;
BEGIN
    FOR sample IN SELECT * FROM (VALUES
        ('BUS-01',   'BUS',   'Hà Nội',         'Hải Phòng',  '06:00'::time, 120, 150000),
        ('BUS-02',   'BUS',   'Hà Nội',         'Ninh Bình',  '08:30'::time, 150, 180000),
        ('BUS-03',   'BUS',   'Hà Nội',         'Sa Pa',      '11:00'::time, 360, 320000),
        ('BUS-04',   'BUS',   'TP Hồ Chí Minh', 'Vũng Tàu',   '15:30'::time, 180, 200000),
        ('BUS-05',   'BUS',   'TP Hồ Chí Minh', 'Đà Lạt',     '20:00'::time, 420, 350000),
        ('TRAIN-01', 'TRAIN', 'Hà Nội',         'Hải Phòng',  '07:00'::time, 150, 120000),
        ('TRAIN-02', 'TRAIN', 'Hà Nội',         'Ninh Bình',  '09:30'::time, 150, 170000),
        ('TRAIN-03', 'TRAIN', 'Hà Nội',         'Vinh',       '13:00'::time, 360, 350000),
        ('TRAIN-04', 'TRAIN', 'TP Hồ Chí Minh', 'Phan Thiết', '17:30'::time, 240, 220000),
        ('TRAIN-05', 'TRAIN', 'TP Hồ Chí Minh', 'Nha Trang',  '21:30'::time, 510, 450000)
    ) AS data(suffix, vehicle_type, origin, destination, departure_time, duration_minutes, price)
    LOOP
        SELECT id INTO route_id_value FROM routes
        WHERE origin = sample.origin AND destination = sample.destination AND active
        ORDER BY id LIMIT 1;
        IF route_id_value IS NULL THEN
            INSERT INTO routes(origin, destination, active)
            VALUES(sample.origin, sample.destination, TRUE) RETURNING id INTO route_id_value;
        END IF;

        vehicle_code_value := 'SAMPLE-2026-' || sample.suffix;
        SELECT id INTO vehicle_id_value FROM vehicles WHERE vehicle_code = vehicle_code_value;
        IF vehicle_id_value IS NULL THEN
            INSERT INTO vehicles(vehicle_code, vehicle_type, display_name, active, seat_layout)
            VALUES(vehicle_code_value, sample.vehicle_type,
                '[MẪU 2026] ' || sample.origin || ' - ' || sample.destination, TRUE,
                CASE WHEN sample.vehicle_type = 'BUS' THEN 'BUS_SLEEPER_34' ELSE 'TRAIN_11' END)
            RETURNING id INTO vehicle_id_value;
            IF sample.vehicle_type = 'BUS' THEN
                INSERT INTO seats(vehicle_id, seat_code, seat_class, coach_number, deck_number, position_number)
                SELECT vehicle_id_value, 'A' || number, 'BUS_SLEEPER', 0, (number-1)/17+1, number
                FROM generate_series(1,34) AS number;
            ELSE
                FOR coach IN 1..11 LOOP
                    capacity := CASE WHEN coach <= 2 THEN 56 WHEN coach <= 6 THEN 28 ELSE 42 END;
                    beds_per_room := CASE WHEN coach <= 6 THEN 4 ELSE 6 END;
                    INSERT INTO seats(vehicle_id, seat_code, seat_class, coach_number, compartment_number, deck_number, position_number)
                    SELECT vehicle_id_value, 'T' || lpad(coach::text,2,'0') || '-' || lpad(number::text,2,'0'),
                        CASE WHEN coach <= 2 THEN 'SEATED' WHEN coach <= 6 THEN 'SLEEPER_4' ELSE 'SLEEPER_6' END,
                        coach, CASE WHEN coach <= 2 THEN NULL ELSE (number-1)/beds_per_room+1 END,
                        CASE WHEN coach <= 2 THEN 1 ELSE ((number-1)%beds_per_room)/2+1 END, number
                    FROM generate_series(1,capacity) AS number;
                END LOOP;
            END IF;
        END IF;

        -- Reuse only this script's intact, active demo fleet. Do not repair/overwrite a vehicle.
        IF NOT EXISTS(SELECT 1 FROM vehicles WHERE id = vehicle_id_value AND active
            AND vehicle_type = sample.vehicle_type AND display_name LIKE '[MẪU 2026] %'
            AND seat_layout = CASE WHEN sample.vehicle_type = 'BUS' THEN 'BUS_SLEEPER_34' ELSE 'TRAIN_11' END) THEN
            RAISE EXCEPTION 'Reserved sample vehicle % has been changed; review before seeding', vehicle_code_value;
        END IF;
        IF sample.vehicle_type = 'BUS' THEN
            IF (SELECT count(*) FROM seats WHERE vehicle_id = vehicle_id_value) <> 34
                OR (SELECT count(*) FROM seats WHERE vehicle_id = vehicle_id_value AND seat_class = 'BUS_SLEEPER' AND coach_number = 0) <> 34 THEN
                RAISE EXCEPTION 'Sample sleeper bus % must have 34 beds', vehicle_code_value;
            END IF;
        ELSE
            IF (SELECT count(*) FROM seats WHERE vehicle_id = vehicle_id_value) <> 434 THEN
                RAISE EXCEPTION 'Sample train % must have 434 places', vehicle_code_value;
            END IF;
            FOR coach IN 1..11 LOOP
                capacity := CASE WHEN coach <= 2 THEN 56 WHEN coach <= 6 THEN 28 ELSE 42 END;
                IF (SELECT count(*) FROM seats WHERE vehicle_id = vehicle_id_value AND coach_number = coach) <> capacity THEN
                    RAISE EXCEPTION 'Sample train % coach % is inconsistent', vehicle_code_value, coach;
                END IF;
            END LOOP;
        END IF;

        FOR sample_day IN SELECT generate_series(DATE '2026-10-27', DATE '2026-11-10', INTERVAL '1 day')::date LOOP
            departure := (sample_day + sample.departure_time) AT TIME ZONE 'Asia/Ho_Chi_Minh';
            arrival := departure + make_interval(mins => sample.duration_minutes);
            IF EXISTS(SELECT 1 FROM trips WHERE vehicle_id = vehicle_id_value AND departure_at = departure
                AND route_id = route_id_value AND arrival_at = arrival AND base_price = sample.price AND status = 'SCHEDULED') THEN
                CONTINUE;
            END IF;
            IF EXISTS(SELECT 1 FROM trips WHERE vehicle_id = vehicle_id_value AND departure_at = departure)
                OR EXISTS(SELECT 1 FROM trips WHERE vehicle_id = vehicle_id_value AND status <> 'CANCELLED'
                    AND departure_at < arrival AND arrival_at > departure) THEN
                RAISE EXCEPTION 'Sample vehicle % has a conflicting/changed trip on %', vehicle_code_value, sample_day;
            END IF;
            INSERT INTO trips(route_id, vehicle_id, departure_at, arrival_at, base_price, status)
            VALUES(route_id_value, vehicle_id_value, departure, arrival, sample.price, 'SCHEDULED');
        END LOOP;
    END LOOP;
END $$;
COMMIT;

-- Each of the 15 days must show 5 buses, 5 trains, 5 distinct departure times per type.
SELECT (t.departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS departure_date,
    v.vehicle_type, count(*) AS trips,
    count(DISTINCT (t.departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time) AS departure_times
FROM trips t JOIN vehicles v ON v.id = t.vehicle_id
WHERE v.vehicle_code LIKE 'SAMPLE-2026-%'
    AND t.departure_at >= TIMESTAMP '2026-10-27 00:00' AT TIME ZONE 'Asia/Ho_Chi_Minh'
    AND t.departure_at < TIMESTAMP '2026-11-11 00:00' AT TIME ZONE 'Asia/Ho_Chi_Minh'
    AND t.status = 'SCHEDULED'
GROUP BY 1,2 ORDER BY 1,2;

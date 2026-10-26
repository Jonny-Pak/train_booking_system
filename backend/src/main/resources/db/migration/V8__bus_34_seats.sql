-- Extend existing standard buses without deleting or renumbering any booked seat.
DO $$
BEGIN
    IF EXISTS (
        SELECT v.id FROM vehicles v LEFT JOIN seats s ON s.vehicle_id=v.id
        WHERE v.seat_layout='BUS_16' GROUP BY v.id
        HAVING count(s.id) <> 16 OR count(s.id) FILTER (
            WHERE s.coach_number=0 AND s.deck_number=1 AND s.position_number BETWEEN 1 AND 16) <> 16
    ) THEN
        RAISE EXCEPTION 'BUS_16 layout is inconsistent; review before extending to 34 seats';
    END IF;
END $$;

ALTER TABLE vehicles DROP CONSTRAINT chk_vehicle_seat_layout;
ALTER TABLE seats DROP CONSTRAINT chk_seat_position;
ALTER TABLE seats ADD CONSTRAINT chk_seat_position CHECK (
    (coach_number IS NULL AND compartment_number IS NULL AND deck_number IS NULL AND position_number IS NULL)
    OR (coach_number IS NOT NULL AND deck_number IS NOT NULL AND position_number IS NOT NULL AND (
        (coach_number = 0 AND compartment_number IS NULL AND deck_number = 1 AND position_number BETWEEN 1 AND 34 AND seat_class = 'STANDARD')
        OR (coach_number BETWEEN 1 AND 2 AND compartment_number IS NULL AND deck_number = 1 AND position_number BETWEEN 1 AND 56 AND seat_class = 'SEATED')
        OR (coach_number BETWEEN 3 AND 6 AND compartment_number IS NOT NULL AND compartment_number BETWEEN 1 AND 7
            AND position_number BETWEEN 1 AND 28 AND seat_class = 'SLEEPER_4'
            AND compartment_number = (position_number - 1) / 4 + 1 AND deck_number = ((position_number - 1) % 4) / 2 + 1)
        OR (coach_number BETWEEN 7 AND 11 AND compartment_number IS NOT NULL AND compartment_number BETWEEN 1 AND 7
            AND position_number BETWEEN 1 AND 42 AND seat_class = 'SLEEPER_6'
            AND compartment_number = (position_number - 1) / 6 + 1 AND deck_number = ((position_number - 1) % 6) / 2 + 1)
    ))
);

INSERT INTO seats(vehicle_id,seat_code,seat_class,coach_number,deck_number,position_number)
SELECT v.id, 'A' || position, 'STANDARD', 0, 1, position
FROM vehicles v CROSS JOIN generate_series(17,34) AS position WHERE v.seat_layout='BUS_16';
UPDATE vehicles SET seat_layout='BUS_34' WHERE seat_layout='BUS_16';
ALTER TABLE vehicles ADD CONSTRAINT chk_vehicle_seat_layout CHECK (
    seat_layout='CUSTOM' OR (seat_layout='BUS_34' AND vehicle_type='BUS')
    OR (seat_layout='TRAIN_11' AND vehicle_type='TRAIN')
);

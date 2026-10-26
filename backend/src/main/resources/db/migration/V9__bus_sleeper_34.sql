-- Correct the bus type while retaining all seat IDs, codes and booking references.
DO $$
BEGIN
    IF EXISTS (
        SELECT v.id FROM vehicles v LEFT JOIN seats s ON s.vehicle_id=v.id
        WHERE v.seat_layout='BUS_34' GROUP BY v.id
        HAVING count(s.id) <> 34 OR count(s.id) FILTER (
            WHERE s.coach_number=0 AND s.deck_number=1 AND s.position_number BETWEEN 1 AND 34 AND s.seat_class='STANDARD') <> 34
    ) THEN
        RAISE EXCEPTION 'BUS_34 layout is inconsistent; review before converting to sleeper';
    END IF;
END $$;
ALTER TABLE vehicles DROP CONSTRAINT chk_vehicle_seat_layout;
ALTER TABLE seats DROP CONSTRAINT chk_seat_position;

UPDATE seats s SET seat_class='BUS_SLEEPER', deck_number=(s.position_number-1)/17+1
FROM vehicles v WHERE s.vehicle_id=v.id AND v.seat_layout='BUS_34';
UPDATE vehicles SET seat_layout='BUS_SLEEPER_34' WHERE seat_layout='BUS_34';

ALTER TABLE vehicles ADD CONSTRAINT chk_vehicle_seat_layout CHECK (
    seat_layout='CUSTOM' OR (seat_layout='BUS_SLEEPER_34' AND vehicle_type='BUS')
    OR (seat_layout='TRAIN_11' AND vehicle_type='TRAIN')
);
ALTER TABLE seats ADD CONSTRAINT chk_seat_position CHECK (
    (coach_number IS NULL AND compartment_number IS NULL AND deck_number IS NULL AND position_number IS NULL)
    OR (coach_number IS NOT NULL AND deck_number IS NOT NULL AND position_number IS NOT NULL AND (
        (coach_number=0 AND compartment_number IS NULL AND position_number BETWEEN 1 AND 34
            AND seat_class='BUS_SLEEPER' AND deck_number=(position_number-1)/17+1)
        OR (coach_number BETWEEN 1 AND 2 AND compartment_number IS NULL AND deck_number=1 AND position_number BETWEEN 1 AND 56 AND seat_class='SEATED')
        OR (coach_number BETWEEN 3 AND 6 AND compartment_number IS NOT NULL AND compartment_number BETWEEN 1 AND 7
            AND position_number BETWEEN 1 AND 28 AND seat_class='SLEEPER_4'
            AND compartment_number=(position_number-1)/4+1 AND deck_number=((position_number-1)%4)/2+1)
        OR (coach_number BETWEEN 7 AND 11 AND compartment_number IS NOT NULL AND compartment_number BETWEEN 1 AND 7
            AND position_number BETWEEN 1 AND 42 AND seat_class='SLEEPER_6'
            AND compartment_number=(position_number-1)/6+1 AND deck_number=((position_number-1)%6)/2+1)
    ))
);

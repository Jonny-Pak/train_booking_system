ALTER TABLE vehicles ADD COLUMN seat_layout VARCHAR(20) NOT NULL DEFAULT 'CUSTOM';
ALTER TABLE vehicles ADD CONSTRAINT chk_vehicle_seat_layout CHECK (
    seat_layout = 'CUSTOM' OR (seat_layout = 'BUS_16' AND vehicle_type = 'BUS')
    OR (seat_layout = 'TRAIN_11' AND vehicle_type = 'TRAIN')
);
ALTER TABLE seats ADD COLUMN coach_number INTEGER;
ALTER TABLE seats ADD COLUMN compartment_number INTEGER;
ALTER TABLE seats ADD COLUMN deck_number INTEGER;
ALTER TABLE seats ADD COLUMN position_number INTEGER;
ALTER TABLE seats ADD CONSTRAINT chk_seat_position CHECK (
    (coach_number IS NULL AND compartment_number IS NULL AND deck_number IS NULL AND position_number IS NULL)
    OR (coach_number IS NOT NULL AND deck_number IS NOT NULL AND position_number IS NOT NULL AND (
        (coach_number = 0 AND compartment_number IS NULL AND deck_number = 1 AND position_number BETWEEN 1 AND 16 AND seat_class = 'STANDARD')
        OR (coach_number BETWEEN 1 AND 2 AND compartment_number IS NULL AND deck_number = 1 AND position_number BETWEEN 1 AND 56 AND seat_class = 'SEATED')
        OR (coach_number BETWEEN 3 AND 6 AND compartment_number IS NOT NULL AND compartment_number BETWEEN 1 AND 7
            AND position_number BETWEEN 1 AND 28 AND seat_class = 'SLEEPER_4'
            AND compartment_number = (position_number - 1) / 4 + 1 AND deck_number = ((position_number - 1) % 4) / 2 + 1)
        OR (coach_number BETWEEN 7 AND 11 AND compartment_number IS NOT NULL AND compartment_number BETWEEN 1 AND 7
            AND position_number BETWEEN 1 AND 42 AND seat_class = 'SLEEPER_6'
            AND compartment_number = (position_number - 1) / 6 + 1 AND deck_number = ((position_number - 1) % 6) / 2 + 1)
    ))
);
CREATE UNIQUE INDEX uq_seat_layout_position ON seats(vehicle_id, coach_number, position_number)
    WHERE coach_number IS NOT NULL;

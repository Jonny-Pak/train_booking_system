ALTER TABLE payments ADD COLUMN expires_at TIMESTAMPTZ;
UPDATE payments p SET expires_at = b.hold_expires_at FROM bookings b WHERE b.id = p.booking_id;
ALTER TABLE payments ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE payments ADD COLUMN response_code VARCHAR(2);
ALTER TABLE payments ADD COLUMN transaction_status VARCHAR(2);
ALTER TABLE payments ADD COLUMN reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payments ADD COLUMN reconciliation_reason VARCHAR(80);
ALTER TABLE payments ADD CONSTRAINT chk_payment_reconciliation CHECK (
    reconciliation_required = (reconciliation_reason IS NOT NULL)
);
CREATE UNIQUE INDEX uq_payment_pending_booking ON payments(booking_id) WHERE status = 'PENDING';
CREATE INDEX idx_payment_booking ON payments(booking_id, id DESC);

-- Paginated customer history and admin history. Keep all booking records.
CREATE INDEX idx_booking_user_history ON bookings(user_id, created_at DESC, id DESC);
CREATE INDEX idx_booking_history ON bookings(created_at DESC, id DESC);

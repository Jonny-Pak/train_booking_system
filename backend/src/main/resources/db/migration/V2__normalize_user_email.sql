-- Fail atomically if existing accounts collide after normalization; never merge/delete users.
UPDATE users SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));
CREATE UNIQUE INDEX uq_users_email_normalized ON users (lower(btrim(email)));
ALTER TABLE users ADD CONSTRAINT chk_users_email_normalized
    CHECK (email = lower(btrim(email)));

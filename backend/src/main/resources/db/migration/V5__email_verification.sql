-- Preserve access for accounts created before email verification was introduced.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;
ALTER TABLE users ADD COLUMN verification_token_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN verification_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN verification_sent_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_users_verification_token ON users(verification_token_hash)
    WHERE verification_token_hash IS NOT NULL;
ALTER TABLE users ADD CONSTRAINT chk_users_verification_token CHECK (
    (verification_token_hash IS NULL AND verification_expires_at IS NULL)
    OR (NOT email_verified AND verification_token_hash IS NOT NULL AND verification_expires_at IS NOT NULL)
);

-- Add email verification fields to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_otp VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_otp_expiry TIMESTAMP;

-- Set existing users as verified (they registered before this feature)
UPDATE users SET email_verified = true WHERE email_verified IS NULL;

-- Make email_verified NOT NULL after setting defaults
ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;

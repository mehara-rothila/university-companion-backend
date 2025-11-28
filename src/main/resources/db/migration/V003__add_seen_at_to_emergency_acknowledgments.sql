-- Add seen_at column to emergency_notification_acknowledgments table
ALTER TABLE emergency_notification_acknowledgments
ADD COLUMN IF NOT EXISTS seen_at TIMESTAMP;

-- Update has_seen based on existing data
-- If acknowledged_at exists, assume it was seen (for backward compatibility)
UPDATE emergency_notification_acknowledgments
SET has_seen = true, seen_at = acknowledged_at
WHERE acknowledged_at IS NOT NULL AND has_seen = false;

-- If dismissed_at exists but no acknowledged_at, assume it was seen when dismissed
UPDATE emergency_notification_acknowledgments
SET has_seen = true, seen_at = dismissed_at
WHERE dismissed_at IS NOT NULL AND acknowledged_at IS NULL AND has_seen = false;

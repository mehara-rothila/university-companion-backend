-- Drop the old constraint
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

-- Add the new constraint that includes EMERGENCY
ALTER TABLE notifications
ADD CONSTRAINT notifications_type_check
CHECK (type IN ('GENERAL', 'ACADEMIC', 'FINANCIAL_AID', 'LOST_FOUND', 'WELLNESS', 'DINING', 'LIBRARY', 'SOCIAL', 'SYSTEM', 'EMERGENCY'));

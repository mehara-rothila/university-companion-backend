-- Fix lost_found_items with null user_id by assigning them to Mehara Rothila
-- First, get the user_id for mehararothila04@gmail.com and update items without an owner

UPDATE lost_found_items
SET user_id = (SELECT id FROM users WHERE email = 'mehararothila04@gmail.com' LIMIT 1)
WHERE user_id IS NULL
  AND EXISTS (SELECT 1 FROM users WHERE email = 'mehararothila04@gmail.com');

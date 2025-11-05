-- Fix invalid LeaveType enum values in the database
-- This script updates any invalid enum values to match the Java enum constants

-- Update "Sick Leave" to "SICK"
UPDATE leave_requests 
SET leave_type = 'SICK' 
WHERE leave_type = 'Sick Leave' OR leave_type = 'SICK_LEAVE' OR leave_type = 'Sick Leave';

-- Update any other variations
UPDATE leave_requests 
SET leave_type = UPPER(REPLACE(leave_type, ' ', '_'))
WHERE leave_type LIKE '% %';

-- Verify valid enum values:
-- SICK, ANNUAL, CASUAL, EMERGENCY, OTHER, UNPAID, STUDY

-- Check current values
SELECT DISTINCT leave_type FROM leave_requests;


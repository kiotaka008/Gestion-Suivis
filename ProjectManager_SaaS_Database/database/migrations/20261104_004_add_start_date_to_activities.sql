BEGIN;

ALTER TABLE public.activities
ADD COLUMN IF NOT EXISTS start_date DATE;

COMMENT ON COLUMN public.activities.start_date IS
'Date when the activity is planned to start';

COMMIT;
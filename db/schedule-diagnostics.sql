-- ============================================================================
-- GYMLET schedule diagnostics (READ ONLY)
-- ----------------------------------------------------------------------------
-- Run these against Supabase (SQL editor) BEFORE and AFTER the next backend
-- deploy. Section A only reads. Section B shows what the boot migration is
-- expected to change. Section C is optional manual verification of the unique
-- weekday index. Nothing here is executed by the application.
-- ============================================================================


-- ============================================================================
-- SECTION A — READ-ONLY DIAGNOSTICS
-- ============================================================================

-- A1. Required columns exist (expect 8 rows).
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
        (table_name = 'workout_day'     AND column_name IN ('rest_day','plan_id','weekday'))
     OR (table_name = 'workout_session' AND column_name = 'workout_day_name_snapshot')
     OR (table_name = 'workout_exercise' AND column_name IN ('target_rir','rest_seconds'))
     OR (table_name = 'app_user'        AND column_name IN ('active_plan_id','training_profile'))
      )
ORDER BY table_name, column_name;

-- A2. Duplicate (user_id, plan_id, weekday) — the core corruption. Expect 0 rows
--     after the migration has run.
SELECT user_id, plan_id, weekday, COUNT(*) AS rows_on_weekday,
       string_agg(id::text, ', ' ORDER BY id) AS row_ids
FROM workout_day
WHERE weekday IS NOT NULL
GROUP BY user_id, plan_id, weekday
HAVING COUNT(*) > 1
ORDER BY user_id, plan_id, weekday;

-- A3. Missing weekdays for otherwise explicit plans (1..7 with a gap).
WITH plans AS (
    SELECT DISTINCT user_id, plan_id
    FROM workout_day
    WHERE plan_id IS NOT NULL AND user_id IS NOT NULL
),
expected AS (
    SELECT p.user_id, p.plan_id, w AS weekday
    FROM plans p CROSS JOIN generate_series(1, 7) AS w
)
SELECT e.user_id, e.plan_id, e.weekday AS missing_weekday
FROM expected e
LEFT JOIN workout_day d
       ON d.user_id = e.user_id AND d.plan_id = e.plan_id AND d.weekday = e.weekday
WHERE d.id IS NULL
ORDER BY e.user_id, e.plan_id, e.weekday;

-- A4. Rows named "Rest" that are NOT flagged rest_day (lost their flag). These are
--     the "Rest but rest_day=false" rows; the reserved-slot ones are auto-normalised.
SELECT id, user_id, plan_id, weekday, day_number, name, rest_day
FROM workout_day
WHERE lower(trim(name)) = 'rest' AND rest_day = false
ORDER BY user_id, plan_id, id;

-- A5. Rows flagged rest_day that are not named "Rest".
SELECT id, user_id, plan_id, weekday, day_number, name, rest_day
FROM workout_day
WHERE rest_day = true AND lower(trim(coalesce(name,''))) <> 'rest'
ORDER BY user_id, plan_id, id;

-- A6. Weekdays outside the valid 1..7 range.
SELECT id, user_id, plan_id, weekday, day_number, name, rest_day
FROM workout_day
WHERE weekday IS NOT NULL AND (weekday < 1 OR weekday > 7)
ORDER BY user_id, plan_id, id;

-- A7. day_number convention conflicts: a real workout in the reserved rest range
--     (day_number = 50 + weekday is reserved for rest rows), or a rest row outside it.
SELECT id, user_id, plan_id, weekday, day_number, name, rest_day,
       CASE
           WHEN rest_day = false AND day_number >= 50 AND day_number <> 6
               THEN 'WORKOUT_IN_RESERVED_REST_RANGE'
           WHEN rest_day = true AND weekday IS NOT NULL AND day_number <> 50 + weekday
               THEN 'REST_OUTSIDE_RESERVED_SLOT'
           ELSE 'ok'
       END AS convention_conflict
FROM workout_day
WHERE (rest_day = false AND day_number >= 50 AND day_number <> 6)
   OR (rest_day = true AND weekday IS NOT NULL AND day_number <> 50 + weekday)
ORDER BY user_id, plan_id, id;

-- A8. Every weekend/duplicate-prone row with its exercises and session counts, so
--     the keeper choice (sessions > exercises > real name > lowest id) is auditable.
SELECT d.user_id, d.plan_id, d.weekday,
       COUNT(DISTINCT d.id)                          AS rows_on_weekday,
       SUM(CASE WHEN d.rest_day THEN 1 ELSE 0 END)   AS rest_rows,
       SUM(CASE WHEN NOT d.rest_day THEN 1 ELSE 0 END) AS workout_rows,
       COALESCE(SUM(we.cnt), 0)                      AS exercises,
       COALESCE(SUM(ss.cnt), 0)                      AS sessions,
       string_agg(DISTINCT d.id::text, ', ')         AS row_ids
FROM workout_day d
LEFT JOIN (SELECT workout_day_id, COUNT(*) AS cnt FROM workout_exercise GROUP BY workout_day_id) we
       ON we.workout_day_id = d.id
LEFT JOIN (SELECT workout_day_id, COUNT(*) AS cnt FROM workout_session GROUP BY workout_day_id) ss
       ON ss.workout_day_id = d.id
WHERE d.weekday IS NOT NULL
GROUP BY d.user_id, d.plan_id, d.weekday
ORDER BY d.user_id, d.plan_id, d.weekday;

-- A9. Plans with a malformed schedule at a glance.
SELECT d.user_id, d.plan_id,
       COUNT(*) FILTER (WHERE d.weekday IS NOT NULL)                     AS scheduled_rows,
       COUNT(DISTINCT d.weekday) FILTER (WHERE d.weekday IS NOT NULL)    AS distinct_weekdays,
       COUNT(*) FILTER (WHERE d.weekday IS NOT NULL AND d.rest_day)      AS rest_rows,
       COUNT(*) FILTER (WHERE d.weekday IS NOT NULL AND NOT d.rest_day)  AS workout_rows
FROM workout_day d
WHERE d.plan_id IS NOT NULL
GROUP BY d.user_id, d.plan_id
HAVING COUNT(*) FILTER (WHERE d.weekday IS NOT NULL) <> 7
    OR COUNT(DISTINCT d.weekday) FILTER (WHERE d.weekday IS NOT NULL) <> 7
ORDER BY d.user_id, d.plan_id;

-- A10. The known production example (user_id = 2, plan_id = 4), before/after.
SELECT id, day_number, weekday, name, rest_day
FROM workout_day
WHERE user_id = 2 AND plan_id = 4
ORDER BY day_number, id;

-- A11. Unique weekday index presence (0 rows = not created yet).
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'workout_day'
  AND indexname = 'uk_workout_day_user_plan_weekday';

-- A12. Unique constraints on workout_day (expect only the (user_id, plan_id,
--      day_number) key plus the new weekday index above).
SELECT con.conname AS constraint_name,
       con.contype AS type,
       string_agg(att.attname, ', ' ORDER BY k.ord) AS columns
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
JOIN pg_namespace ns ON ns.oid = rel.relnamespace
CROSS JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, ord)
JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k.attnum
WHERE rel.relname = 'workout_day' AND ns.nspname = 'public' AND con.contype = 'u'
GROUP BY con.conname, con.contype
ORDER BY con.conname;


-- ============================================================================
-- SECTION B — WHAT THE BOOT MIGRATION WILL DO (no manual SQL required)
-- ----------------------------------------------------------------------------
-- On the next backend start, SchemaMigration runs, in order:
--   1. StartupSchemaMigration   -> ensures every new column exists (idempotent)
--   2. ScheduleReconciler       -> repairs malformed weekly schedules (below)
--   3. PlanService              -> ensures each user has an active plan + 7 days
--   4. ScheduleReconciler       -> second, idempotent clean-up pass
--   5. weekday unique index     -> created only if no duplicate remains
--
-- For user_id = 2, plan_id = 4 the expected automatic result is:
--   weekday 1 -> Rest (new rest row, day_number 51)
--   weekday 2 -> Rest (id 59 normalised to rest_day = true)
--   weekday 3 -> Back (id 58, rest_day = false, renumbered 51 -> 7; id 60 removed)
--   weekday 4 -> Rest (id 61)
--   weekday 5 -> Rest (id 62)
--   weekday 6 -> Rest (id 63)
--   weekday 7 -> Rest (id 64)
-- Historical sessions/exercises are never deleted; a duplicate that owns
-- exercises or sessions is detached (weekday cleared) instead of removed.


-- ============================================================================
-- SECTION C — OPTIONAL MANUAL VERIFICATION ONLY
-- ----------------------------------------------------------------------------
-- The application creates the partial unique index itself. This statement is
-- provided only if you want to confirm/adopt it manually, and MUST NOT be run
-- while SECTION A2 still returns rows.
--
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_workout_day_user_plan_weekday
--     ON workout_day (user_id, plan_id, weekday)
--     WHERE weekday IS NOT NULL;

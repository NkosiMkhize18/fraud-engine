-- Plain (transactional) index build. On a large fraud_assessment table, build it by hand with
-- CREATE INDEX CONCURRENTLY before deploying, so the migration finds it already present (IF NOT EXISTS).
-- CONCURRENTLY can't run inside Flyway here: it waits for every open transaction, including Flyway's own, so startup hangs.
CREATE INDEX IF NOT EXISTS idx_assessment_account_evaluated ON fraud_assessment (account_id, evaluated_at);

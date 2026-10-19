-- Audit trail of API activity: who submitted, read or searched, and the outcome. Append-only.
CREATE TABLE audit_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor       VARCHAR(128) NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    resource_id VARCHAR(64),
    outcome     VARCHAR(32)  NOT NULL,
    request_id  VARCHAR(64),
    detail      VARCHAR(255)
);

CREATE INDEX idx_audit_resource ON audit_event (resource_id, occurred_at);
CREATE INDEX idx_audit_occurred ON audit_event (occurred_at);

-- Rows can't be changed or removed, even by the application. The database role that runs the application should
-- also not own this table, so it can't drop the trigger; that separation is an operational control.
CREATE FUNCTION audit_event_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$;

CREATE TRIGGER audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_append_only();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_append_only();

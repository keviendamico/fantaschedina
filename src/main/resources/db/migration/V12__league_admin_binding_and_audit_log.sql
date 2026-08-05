-- Admin-league binding
ALTER TABLE leagues ADD COLUMN created_by_user_id BIGINT;

UPDATE leagues
SET created_by_user_id = (SELECT id FROM users WHERE username = 'kappanove');

ALTER TABLE leagues ALTER COLUMN created_by_user_id SET NOT NULL;

-- League audit log
CREATE TABLE league_audit_log (
    id                   BIGSERIAL PRIMARY KEY,
    league_id            BIGINT       NOT NULL REFERENCES leagues(id),
    type                 VARCHAR(30)  NOT NULL,
    target_membership_id BIGINT       REFERENCES league_memberships(id),
    amount               INTEGER,
    note                 TEXT,
    created_at           TIMESTAMP    NOT NULL
);
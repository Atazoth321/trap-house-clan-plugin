CREATE TABLE IF NOT EXISTS verified_membership_awards (
 id uuid PRIMARY KEY REFERENCES events(id),
 member_id uuid NOT NULL REFERENCES members(id),
 actor uuid NOT NULL REFERENCES members(id),
 start_date date NOT NULL,
 end_date date NOT NULL CHECK(end_date>start_date),
 reason varchar(1000) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS verified_membership_member ON verified_membership_awards(member_id);
DROP TRIGGER IF EXISTS immutable_verified_membership ON verified_membership_awards;
CREATE TRIGGER immutable_verified_membership BEFORE UPDATE OR DELETE OR TRUNCATE ON verified_membership_awards
 FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();

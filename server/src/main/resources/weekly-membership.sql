-- The first migration is the earning baseline, including for existing members.
CREATE TABLE IF NOT EXISTS membership_reward_state (
 member_id uuid PRIMARY KEY REFERENCES members(id),
 started_at timestamptz NOT NULL DEFAULT now(),
 paid_weeks bigint NOT NULL DEFAULT 0 CHECK(paid_weeks>=0)
);
INSERT INTO membership_reward_state(member_id) SELECT id FROM members ON CONFLICT DO NOTHING;
CREATE OR REPLACE FUNCTION initialize_membership_rewards() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO membership_reward_state(member_id) VALUES(NEW.id) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS membership_reward_baseline ON members;
CREATE TRIGGER membership_reward_baseline AFTER INSERT ON members
 FOR EACH ROW EXECUTE FUNCTION initialize_membership_rewards();
CREATE UNIQUE INDEX IF NOT EXISTS membership_week_once ON events(member_id,event_key) WHERE type='MEMBERSHIP';

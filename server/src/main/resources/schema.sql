CREATE TABLE IF NOT EXISTS members (
    id uuid PRIMARY KEY,
    name varchar(12) NOT NULL,
    normalized_name varchar(12) NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS credentials (
    token_hash char(64) PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id),
    administrator boolean NOT NULL DEFAULT false,
    expires_at timestamptz NOT NULL,
    revoked boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS ranks (
    name varchar(40) PRIMARY KEY,
    threshold bigint NOT NULL UNIQUE CHECK (threshold >= 0)
);
-- Label aliases preserve immutable history and existing manual assignments.
CREATE TABLE IF NOT EXISTS rank_aliases (legacy_name varchar(40) PRIMARY KEY,rank_name varchar(40) NOT NULL);
INSERT INTO rank_aliases VALUES ('Soldier','Corporal'),('Enforcer','Sergeant'),('Lieutenant','Brigadier'),
 ('TRAP Elite','Marshal'),('Administrator','Deputy Owner'),('Event Manager','Major') ON CONFLICT DO NOTHING;
UPDATE ranks r SET name=a.rank_name FROM rank_aliases a WHERE r.name=a.legacy_name
 AND NOT EXISTS(SELECT 1 FROM ranks existing WHERE existing.name=a.rank_name);
INSERT INTO ranks VALUES ('Recruit',0),('Pawn',50),('Corporal',100),('Novice',250),('Sergeant',500),
 ('Colonel',1000),('Brigadier',1500),('Admiral',3000),('Marshal',5000)
ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS rules (
    type varchar(12) NOT NULL CHECK (type IN ('LOOT','COMBAT','COLLECTION','MANUAL')),
    event_key varchar(200) NOT NULL,
    points integer NOT NULL CHECK (points BETWEEN 1 AND 100000),
    daily_limit integer NOT NULL CHECK (daily_limit BETWEEN 1 AND 100),
    enabled boolean NOT NULL DEFAULT false,
    PRIMARY KEY (type, event_key)
);
CREATE TABLE IF NOT EXISTS events (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id),
    client_id uuid NOT NULL,
    type varchar(12) NOT NULL,
    event_key varchar(200) NOT NULL,
    observed_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    points integer NOT NULL CHECK (points > 0),
    UNIQUE (member_id, client_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS unique_unlock ON events(member_id, type, event_key)
WHERE type IN ('COMBAT','COLLECTION');
CREATE INDEX IF NOT EXISTS event_daily ON events(member_id, type, event_key, received_at);
CREATE TABLE IF NOT EXISTS manual_rewards (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id),
    request_id uuid NOT NULL,
    event_key varchar(200) NOT NULL,
    reason varchar(1000) NOT NULL,
    points integer NOT NULL CHECK (points > 0),
    daily_limit integer NOT NULL,
    status varchar(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    reviewed_by uuid REFERENCES members(id),
    reviewed_at timestamptz,
    UNIQUE(member_id, request_id),
    CHECK (reviewed_by IS NULL OR reviewed_by <> member_id)
);
CREATE TABLE IF NOT EXISTS ledger (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id),
    points integer NOT NULL CHECK (points > 0),
    event_id uuid UNIQUE REFERENCES events(id),
    reward_id uuid UNIQUE REFERENCES manual_rewards(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((event_id IS NULL) <> (reward_id IS NULL))
);
CREATE TABLE IF NOT EXISTS audit (
    id bigserial PRIMARY KEY,
    actor uuid NOT NULL REFERENCES members(id),
    action varchar(40) NOT NULL,
    target uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS discord_outbox (
    id uuid PRIMARY KEY,
    content varchar(1800) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt timestamptz NOT NULL DEFAULT now()
);

-- Additive v2 migration. Existing awards and member IDs are preserved.
ALTER TABLE rules ADD COLUMN IF NOT EXISTS category varchar(20) NOT NULL DEFAULT '';
ALTER TABLE rules ADD COLUMN IF NOT EXISTS display_name varchar(100) NOT NULL DEFAULT '';
ALTER TABLE ledger ADD COLUMN IF NOT EXISTS category varchar(20) NOT NULL DEFAULT 'Misc';
ALTER TABLE events ALTER COLUMN received_at SET DEFAULT clock_timestamp();
CREATE TABLE IF NOT EXISTS goals (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    description varchar(500) NOT NULL DEFAULT '',
    category varchar(20) NOT NULL CHECK (category IN ('Milestones','Combat','Pets','Collection','Diaries','Challenges')),
    section varchar(100) NOT NULL,
    source varchar(20) NOT NULL CHECK (source IN ('EVENT','MANUAL','VARBIT','VARP','SKILL','QUEST','ITEM','TOTAL_LEVEL','CA_TIER')),
    source_key varchar(200) NOT NULL,
    target bigint NOT NULL CHECK (target BETWEEN 1 AND 2147483647),
    points integer NOT NULL DEFAULT 0 CHECK (points BETWEEN 0 AND 100000),
    enabled boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS goal_progress (
    member_id uuid NOT NULL REFERENCES members(id),
    goal_id uuid NOT NULL REFERENCES goals(id),
    value bigint NOT NULL CHECK (value >= 0),
    completed_at timestamptz,
    award_event uuid UNIQUE REFERENCES events(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id,goal_id)
);
CREATE TABLE IF NOT EXISTS clan_events (
    id uuid PRIMARY KEY,
    title varchar(100) NOT NULL,
    description varchar(1000) NOT NULL DEFAULT '',
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    registration_closes timestamptz NOT NULL,
    capacity integer NOT NULL DEFAULT 100 CHECK (capacity BETWEEN 1 AND 1000),
    published boolean NOT NULL DEFAULT false,
    CHECK (ends_at > starts_at),
    CHECK (registration_closes <= ends_at)
);
CREATE TABLE IF NOT EXISTS event_registrations (
    event_id uuid NOT NULL REFERENCES clan_events(id),
    member_id uuid NOT NULL REFERENCES members(id),
    registered_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id,member_id)
);
CREATE TABLE IF NOT EXISTS races (
    id uuid PRIMARY KEY,
    title varchar(100) NOT NULL,
    description varchar(1000) NOT NULL DEFAULT '',
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    score_type varchar(12) NOT NULL CHECK (score_type IN ('LOOT','COMBAT','COLLECTION')),
    score_key varchar(200) NOT NULL,
    published boolean NOT NULL DEFAULT false,
    CHECK (ends_at>starts_at)
);
CREATE TABLE IF NOT EXISTS race_prizes (
    race_id uuid NOT NULL REFERENCES races(id),
    place integer NOT NULL CHECK (place BETWEEN 1 AND 1000),
    points integer NOT NULL CHECK (points BETWEEN 0 AND 100000),
    PRIMARY KEY (race_id,place)
);
CREATE TABLE IF NOT EXISTS race_firsts (
    id uuid PRIMARY KEY,
    race_id uuid NOT NULL REFERENCES races(id),
    label varchar(100) NOT NULL,
    event_type varchar(12) NOT NULL CHECK (event_type IN ('LOOT','COMBAT','COLLECTION')),
    event_key varchar(200) NOT NULL,
    points integer NOT NULL CHECK (points BETWEEN 0 AND 100000)
);
CREATE TABLE IF NOT EXISTS member_metrics (
    member_id uuid PRIMARY KEY REFERENCES members(id),
    ehp numeric(16,3) NOT NULL CHECK (ehp>=0),
    ehb numeric(16,3) NOT NULL CHECK (ehb>=0),
    fetched_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS metric_attempts (
    member_id uuid PRIMARY KEY REFERENCES members(id),
    last_attempt timestamptz NOT NULL DEFAULT now()
);

-- Additive rank protection migration; existing awards are preserved.
CREATE TABLE IF NOT EXISTS member_permissions (
 member_id uuid PRIMARY KEY REFERENCES members(id),
 permission varchar(20) NOT NULL CHECK (permission IN ('OWNER','ADMINISTRATOR','EVENT_MANAGER','MEMBER'))
);
CREATE TABLE IF NOT EXISTS protected_ranks (name varchar(40) PRIMARY KEY);
INSERT INTO protected_ranks VALUES ('Owner'),('Deputy Owner'),('Master'),('Major'),('Proselyte') ON CONFLICT DO NOTHING;
INSERT INTO protected_ranks SELECT a.rank_name FROM protected_ranks p JOIN rank_aliases a ON a.legacy_name=p.name WHERE true ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS member_rank_state (
 member_id uuid PRIMARY KEY REFERENCES members(id), assigned_rank varchar(40), locked boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS point_adjustments (
 id uuid PRIMARY KEY, member_id uuid NOT NULL REFERENCES members(id),
 points integer NOT NULL CHECK (points BETWEEN -100000 AND 100000 AND points <> 0),
 actor uuid NOT NULL REFERENCES members(id), reason varchar(1000) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE audit ADD COLUMN IF NOT EXISTS details jsonb NOT NULL DEFAULT '{}';
CREATE OR REPLACE FUNCTION reject_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Permanent history cannot be changed'; END $$;
DROP TRIGGER IF EXISTS immutable_audit ON audit;
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE OR TRUNCATE ON audit FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();
DROP TRIGGER IF EXISTS immutable_adjustments ON point_adjustments;
CREATE TRIGGER immutable_adjustments BEFORE UPDATE OR DELETE OR TRUNCATE ON point_adjustments FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();
CREATE OR REPLACE VIEW point_entries AS
 SELECT id,member_id,points,event_id,reward_id,created_at,category FROM ledger
 UNION ALL SELECT id,member_id,points,NULL::uuid,NULL::uuid,created_at,'Misc'::varchar FROM point_adjustments;
CREATE TABLE IF NOT EXISTS promotion_notices (
 member_id uuid NOT NULL REFERENCES members(id), rank varchar(40) NOT NULL, PRIMARY KEY(member_id,rank)
);

-- Permanent lifetime rules. A balance is never a substitute for rank history.
CREATE TABLE IF NOT EXISTS earned_rank_history (
 member_id uuid NOT NULL REFERENCES members(id), rank varchar(40) NOT NULL,
 threshold bigint NOT NULL CHECK(threshold>=0), points_at_earning bigint NOT NULL,
 earned_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(member_id,rank,threshold)
);
DROP TRIGGER IF EXISTS immutable_earned_ranks ON earned_rank_history;
CREATE TRIGGER immutable_earned_ranks BEFORE UPDATE OR DELETE OR TRUNCATE ON earned_rank_history
 FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();
DROP TRIGGER IF EXISTS immutable_ledger ON ledger;
CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE OR TRUNCATE ON ledger
 FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();

-- Recover historical peaks using the available entries and current threshold definitions.
WITH running AS (
 SELECT member_id,created_at,sum(points) OVER(PARTITION BY member_id ORDER BY created_at,id ROWS UNBOUNDED PRECEDING) AS balance FROM point_entries
), peaks AS (SELECT member_id,greatest(0,max(balance)) AS balance FROM running GROUP BY member_id)
INSERT INTO earned_rank_history(member_id,rank,threshold,points_at_earning)
 SELECT m.id,r.name,r.threshold,coalesce(p.balance,0) FROM members m LEFT JOIN peaks p ON p.member_id=m.id CROSS JOIN ranks r
 WHERE r.threshold<=coalesce(p.balance,0) AND r.name NOT IN (SELECT name FROM protected_ranks) AND r.name NOT IN (SELECT legacy_name FROM rank_aliases)
 ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION capture_earned_ranks() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE member_key uuid; balance bigint;
BEGIN
 IF TG_TABLE_NAME='members' THEN member_key := NEW.id; ELSE member_key := NEW.member_id; END IF;
 SELECT coalesce(sum(points),0) INTO balance FROM point_entries WHERE member_id=member_key;
 INSERT INTO earned_rank_history(member_id,rank,threshold,points_at_earning)
 SELECT member_key,name,threshold,balance FROM ranks WHERE threshold<=balance
 AND name NOT IN (SELECT name FROM protected_ranks) AND name NOT IN (SELECT legacy_name FROM rank_aliases) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS earned_from_awards ON ledger;
CREATE TRIGGER earned_from_awards AFTER INSERT ON ledger FOR EACH ROW EXECUTE FUNCTION capture_earned_ranks();
DROP TRIGGER IF EXISTS earned_from_adjustments ON point_adjustments;
CREATE TRIGGER earned_from_adjustments AFTER INSERT ON point_adjustments FOR EACH ROW EXECUTE FUNCTION capture_earned_ranks();
DROP TRIGGER IF EXISTS earned_from_membership ON members;
CREATE TRIGGER earned_from_membership AFTER INSERT ON members FOR EACH ROW EXECUTE FUNCTION capture_earned_ranks();
CREATE OR REPLACE FUNCTION capture_threshold_qualifications() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO earned_rank_history(member_id,rank,threshold,points_at_earning)
 SELECT m.id,r.name,r.threshold,b.balance FROM members m
 CROSS JOIN LATERAL(SELECT coalesce(sum(points),0) AS balance FROM point_entries WHERE member_id=m.id) b
 CROSS JOIN ranks r WHERE r.threshold<=b.balance AND r.name NOT IN (SELECT name FROM protected_ranks) AND r.name NOT IN (SELECT legacy_name FROM rank_aliases)
 ON CONFLICT DO NOTHING;
 RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS earned_from_thresholds ON ranks;
CREATE TRIGGER earned_from_thresholds AFTER INSERT OR UPDATE ON ranks FOR EACH STATEMENT EXECUTE FUNCTION capture_threshold_qualifications();

-- Include current threshold qualifications while retaining every previously earned tier.
CREATE OR REPLACE VIEW automatic_rank_candidates AS
 SELECT h.member_id,coalesce(a.rank_name,h.rank) AS rank,h.threshold FROM earned_rank_history h
 LEFT JOIN rank_aliases a ON a.legacy_name=h.rank
 WHERE coalesce(a.rank_name,h.rank) NOT IN (SELECT name FROM protected_ranks)
 UNION SELECT m.id,r.name,r.threshold FROM members m CROSS JOIN ranks r
 WHERE r.threshold <= (SELECT coalesce(sum(points),0) FROM point_entries WHERE member_id=m.id)
 AND r.name NOT IN (SELECT name FROM protected_ranks) AND r.name NOT IN (SELECT legacy_name FROM rank_aliases);
CREATE OR REPLACE VIEW retained_automatic_ranks AS
 SELECT DISTINCT ON(member_id) member_id,rank,threshold FROM automatic_rank_candidates ORDER BY member_id,threshold DESC,rank;

CREATE OR REPLACE FUNCTION guard_point_adjustment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE balance bigint;
BEGIN
 IF length(btrim(NEW.reason))=0 THEN RAISE EXCEPTION 'Adjustment reason required'; END IF;
 IF NEW.points<0 AND NOT EXISTS(SELECT 1 FROM member_permissions p JOIN members m ON m.id=p.member_id
   WHERE p.member_id=NEW.actor AND p.permission='OWNER' AND m.active) THEN
   RAISE EXCEPTION 'Only the Owner may deduct lifetime points';
 END IF;
 PERFORM 1 FROM members WHERE id=NEW.member_id FOR UPDATE;
 SELECT coalesce(sum(points),0) INTO balance FROM point_entries WHERE member_id=NEW.member_id;
 IF balance+NEW.points<0 THEN RAISE EXCEPTION 'Points cannot become negative'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS guard_adjustment ON point_adjustments;
CREATE TRIGGER guard_adjustment BEFORE INSERT ON point_adjustments FOR EACH ROW EXECUTE FUNCTION guard_point_adjustment();

CREATE OR REPLACE FUNCTION audit_point_adjustment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE balance bigint; recipient_name text; actor_name text;
BEGIN
 SELECT coalesce(sum(points),0) INTO balance FROM point_entries WHERE member_id=NEW.member_id;
 SELECT name INTO recipient_name FROM members WHERE id=NEW.member_id;
 SELECT name INTO actor_name FROM members WHERE id=NEW.actor;
 INSERT INTO audit(actor,action,target,details) VALUES(NEW.actor,'POINT_ADJUSTMENT',NEW.member_id,
 jsonb_build_object('id',NEW.id,'memberId',NEW.member_id,'memberName',recipient_name,'actorId',NEW.actor,
 'actorName',actor_name,'points',NEW.points,'reason',NEW.reason,'beforePoints',balance-NEW.points,'afterPoints',balance));
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS audit_adjustment ON point_adjustments;
CREATE TRIGGER audit_adjustment AFTER INSERT ON point_adjustments FOR EACH ROW EXECUTE FUNCTION audit_point_adjustment();

-- One verified Discord identity per roster member. Discord names never grant ranks or permissions.
CREATE TABLE IF NOT EXISTS discord_member_links (
 discord_user_id varchar(20) PRIMARY KEY CHECK(discord_user_id ~ '^[0-9]{17,20}$'),
 member_id uuid NOT NULL UNIQUE REFERENCES members(id),
 linked_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS discord_login_sessions (
 id uuid PRIMARY KEY,
 device_hash char(64) NOT NULL,
 browser_hash char(64) NOT NULL,
 state_hash char(64) UNIQUE,
 member_id uuid REFERENCES members(id),
 callback_used boolean NOT NULL DEFAULT false,
 last_poll timestamptz,
 expires_at timestamptz NOT NULL DEFAULT now()+interval '10 minutes'
);
CREATE INDEX IF NOT EXISTS discord_login_expiry ON discord_login_sessions(expires_at);

CREATE TABLE IF NOT EXISTS efficiency_reward_state (
 member_id uuid PRIMARY KEY REFERENCES members(id),
 ehp_high numeric(20,8) NOT NULL CHECK(ehp_high>=0),
 ehb_high numeric(20,8) NOT NULL CHECK(ehb_high>=0),
 last_snapshot timestamptz NOT NULL,
 baseline_at timestamptz NOT NULL,
 week_start timestamptz NOT NULL,
 weekly_points integer NOT NULL DEFAULT 0 CHECK(weekly_points BETWEEN 0 AND 250),
 carry numeric(20,8) NOT NULL DEFAULT 0 CHECK(carry>=0 AND carry<1)
);

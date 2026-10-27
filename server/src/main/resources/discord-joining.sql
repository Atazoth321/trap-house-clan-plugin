CREATE TABLE IF NOT EXISTS discord_join_requests (
 id uuid PRIMARY KEY,
 discord_user_id varchar(20) NOT NULL,
 character_name varchar(12) NOT NULL,
 normalized_name varchar(12) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 reviewed_at timestamptz,
 reviewer uuid REFERENCES members(id),
 reason varchar(1000),
 member_id uuid REFERENCES members(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS discord_join_pending_user ON discord_join_requests(discord_user_id) WHERE status='PENDING';
ALTER TABLE discord_join_requests ADD COLUMN IF NOT EXISTS reviewer_discord_id varchar(20);
-- Unlinked Discord staff are identified in audit details rather than by a member UUID.
ALTER TABLE audit ALTER COLUMN actor DROP NOT NULL;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='audit_discord_reviewer_required' AND conrelid='audit'::regclass) THEN
  ALTER TABLE audit ADD CONSTRAINT audit_discord_reviewer_required CHECK (
   actor IS NOT NULL OR (action IN ('DISCORD_LINK_APPROVED','DISCORD_LINK_REJECTED')
    AND coalesce(details->>'reviewerDiscordId','') ~ '^[0-9]{17,20}$'));
 END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS discord_join_pending_name ON discord_join_requests(normalized_name) WHERE status='PENDING';
CREATE TABLE IF NOT EXISTS discord_interactions (
 id varchar(20) PRIMARY KEY,
 response jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS discord_interactions_created ON discord_interactions(created_at);
CREATE TABLE IF NOT EXISTS discord_join_outbox (
 id uuid PRIMARY KEY,
 payload jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 next_attempt timestamptz NOT NULL DEFAULT now(),
 attempts integer NOT NULL DEFAULT 0,
 delivered_at timestamptz
);
ALTER TABLE discord_join_outbox ADD COLUMN IF NOT EXISTS review_channel_id varchar(20);
ALTER TABLE discord_join_outbox ADD COLUMN IF NOT EXISTS review_message_id varchar(20);
CREATE TABLE IF NOT EXISTS discord_join_archive_outbox (
 request_id uuid PRIMARY KEY REFERENCES discord_join_requests(id),
 payload jsonb NOT NULL,
 staff_channel_id varchar(20),
 staff_message_id varchar(20),
 logged_at timestamptz,
 cleaned_at timestamptz,
 attempts integer NOT NULL DEFAULT 0,
 next_attempt timestamptz NOT NULL DEFAULT now(),
 created_at timestamptz NOT NULL DEFAULT now()
);

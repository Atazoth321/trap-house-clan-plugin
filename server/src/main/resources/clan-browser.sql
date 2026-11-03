-- Read-only browser observations are separate from rewards and permissions.
CREATE TABLE IF NOT EXISTS browser_statistics (
 member_id uuid PRIMARY KEY REFERENCES members(id),
 statistics jsonb NOT NULL DEFAULT '{}',
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS browser_collection_pages (
 member_id uuid NOT NULL REFERENCES members(id),
 page_key varchar(100) NOT NULL,
 label varchar(100) NOT NULL,
 section varchar(32) NOT NULL,
 items jsonb NOT NULL,
 counter bigint CHECK(counter>=0),
 completion_counter bigint CHECK(completion_counter>=0),
 completed boolean NOT NULL DEFAULT false,
 completion_estimated boolean NOT NULL DEFAULT true,
 last_drop_counter bigint CHECK(last_drop_counter>=0),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(member_id,page_key)
);
CREATE INDEX IF NOT EXISTS browser_page_lookup ON browser_collection_pages(page_key);
CREATE TABLE IF NOT EXISTS browser_monthly_collection (
 member_id uuid NOT NULL REFERENCES members(id),
 month date NOT NULL,
 baseline integer NOT NULL CHECK(baseline>=0),
 peak integer NOT NULL CHECK(peak>=baseline),
 PRIMARY KEY(member_id,month)
);

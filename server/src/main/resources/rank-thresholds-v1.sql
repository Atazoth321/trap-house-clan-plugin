-- Apply the Owner-selected progression once. Later operator edits are not reset on redeploy.
CREATE TABLE IF NOT EXISTS configuration_migrations (
 name varchar(100) PRIMARY KEY,
 applied_at timestamptz NOT NULL DEFAULT now()
);
DO $$
BEGIN
 PERFORM pg_advisory_xact_lock(72419051001);
 IF NOT EXISTS(SELECT 1 FROM configuration_migrations WHERE name='higher-rank-thresholds-v1') THEN
  -- Move the standard thresholds out of the destination range first to avoid unique collisions.
  -- Any conflict with a custom rank fails atomically instead of deleting that rank.
  UPDATE ranks SET threshold=threshold+(SELECT greatest(max(threshold),45000)+1 FROM ranks)
   WHERE name IN ('Recruit','Pawn','Corporal','Novice','Sergeant','Colonel','Brigadier','Admiral','Marshal');
  UPDATE ranks r SET threshold=v.threshold FROM (VALUES
   ('Recruit',0::bigint),('Pawn',500),('Corporal',1500),('Novice',3000),('Sergeant',6000),
   ('Colonel',10000),('Brigadier',17500),('Admiral',27500),('Marshal',45000)
  ) AS v(name,threshold) WHERE r.name=v.name;
  INSERT INTO configuration_migrations(name) VALUES ('higher-rank-thresholds-v1');
 END IF;
END $$;

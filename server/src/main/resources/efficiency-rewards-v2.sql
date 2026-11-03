-- Increase only the earning allowance. Retain all baseline/high-water state and awards.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM configuration_migrations WHERE name='efficiency-rewards-v2') THEN
  ALTER TABLE efficiency_reward_state DROP CONSTRAINT IF EXISTS efficiency_reward_state_weekly_points_check;
  ALTER TABLE efficiency_reward_state ADD CONSTRAINT efficiency_reward_state_weekly_points_check CHECK(weekly_points BETWEEN 0 AND 500);
  INSERT INTO configuration_migrations(name) VALUES ('efficiency-rewards-v2');
 END IF;
END $$;

-- Increasing threshold rewards for future awards. Preserve existing ledger and staff overrides.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM configuration_migrations WHERE name='collection-milestone-balance-v2') THEN
  UPDATE goals g SET points=v.new_points FROM (VALUES
   ('41000000-0000-4000-8000-000000000001'::uuid,200,200),
   ('41000000-0000-4000-8000-000000000002'::uuid,400,250),
   ('41000000-0000-4000-8000-000000000003'::uuid,400,300),
   ('41000000-0000-4000-8000-000000000004'::uuid,400,350),
   ('41000000-0000-4000-8000-000000000005'::uuid,400,400),
   ('41000000-0000-4000-8000-000000000006'::uuid,200,450),
   ('41000000-0000-4000-8000-000000000007'::uuid,200,500),
   ('41000000-0000-4000-8000-000000000008'::uuid,200,550),
   ('41000000-0000-4000-8000-000000000009'::uuid,600,600)
  ) AS v(id,old_points,new_points)
  WHERE g.id=v.id AND g.enabled AND g.points=v.old_points;
  INSERT INTO configuration_migrations(name) VALUES ('collection-milestone-balance-v2');
 END IF;
END $$;

-- Future awards only; retain staff settings and historical ledger entries.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM configuration_migrations WHERE name='prestige-combat-rewards-v2') THEN
  UPDATE goals g SET points=v.new_points FROM (VALUES
   ('40000000-0000-4000-8000-000000000006'::uuid,200,1000),
   ('40000000-0000-4000-8000-000000000007'::uuid,500,1000),
   ('10000000-0000-4000-8000-000000000010'::uuid,30,100),
   ('10000000-0000-4000-8000-000000000011'::uuid,60,250),
   ('10000000-0000-4000-8000-000000000012'::uuid,120,500),
   ('10000000-0000-4000-8000-000000000013'::uuid,230,1000),
   ('10000000-0000-4000-8000-000000000014'::uuid,380,2000),
   ('10000000-0000-4000-8000-000000000015'::uuid,750,5000)
  ) AS v(id,old_points,new_points)
  WHERE g.id=v.id AND g.enabled AND g.points=v.old_points;
  INSERT INTO configuration_migrations(name) VALUES ('prestige-combat-rewards-v2');
 END IF;
END $$;

-- Caller owns the transaction; serialize with the other reward migrations.
SELECT pg_advisory_xact_lock(72419051001);
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM configuration_migrations WHERE name='activity-rewards-v3') THEN RETURN; END IF;

 -- Existing enabled activity rules receive twice their former reward. Preserve caps,
 -- disabled rules, and all historical awards; never backpay repeatable observations.
 UPDATE rules SET points=least(100000,points*2)
 WHERE enabled AND category IN ('Drops','Diaries','Combat','Pets','Events','Milestones');

 -- New claims require the existing independent Administrator/Owner review workflow.
 -- These are not automatic proof of a drop, pet, diary completion, or event attendance.
 INSERT INTO rules(type,event_key,points,daily_limit,enabled,category,display_name) VALUES
 ('MANUAL','verified rare drop',250,1,true,'Drops','Verified rare drop'),
 ('MANUAL','verified pet unlock',750,1,true,'Pets','Verified new pet unlock'),
 ('MANUAL','verified diary tier',250,1,true,'Diaries','Verified achievement diary tier'),
 ('MANUAL','clan event attendance',200,1,true,'Events','Verified clan event attendance')
 ON CONFLICT(type,event_key) DO NOTHING;

 -- Existing starter diary goals were tracking only. Completed goals with no prior
 -- award are paid once by the normal sync pipeline, including past completions.
 UPDATE goals SET points=CASE source_key WHEN 'LOOT:*' THEN 200 ELSE 100 END
 WHERE id IN ('10000000-0000-4000-8000-000000000040',
              '10000000-0000-4000-8000-000000000041',
              '10000000-0000-4000-8000-000000000042')
 AND enabled AND points=0;
 INSERT INTO configuration_migrations(name) VALUES ('activity-rewards-v3');
END $$;

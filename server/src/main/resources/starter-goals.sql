-- Tracking-only defaults: no clan reward amounts are invented.
INSERT INTO goals(id,name,description,category,section,source,source_key,target,enabled,sort_order) VALUES
('10000000-0000-4000-8000-000000000001','First Dragon defender','Observed in your inventory, equipment or an opened bank.','Milestones','Milestones','ITEM','12954',1,true,1),
('10000000-0000-4000-8000-000000000002','First Fire cape','Observed in your inventory, equipment or an opened bank.','Milestones','Milestones','ITEM','6570',1,true,2),
('10000000-0000-4000-8000-000000000003','First Infernal cape','Observed in your inventory, equipment or an opened bank.','Milestones','Milestones','ITEM','21295',1,true,3),
('10000000-0000-4000-8000-000000000004','Champion cape','Observed in your inventory, equipment or an opened bank.','Milestones','Milestones','ITEM','21439',1,true,4),
('10000000-0000-4000-8000-000000000005','Cursed phalanx','Observed in your inventory, equipment or an opened bank.','Milestones','Milestones','ITEM','27248',1,true,5),
('10000000-0000-4000-8000-000000000006','Total level 2000','Your current total level.','Milestones','Milestones','TOTAL_LEVEL','total',2000,true,6),
('10000000-0000-4000-8000-000000000010','Easy tier','Compared with the live game combat-point threshold.','Combat','Combat achievements','CA_TIER','4132',1,true,10),
('10000000-0000-4000-8000-000000000011','Medium tier','Compared with the live game combat-point threshold.','Combat','Combat achievements','CA_TIER','10660',1,true,11),
('10000000-0000-4000-8000-000000000012','Hard tier','Compared with the live game combat-point threshold.','Combat','Combat achievements','CA_TIER','10661',1,true,12),
('10000000-0000-4000-8000-000000000013','Elite tier','Compared with the live game combat-point threshold.','Combat','Combat achievements','CA_TIER','14812',1,true,13),
('10000000-0000-4000-8000-000000000014','Master tier','Compared with the live game combat-point threshold.','Combat','Combat achievements','CA_TIER','14813',1,true,14),
('10000000-0000-4000-8000-000000000015','Grandmaster tier','Compared with the live game combat-point threshold.','Combat','Combat achievements','CA_TIER','14814',1,true,15),
('10000000-0000-4000-8000-000000000020','First collection unlock','A supported collection unlock recorded while tracking is enabled.','Collection','Collection log','EVENT','COLLECTION:*',1,true,20),
('10000000-0000-4000-8000-000000000021','First combat task','A supported combat task recorded while tracking is enabled.','Combat','Combat achievements','EVENT','COMBAT:*',1,true,21),
('10000000-0000-4000-8000-000000000030','Abyssal orphan','Observed pet insurance flag; this is client evidence, not independent verification.','Pets','Pets','VARBIT','204',1,true,30),
('10000000-0000-4000-8000-000000000031','Jal-nib-rek','Observed pet insurance flag; this is client evidence, not independent verification.','Pets','Pets','VARBIT','5644',1,true,31),
('10000000-0000-4000-8000-000000000040','Complete a combat task','Record one supported combat unlock while tracking is enabled.','Diaries','Recruit diary','EVENT','COMBAT:*',1,true,40),
('10000000-0000-4000-8000-000000000041','Unlock a collection item','Record one supported collection unlock while tracking is enabled.','Diaries','Recruit diary','EVENT','COLLECTION:*',1,true,41),
('10000000-0000-4000-8000-000000000042','Five configured drops','Record five loot observations accepted by the clan scoring rules.','Diaries','Raider diary','EVENT','LOOT:*',5,true,42)
ON CONFLICT(id) DO NOTHING;

-- Additive one-time rewards requested by the clan. Preserve all existing staff edits.
ALTER TABLE goals DROP CONSTRAINT IF EXISTS goals_source_check;
ALTER TABLE goals ADD CONSTRAINT goals_source_check CHECK (source IN ('EVENT','MANUAL','VARBIT','VARP','SKILL','QUEST','ITEM','TOTAL_LEVEL','CA_TIER','MILESTONE'));
INSERT INTO goals(id,name,description,category,section,source,source_key,target,points,enabled,sort_order) VALUES
('40000000-0000-4000-8000-000000000001','First Quiver obtained','Own a Dizana''s quiver, including blessed, charged, locked and broken forms. Observed in inventory, equipment or an opened bank.','Milestones','Prestige milestones','MILESTONE','QUIVER',1,100,true,350),
('40000000-0000-4000-8000-000000000002','First Masori Crafting Kit','Own a Masori crafting kit or its assembled backpack variant. Observed in inventory, equipment or an opened bank.','Milestones','Prestige milestones','MILESTONE','MASORI_KIT',1,75,true,351),
('40000000-0000-4000-8000-000000000003','First Menaphite Ornament Kit','Own a Menaphite ornament kit or Elidinis'' ward (or). Observed in inventory, equipment or an opened bank.','Milestones','Prestige milestones','MILESTONE','MENAPHITE_KIT',1,100,true,352),
('40000000-0000-4000-8000-000000000004','Purifying Sigil combined','Own the completed Purifying sigil. Individual pieces do not count. Observed in inventory or an opened bank.','Milestones','Prestige milestones','MILESTONE','PURIFYING_SIGIL',1,200,true,353),
('40000000-0000-4000-8000-000000000005','Obtain the Ancient blood ornament kit','Own an Ancient blood ornament kit or a Sanguine Torva armour piece. Observed in inventory, equipment or an opened bank.','Milestones','Prestige milestones','MILESTONE','ANCIENT_BLOOD_KIT',1,200,true,354),
('40000000-0000-4000-8000-000000000006','All quests completed','Reach the game''s current maximum quest points. Miniquests are not required. Awards once, including quests completed before joining.','Milestones','Prestige milestones','MILESTONE','ALL_QUESTS',1,200,true,355),
('40000000-0000-4000-8000-000000000007','All achievement diaries completed','Complete all 48 Easy, Medium, Hard and Elite diary tiers. Awards once, in addition to the individual diary rewards.','Milestones','Prestige milestones','MILESTONE','ALL_DIARIES',1,500,true,356)
ON CONFLICT(id) DO NOTHING;

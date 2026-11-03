-- Independent cumulative thresholds; each goal awards once through the existing ledger.
INSERT INTO goals(id,name,description,category,section,source,source_key,target,points,enabled,sort_order) VALUES
('41000000-0000-4000-8000-000000000001','100+ items logged','Reach 100 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',100,200,true,360),
('41000000-0000-4000-8000-000000000002','300+ items logged','Reach 300 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',300,400,true,361),
('41000000-0000-4000-8000-000000000003','500+ items logged','Reach 500 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',500,400,true,362),
('41000000-0000-4000-8000-000000000004','700+ items logged','Reach 700 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',700,400,true,363),
('41000000-0000-4000-8000-000000000005','900+ items logged','Reach 900 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',900,400,true,364),
('41000000-0000-4000-8000-000000000006','1000+ items logged','Reach 1000 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',1000,200,true,365),
('41000000-0000-4000-8000-000000000007','1100+ items logged','Reach 1100 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',1100,200,true,366),
('41000000-0000-4000-8000-000000000008','1200+ items logged','Reach 1200 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',1200,200,true,367),
('41000000-0000-4000-8000-000000000009','1550+ items logged','Reach 1550 collection-log slots. Historical logged items count. Awarded once.','Collection','Collection Log milestones','MILESTONE','COLLECTION_COUNT',1550,600,true,368)
ON CONFLICT(id) DO NOTHING;

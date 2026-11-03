# Additional milestone rewards

Each reward pays once through the existing milestone ledger. Completed goals from before joining can qualify during a later sync. New defaults preserve existing goal identities, staff-edited rewards, disabled goals, and award history on repeated initialization.

| Milestone | Points |
| --- | ---: |
| First Quiver obtained | 100 |
| First Masori Crafting Kit | 75 |
| First Menaphite Ornament Kit | 100 |
| Purifying Sigil combined | 200 |
| Obtain the Ancient blood ornament kit | 200 |
| All quests completed | 1,000 |
| All achievement diaries completed | 1,000 |
| 100+ collection-log items | 200 |
| 300+ collection-log items | 250 |
| 500+ collection-log items | 300 |
| 700+ collection-log items | 350 |
| 900+ collection-log items | 400 |
| 1000+ collection-log items | 450 |
| 1100+ collection-log items | 500 |
| 1200+ collection-log items | 550 |
| 1550+ collection-log items | 600 |

The seven prestige milestones total **2,675 points** and collection thresholds total **3,600 points**. Threshold rewards stack; reaching 500 collection slots earns the 100, 300 and 500 rewards once each.

Item goals inspect inventory, equipment and an opened bank. Quivers include charged, blessed, locked and broken forms. Applied Masori kits, the ornamented Elidinis' ward, and Sanguine Torva pieces also qualify for their kit milestones. Individual Purifying sigil pieces do not qualify. Previously consumed items without a remaining qualifying reward cannot be reconstructed. These client observations are possession evidence, not independent ownership verification.

All quests compares current quest points with the game's live maximum; unloaded maxima remain unknown. Miniquests are not required. All achievement diaries requires all 48 tier-completion flags and stacks with the existing individual diary rewards. Collection thresholds use the game's distinct logged-item count once its maximum is loaded, rather than duplicate item quantities or plugin-observed page totals.

Deploy the backend and run the existing `init` pre-deploy command. It adds `additional-milestones.sql` and `collection-milestones.sql`. The full seeded catalog now contains **214 goals**; client and server bounds have increased to **250**. Release the updated client through Plugin Hub for automatic tracking of the new `MILESTONE` source. The item and aggregate milestones appear in Achievements under Prestige milestones and Collection Log milestones.

The Profile page shows Prestige milestones and Collection Log milestones before the skill lists. Collection thresholds display even before Collection points are earned, with progress and completion checks. New milestone definitions include itemId display metadata for the matching item, quest cape, diary cape or collection log; the Profile, Achievements, reward catalog and award history use these sprites. Unmapped goals retain category artwork. The updated backend must be deployed before these definitions appear in live profiles.

Collection rewards increase with the threshold under collection-milestone-balance-v2. This changes future awards only: prior ledger entries are neither deducted nor topped up. Custom reward amounts and disabled goals are preserved; repeated initialization does not overwrite later staff edits.

The prestige-combat-rewards-v2 migration raises unchanged enabled all-quest and all-diary defaults to 1,000 points each and combat tiers to 100 / 250 / 500 / 1,000 / 2,000 / 5,000. These increases apply to future awards; historical awards and staff overrides are preserved.

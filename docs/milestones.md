# Expanded milestones and ranking intervals

The expanded catalog adds **131 one-time tracking goals**, bringing the seeded total to **150 goals**:

- 96 skill milestones: level 50, 75, 90, and 99 for all 24 supported skills, including Sailing.
- 11 additional total-level milestones: 500, 750, 1000, 1250, 1500, 1750, 1900, 2100, 2200, 2300, and 2376. The existing total level 2000 goal is retained separately.
- 24 quest milestones: Animal Magnetism, Bone Voyage, Desert Treasure I and II, Dragon Slayer I and II, Fairytale I and II, the three Fremennik quests, The Grand Tree, A Kingdom Divided, Lunar Diplomacy, Monkey Madness I and II, Recipe for Disaster, Regicide, Sins of the Father, Song of the Elves, Tree Gnome Village, Underground Pass, Beneath Cursed Sands, and While Guthix Sleeps.

Skill milestones use unboosted levels. Quest milestones use RuneLite's quest completion state. Multiple thresholds can complete on the same sync for an established account. These probes read current account state; they do not prove the achievement happened after joining the clan. The Owner-authorized reward rollout now assigns positive rewards to 143 milestone and combat-tier goals; see [the reward table](milestone-rewards.md). The seven remaining pet, event, collection, and diary starter goals retain their existing configuration. Completed supported goals can award once during a later sync; existing awarded goals cannot award a second time.

The catalog is added during the existing `init` deployment command, using stable IDs and `ON CONFLICT DO NOTHING`. Repeated deployments preserve edited reward amounts, enabled status, and progress. The client/server currently support 200 definitions per sync; the seeded total of 150 fits that limit, leaving room for 50 custom goals. Additional custom goals beyond that limit require pagination before they can all be tracked.

## Owner-selected ranking intervals

The same deployment applies these intervals once:

| Rank | Points |
| --- | ---: |
| Recruit | 0 |
| Pawn | 500 |
| Corporal | 1,500 |
| Novice | 3,000 |
| Sergeant | 6,000 |
| Colonel | 10,000 |
| Brigadier | 17,500 |
| Admiral | 27,500 |
| Marshal | 45,000 |

`rank-thresholds-v1.sql` records this configuration migration so subsequent deployments do not overwrite future staff edits. Points, permanent history, rank overrides, locks, and protected leadership ranks are preserved. Members who already earned ranks retain them; raising thresholds does not authorize demotion. New members qualify against the new thresholds. Conflicts with a custom rank's threshold fail the transaction and require operator review rather than deleting the custom rank.

Deploy this commit on Render. The existing pre-deploy command performs the additive setup; no manual SQL is necessary for these catalog entries or rank intervals. Then refresh the plugin or wait for its next definition fetch and progress sync. Local builds and API-key validation tests do not replace live database migration verification.

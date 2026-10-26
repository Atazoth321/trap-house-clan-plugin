# Milestone rewards (current policy: v3)

The one-time milestone amounts below remain current. See [current reward balance](reward-balance.md) for the later changes to repeatable sources, pets, and starter clan diary bonuses; those supersede the activity defaults in this document.

The Owner authorized selecting and applying these values. `init` runs the `milestone-rewards-v1` configuration migration after seeding the catalog. It assigns rewards only to the 143 listed goals whose current reward is zero, preserves previously configured positive rewards and disabled goals, and records a migration marker so redeploying cannot overwrite later edits. The v2 migration increases unchanged enabled policy rewards by approximately 50%, rounded to the nearest five points, and adds a one-time positive ledger top-up for active members whose goals were already paid. Existing award rows are never edited, previous Owner deductions remain intact, and no negative adjustments occur. The normal progress pipeline issues one positive ledger award per completed goal when the member next syncs. Historical awarded goals are never paid again.

## Skills

Each of the 24 supported skills has these cumulative, one-time milestone rewards:

| Level reached | Points |
| --- | ---: |
| 50 | 20 |
| 75 | 30 |
| 90 | 60 |
| 99 | 120 |

That is 230 points per skill, or **5,520** for all skills. Already having level 99 qualifies for the four goals on sync; a temporary boost does not count.

## Total level

| Total level | Points |
| --- | ---: |
| 500 | 20 |
| 750 | 30 |
| 1,000 | 50 |
| 1,250 | 60 |
| 1,500 | 90 |
| 1,750 | 120 |
| 1,900 | 150 |
| 2,000 | 230 |
| 2,100 | 300 |
| 2,200 | 450 |
| 2,300 | 600 |
| 2,376 | 750 |

All thresholds combined: **2,850 points**. These stack with individual skill goals.

## Equipment and capes

| Milestone | Points |
| --- | ---: |
| Dragon defender | 50 |
| Fire cape | 150 |
| Infernal cape | 750 |
| Champion cape | 300 |
| Cursed phalanx | 1200 |

Combined: **2,450 points**. Detection requires the configured exact item ID in inventory, equipment, or an opened bank. Alternate cosmetic variants are not automatically equivalent, and untradeability/possession is client evidence rather than independent server verification.

## Combat achievement tiers

| Tier | Points |
| --- | ---: |
| Easy | 30 |
| Medium | 60 |
| Hard | 120 |
| Elite | 230 |
| Master | 380 |
| Grandmaster | 750 |

All tiers combined: **1,570 points**. Earlier tiers stack with later tiers.

## Quests

| Quest | Points |
| --- | ---: |
| Tree Gnome Village | 20 |
| Animal Magnetism | 30 |
| Bone Voyage | 30 |
| Dragon Slayer I | 30 |
| Fairytale I | 30 |
| The Fremennik Trials | 30 |
| The Grand Tree | 30 |
| Fairytale II | 50 |
| The Fremennik Isles | 60 |
| Monkey Madness I | 60 |
| Regicide | 60 |
| Underground Pass | 60 |
| Desert Treasure I | 80 |
| Lunar Diplomacy | 80 |
| The Fremennik Exiles | 120 |
| A Kingdom Divided | 120 |
| Beneath Cursed Sands | 120 |
| Sins of the Father | 150 |
| Dragon Slayer II | 230 |
| Monkey Madness II | 230 |
| Recipe for Disaster | 230 |
| While Guthix Sleeps | 230 |
| Desert Treasure II | 300 |
| Song of the Elves | 300 |

Combined: **2,680 points**. Only completed quests qualify, not partial progress.

## Balance and rollout

All 143 configured rewards sum to **15,070 points**, qualifying for Colonel (10,000) while remaining below Brigadier (17,500). This is the maximum for these goals, assuming no previous custom values; it includes very difficult achievements. The v3 activity policy below adds repeatable and event rewards. Rank intervals remain Recruit 0, Pawn 500, Corporal 1,500, Novice 3,000, Sergeant 6,000, Colonel 10,000, Brigadier 17,500, Admiral 27,500, and Marshal 45,000.

Deploy the commit on Render and let its pre-deploy `init` finish. Remain logged into your linked clan character and allow a definition refresh and another progress sync (typically about one minute). Previously completed zero-reward goals can then receive their one-time rewards. The rewards are not applied to the live database merely by building the local project; deployment and live verification are required. Existing earned rank history, protected ranks, locks, permissions, and Owner-only deductions are unchanged.

## V3 activity policy

V3 doubles unchanged enabled v2 milestone and combat tier rewards. Already-paid goals receive only the positive difference from their historical goal awards, once per member and goal. Owner deductions are not restored. Custom milestone values and disabled goals are preserved.

Existing enabled scoring rules in Drops, Diaries, Combat, Pets, Events, and Milestones receive a one-time doubling, capped at 100,000 points per award. Daily caps stay unchanged. Past repeatable awards are not backpaid. This intentionally increases existing custom scoring rules in those categories; later edits are preserved on redeploy.

New independently reviewed claims (one approval per member per claim type per UTC day):

| Claim | Points |
| --- | ---: |
| Verified rare drop | 250 |
| Verified new pet unlock | 750 |
| Verified achievement diary tier | 250 |
| Clan event attendance | 200 |

An Administrator or Owner other than the requester must approve, with evidence/reason through the existing reward workflow. Staff must reject duplicate claims for the same unlock or attendance; these manual claims have a daily cap, not automatic semantic duplicate detection. Existing rules with these exact keys are preserved. Pending requests whose configured amount changed must be rejected and resubmitted.

Starter clan diary goals now pay 100 for the first accepted combat task, 100 for the first accepted collection unlock, and 200 for five accepted configured drops. These bonuses stack with source awards. They are clan goals, not automatic detection of every OSRS achievement diary tier. A tracked completed goal with no prior award pays on the next relevant sync.

The later [automatic drops and pets catalog](automatic-drops-and-pets.md) enables exact rules for supported loot and named pet collection unlocks. Pet insurance flag correctness has not been verified, so the two existing pet tracking probes remain without automatic point rewards. Manual claim amounts do not imply automatic support for every in-game item or pet.

Validation: Gradle build/serverDist and IDE compilation passed. PostgreSQL integration tests cover v3 top-up idempotence, retained Owner deductions, unchanged daily caps and disabled rules, but require a disposable PostgreSQL database and have not been executed locally. Live deployment and awards remain unverified.

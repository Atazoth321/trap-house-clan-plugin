# Milestone rewards (current policy: v2)

The Owner authorized selecting and applying these values. `init` runs the `milestone-rewards-v1` configuration migration after seeding the catalog. It assigns rewards only to the 143 listed goals whose current reward is zero, preserves previously configured positive rewards and disabled goals, and records a migration marker so redeploying cannot overwrite later edits. The v2 migration increases unchanged enabled policy rewards by approximately 50%, rounded to the nearest five points, and adds a one-time positive ledger top-up for active members whose goals were already paid. Existing award rows are never edited, previous Owner deductions remain intact, and no negative adjustments occur. The normal progress pipeline issues one positive ledger award per completed goal when the member next syncs. Historical awarded goals are never paid again.

## Skills

Each of the 24 supported skills has these cumulative, one-time milestone rewards:

| Level reached | Points |
| --- | ---: |
| 50 | 10 |
| 75 | 15 |
| 90 | 30 |
| 99 | 60 |

That is 115 points per skill, or **2,760** for all skills. Already having level 99 qualifies for the four goals on sync; a temporary boost does not count.

## Total level

| Total level | Points |
| --- | ---: |
| 500 | 10 |
| 750 | 15 |
| 1,000 | 25 |
| 1,250 | 30 |
| 1,500 | 45 |
| 1,750 | 60 |
| 1,900 | 75 |
| 2,000 | 115 |
| 2,100 | 150 |
| 2,200 | 225 |
| 2,300 | 300 |
| 2,376 | 375 |

All thresholds combined: **1,425 points**. These stack with individual skill goals.

## Equipment and capes

| Milestone | Points |
| --- | ---: |
| Dragon defender | 25 |
| Fire cape | 75 |
| Infernal cape | 375 |
| Champion cape | 150 |
| Cursed phalanx | 600 |

Combined: **1,225 points**. Detection requires the configured exact item ID in inventory, equipment, or an opened bank. Alternate cosmetic variants are not automatically equivalent, and untradeability/possession is client evidence rather than independent server verification.

## Combat achievement tiers

| Tier | Points |
| --- | ---: |
| Easy | 15 |
| Medium | 30 |
| Hard | 60 |
| Elite | 115 |
| Master | 190 |
| Grandmaster | 375 |

All tiers combined: **785 points**. Earlier tiers stack with later tiers.

## Quests

| Quest | Points |
| --- | ---: |
| Tree Gnome Village | 10 |
| Animal Magnetism | 15 |
| Bone Voyage | 15 |
| Dragon Slayer I | 15 |
| Fairytale I | 15 |
| The Fremennik Trials | 15 |
| The Grand Tree | 15 |
| Fairytale II | 25 |
| The Fremennik Isles | 30 |
| Monkey Madness I | 30 |
| Regicide | 30 |
| Underground Pass | 30 |
| Desert Treasure I | 40 |
| Lunar Diplomacy | 40 |
| The Fremennik Exiles | 60 |
| A Kingdom Divided | 60 |
| Beneath Cursed Sands | 60 |
| Sins of the Father | 75 |
| Dragon Slayer II | 115 |
| Monkey Madness II | 115 |
| Recipe for Disaster | 115 |
| While Guthix Sleeps | 115 |
| Desert Treasure II | 150 |
| Song of the Elves | 150 |

Combined: **1,340 points**. Only completed quests qualify, not partial progress.

## Balance and rollout

All 143 configured rewards sum to **7,535 points**, qualifying a new member for Sergeant (6,000) but remaining below Colonel (10,000). This is the maximum for these goals, assuming no previous custom values; it includes very difficult achievements. More repeatable and event rewards require separate policy. Rank intervals remain Recruit 0, Pawn 500, Corporal 1,500, Novice 3,000, Sergeant 6,000, Colonel 10,000, Brigadier 17,500, Admiral 27,500, and Marshal 45,000.

Deploy the commit on Render and let its pre-deploy `init` finish. Remain logged into your linked clan character and allow a definition refresh and another progress sync (typically about one minute). Previously completed zero-reward goals can then receive their one-time rewards. The rewards are not applied to the live database merely by building the local project; deployment and live verification are required. Existing earned rank history, protected ranks, locks, permissions, and Owner-only deductions are unchanged.

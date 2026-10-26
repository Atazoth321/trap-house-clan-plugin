# Milestone rewards

The Owner authorized selecting and applying these values. `init` runs the `milestone-rewards-v1` configuration migration after seeding the catalog. It assigns rewards only to the 143 listed goals whose current reward is zero, preserves previously configured positive rewards and disabled goals, and records a migration marker so redeploying cannot overwrite later edits. It does not edit points balances directly or deduct points. The normal progress pipeline issues one positive ledger award per completed goal when the member next syncs. Historical awarded goals are never paid again.

## Skills

Each of the 24 supported skills has these cumulative, one-time milestone rewards:

| Level reached | Points |
| --- | ---: |
| 50 | 5 |
| 75 | 10 |
| 90 | 20 |
| 99 | 40 |

That is 75 points per skill, or **1,800** for all skills. Already having level 99 qualifies for the four goals on sync; a temporary boost does not count.

## Total level

| Total level | Points |
| --- | ---: |
| 500 | 5 |
| 750 | 10 |
| 1,000 | 15 |
| 1,250 | 20 |
| 1,500 | 30 |
| 1,750 | 40 |
| 1,900 | 50 |
| 2,000 | 75 |
| 2,100 | 100 |
| 2,200 | 150 |
| 2,300 | 200 |
| 2,376 | 250 |

All thresholds combined: **1,045 points**. These stack with individual skill goals.

## Equipment and capes

| Milestone | Points |
| --- | ---: |
| Dragon defender | 15 |
| Fire cape | 50 |
| Infernal cape | 250 |
| Champion cape | 100 |
| Cursed phalanx | 400 |

Combined: **815 points**. Detection requires the configured exact item ID in inventory, equipment, or an opened bank. Alternate cosmetic variants are not automatically equivalent, and untradeability/possession is client evidence rather than independent server verification.

## Combat achievement tiers

| Tier | Points |
| --- | ---: |
| Easy | 10 |
| Medium | 20 |
| Hard | 40 |
| Elite | 75 |
| Master | 125 |
| Grandmaster | 250 |

All tiers combined: **520 points**. Earlier tiers stack with later tiers.

## Quests

| Quest | Points |
| --- | ---: |
| Tree Gnome Village | 5 |
| Animal Magnetism | 10 |
| Bone Voyage | 10 |
| Dragon Slayer I | 10 |
| Fairytale I | 10 |
| The Fremennik Trials | 10 |
| The Grand Tree | 10 |
| Fairytale II | 15 |
| The Fremennik Isles | 20 |
| Monkey Madness I | 20 |
| Regicide | 20 |
| Underground Pass | 20 |
| Desert Treasure I | 25 |
| Lunar Diplomacy | 25 |
| The Fremennik Exiles | 40 |
| A Kingdom Divided | 40 |
| Beneath Cursed Sands | 40 |
| Sins of the Father | 50 |
| Dragon Slayer II | 75 |
| Monkey Madness II | 75 |
| Recipe for Disaster | 75 |
| While Guthix Sleeps | 75 |
| Desert Treasure II | 100 |
| Song of the Elves | 100 |

Combined: **780 points**. Only completed quests qualify, not partial progress.

## Balance and rollout

All 143 configured rewards sum to **4,960 points**, qualifying a new member for Novice (3,000) but remaining below Sergeant (6,000). This is the maximum for these goals, assuming no previous custom values; it includes very difficult achievements. More repeatable and event rewards require separate policy. Rank intervals remain Recruit 0, Pawn 500, Corporal 1,500, Novice 3,000, Sergeant 6,000, Colonel 10,000, Brigadier 17,500, Admiral 27,500, and Marshal 45,000.

Deploy the commit on Render and let its pre-deploy `init` finish. Remain logged into your linked clan character and allow a definition refresh and another progress sync (typically about one minute). Previously completed zero-reward goals can then receive their one-time rewards. The rewards are not applied to the live database merely by building the local project; deployment and live verification are required. Existing earned rank history, protected ranks, locks, permissions, and Owner-only deductions are unchanged.

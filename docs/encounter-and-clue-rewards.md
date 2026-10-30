# Encounter and clue rewards

These additions require both the updated backend and a Plugin Hub client update. Backend `init` seeds the `encounter-and-clue-rewards-v1` catalog once, preserving custom prices, disabled rules, historical awards and existing progress. No helper/first-raid reward is added.

| Encounter | Reward | Points |
| --- | --- | ---: |
| Normal or Corrupted Gauntlet | Crystal armour seed | 100 |
| Normal or Corrupted Gauntlet | Enhanced crystal weapon seed | 750 |
| Perilous Moons | Each of the 12 unique armour/weapon pieces | 75 |
| Tormented demons | Burning claw | 200 |
| Tormented demons | Tormented synapse | 350 |
| Araxxor | Araxyte fang | 500 |
| Araxxor | Noxious point, blade or pommel | 100 each |

The three different halberd parts total 300 points. Payment happens on each part's drop, rather than on assembling or possessing a halberd. Repeated parts can also qualify. Each listed item has a default cap of two rewarded observations per member per UTC day, shared across supported encounter variants, including normal/corrupted Hunllef. The client records one observation per distinct item in a loot callback, consistent with existing drops. No points are awarded for buying/trading items or opening a bank.

Gauntlet, Tormented demon and Araxxor observations use RuneLite's `ServerNpcLoot` callback with an explicit NPC allowlist. Their older `NpcLootReceived` callback is excluded to avoid submitting both callbacks for the same encounter. Perilous Moons uses the personal `Lunar Chest` event. Regular, quest-only and unrelated NPC sources are not interchangeable. Identifiers and event names were checked against RuneLite's published API/client sources; live encounter testing is still needed.

## Clues

Clues have their own profile points tile and Points Log category with available rewards, received status, and award history.

| First tracked completion | Points |
| --- | ---: |
| Beginner | 25 |
| Easy | 50 |
| Medium | 75 |
| Hard | 100 |
| Elite | 150 |
| Master | 200 |

All six total 600 points. Each pays once per member and difficulty, using the personal clue-casket loot event. It means the first completion observed while tracking is enabled; lifetime completions before installation are not imported, and an experienced clue solver can qualify on their next tracked completion. Repeated caskets, fresh observation IDs and retries cannot repay the same tier. Clue chat text alone does not trigger first-completion or unique-item rewards. These use semantically unique `COLLECTION` observations without adding progress goals or consuming the remaining 200-goal sync capacity.

The new drop cards use their actual item IDs for RuneLite item sprites. Clue cards and history use the matching difficulty's reward-casket sprite and retain the completion reward title. Category artwork remains available while game sprites load.

The observations retain the existing client-trust model; this is not independent server verification of an unmodified game client. Deploy the backend before distributing the client update, then verify a live reward and its ledger/received status.

## Additional bosses

The later `expanded-encounter-and-clue-rewards-v1` migration adds these defaults independently of the initial catalog, so an existing installation receives the additions without resetting its earlier migration marker. Redeploying preserves custom/disabled rules and historical awards.

| Encounter | Reward | Points |
| --- | --- | ---: |
| Royal Titans | Deadeye or mystic vigour prayer scroll | 100 each |
| Hueycoatl | Hueycoatl hide | 100 |
| Hueycoatl | Dragon hunter wand | 500 |
| Nightmare / Phosani's Nightmare | Each Inquisitor armour piece | 500 |
| Nightmare / Phosani's Nightmare | Inquisitor's mace | 750 |
| Nightmare / Phosani's Nightmare | Each nightmare orb | 750 |

These use the NPC loot event's numeric encounter metadata. RuneLite's unified `LootReceived` event supports its modern server-loot callbacks and its older delayed Nightmare loot handling. The plugin does not additionally submit these sources through `NpcLootReceived`. All supported variants of an item share a default two-awards-per-UTC-day cap. The plugin explicitly depends on RuneLite's built-in Loot Tracker so personal loot callbacks are available.

## Clue uniques and completion milestones

All of these appear in **Clues**, alongside first completions, with available rewards, received status and category-filtered award history. Every card uses the actual game item sprite: unique-item sprites for uniques and the matching reward-casket sprite for completion milestones. The server supplies the sprite item IDs, and the client can resolve them from the observation keys for history entries as well.

| Casket unique | Points per drop |
| --- | ---: |
| Ranger boots | 300 |
| Each of 23 obtainable 3rd age items | 1,000 |
| Each of 20 gilded equipment/tool items | 250 |

Only personal clue-casket contents create unique-item observations, keyed by difficulty and item ID. NPC loot, player loot, inventory changes, trading and buying cannot turn into clue-unique observations. Ranger boots use medium clues; gilded rewards use supported hard/elite tables; 3rd age rewards use supported hard/elite/master tables. Crafted 3rd age felling axes, ornament variants and boxed equipment sets are excluded. Identical item rewards share a two-per-day cap across difficulties. Each distinct item in one casket callback creates one observation, even if multiple copies are stacked.

| Difficulty | 10 completed | 50 completed | 100 completed |
| --- | ---: | ---: | ---: |
| Beginner | 25 | 50 | 75 |
| Easy | 50 | 100 | 150 |
| Medium | 75 | 150 | 225 |
| Hard | 100 | 200 | 300 |
| Elite | 150 | 300 | 450 |
| Master | 200 | 400 | 600 |

Each of the 18 milestones pays once, totaling 3,600 points, separate from the 600 first-completion points. Only the exact game-system completion message is parsed; player chat is excluded by the plugin's message-type gate. The game's lifetime counter qualifies existing players: someone with 120 master clues gets any unpaid 10/50/100 milestones on their next tracked master clue completion. Counter observations do not themselves award unique items. No progress goals are added, and the current 200-goal sync limit is unchanged.

# Automatic drop and pet rewards

[Boss jar rewards](boss-jar-rewards.md) add 16 named collection-log unlocks at 250–500 points, once per member per jar. View obtained jar entries in your own collection-log pages to recognize historical unlocks, or enable collection-log chat notifications for new unlocks. Personal boss drops also qualify, sharing the same once-only reward; inventory scans do not qualify.

The [current reward balance](reward-balance.md) supersedes earlier raid, pet, and manual-claim defaults below. Pet unlocks default to 500 points (Chompy chick: 250; Scurry and Moxi: 350), common raid uniques have lower rewards, supported items share caps across sources, and Barrows pays at most four pieces/day at default prices. Historical points remain intact.

`init` installs the base catalog, the additive `automatic-rewards-v2` expansion, and `additional-pet-rewards-v1` once. It enables 118 named drop items and 72 named pet collection unlocks without replacing existing rules, including disabled rules. Subsequent deployments preserve staff edits. The catalog is in `server/src/main/java/com/traphouse/server/AutomaticRewards.java`.

## How scoring works

### Easy live drop test

After the updated server deploys, kill a regular cow in the cow field northeast of Lumbridge and receive its cowhide. The enabled `Cowhide (easy test drop)` rule pays **1 point**, up to 10 times per supported cow NPC variant per member per UTC day (four variants, at most 40 points/day). Calves are excluded. Buying, trading or withdrawing a cowhide does not count; it must be your personal NPC loot event. After 10 rewarded kills of the same variant that day, further kills earn no test points.

This uses the existing plugin's NPC loot tracking and only requires a server update. Enable ranking sync, sign in as the linked clan member and allow the immediate background sync to confirm the award after the kill. Look under **Profile → Points from: Drops** for the award. The `easy-test-cowhide-v1` migration adds only these four rules and preserves existing disabled/custom rules and all previous awards. Subsequent deployments do not restore a deliberately disabled or removed test rule. The one-time `easy-test-cowhide-limit-v2` migration expands unchanged enabled 1-point/1-per-day cowhide defaults to 10 per day; custom rules and previous awards are preserved. This must be deployed to the ranking server before repeat testing works.

Members must enable ranking, authenticate their linked character, and be recognized as an active TRAP HOUSE member. The plugin sends observations; the backend selects points, enforces daily limits and character binding, and records positive ledger entries. Client-specified point amounts are rejected. Earned rank retention, protected leadership ranks, and Owner-only deductions are unchanged. Actual in-game ranks are never changed.

NPC drops use exact NPC/item pairs from RuneLite's `NpcLootReceived`. Only the personal loot event is submitted, once per distinct item in each callback; item quantity does not multiply points. Buying, trading, banking, or seeing someone else's drop message does not trigger this path.

Raids use RuneLite Loot Tracker's personal reward-container `LootReceived` event for Chambers of Xeric, Theatre of Blood, Tombs of Amascut, and Barrows. Keep **Loot Tracker enabled**. Only EVENT records from those four source names are accepted by this plugin path; NPC records are ignored here to avoid double submission alongside NPC loot tracking. Mode/source names outside this exact allowlist are not covered.

Supported automatic drop items allow **two awards per member per UTC day per item ID**, shared across source/NPC phase keys. Barrows additionally allows four awarded pieces total per day. Numeric custom caps are retained; unlisted custom loot rules keep per-source limits. Exact request retries cannot pay twice. A later genuine drop gets a new request ID and can pay again within its cap. Items outside the enabled catalog earn no automatic drop points.

## Drop rewards

### Wilderness and lower-tier clue expansion (55 items)

The additive `wilderness-and-clue-rewards-v1` migration adds the following exact personal-loot rules. Existing enabled/disabled rules, custom prices and caps, and award history are preserved.

| Source | Rewards | Points each |
| --- | --- | ---: |
| Ordinary Revenant Cave monsters | Uncharged Viggora's chainmace / Craw's bow / Thammaron's sceptre | 300 / 350 / 250 |
| Ordinary Revenant Cave monsters | Amulet of avarice / ancient crystal | 100 / 50 |
| Ordinary Revenant Cave monsters | Ancient emblem / totem / statuette | 25 / 40 / 75 |
| Ordinary Revenant Cave monsters | Ancient medallion / effigy / relic | 100 / 150 / 250 |
| Ordinary Revenant Cave monsters | Uncharged bracelet of ethereum | 5 |
| Beginner clues | Mole/frog slippers, bear/demon feet, jester cape, shoulder parrot, trimmed monk robe top/bottom, all three sandwich lady pieces | 10 |
| Easy clues | Flared trousers / pantaloons / sleeping cap | 50 / 25 / 25 |
| Easy clues | Black pickaxe / ham joint / team capes zero, X and i | 10 / 75 / 25 |
| Easy, medium and hard clues | All four pages for Saradomin, Zamorak, Guthix, Bandos, Armadyl and Ancient books | 10 |

Clue items and bracelets pay once per item per member per UTC day. Other Revenant items pay up to twice per item/day. Caps are shared across supported monsters and clue tiers; quantities do not multiply points. Charged player-owned weapons, purchases and inventory scans do not qualify. Only the listed ordinary Revenant NPC IDs are covered; Maledictus and unlisted variants are excluded. Clue rewards require RuneLite Loot Tracker. Deploy the updated backend with successful `init` to enable these rewards. Unit tests cover duplicate keys, shared item caps, clue parsing, and exclusion of charged variants; live loot callbacks and PostgreSQL migration execution have not been verified from this workspace.

### Broad drop expansion (52 items)

The additive `broad-drop-rewards-v1` migration installs these rewards without overwriting existing rules or awarding historical backpay:

| Source | New rewards | Points each |
| --- | --- | ---: |
| Dagannoth Supreme / Prime | Seercull / mud battlestaff | 50 |
| K'ril Tsutsaroth | Steam battlestaff | 50 |
| Thermonuclear smoke devil | Smoke battlestaff | 75 |
| Fossil Island wyverns | Wyvern visage | 350 |
| Alchemical Hydra | Hydra's heart, fang, eye | 50 |
| Hard clues | Zamorak, Guthix, Saradomin, Ancient, Bandos and Armadyl bracers/coifs | 15 |
| Hard clues | All six blessed d'hide bodies/chaps | 25 |
| Hard clues | All six blessed d'hide boots/shields | 35 |
| Medium clues | Wizard boots / holy sandals / spiked manacles | 50 / 100 / 100 |
| Hard clues | Robin hood hat | 100 |
| Elite clues | Rangers' tunic, rangers' tights, holy wraps / Fremennik kilt | 100 / 75 |

The 44 clue items pay once per item per member per UTC day and appear under **Clues** in the reward browser. The eight boss items pay up to twice per item per day, shared across supported NPC variants. Keep RuneLite Loot Tracker enabled for personal clue reward-container observations. Buying, trading, banking and owning these items do not qualify. Deploy the updated backend with successful `init` to enable this catalog; no new client observation path is needed. Unit tests check catalog uniqueness, source restrictions, clue item parsing and shared-cap registration. Live loot callbacks and PostgreSQL migration execution have not been verified from this workspace.

### Accessible drop expansion

The additive `accessible-drop-rewards-v1` migration adds 20 items. Each pays once per member per UTC day, shared across its supported NPC sources. Existing rewards and staff customizations are preserved; previously tracked drops receive no backpay. Deploy the updated backend and run `init` (the Render pre-deploy command) to install them.

| Drops | Points each |
| --- | ---: |
| Rune scimitar (fire giants) | 2 |
| Dragon boots (spiritual mages), granite maul (gargoyles), dragon med helm (metal dragons), leaf-bladed sword (turoths/kurasks) | 10 |
| Dragon platelegs/plateskirt (metal dragons) | 15 |
| Black mask (10) (cave horrors), granite legs (skeletal wyverns), granite boots (Fossil Island wyverns) | 20 |
| Dark/light mystic hats, gloves and boots (their respective Slayer monsters) | 5 |
| Dark/light mystic robe tops and bottoms (their respective Slayer monsters) | 10 |

Only personal NPC loot observations qualify. Purchases, trades, bank withdrawals and inventory ownership do not award points. This expansion preserves the existing higher rewards for boss drops such as abyssal whips, occult necklaces, berserker rings and Sarachnis cudgels.

### Visual reward browser

The points log now extends the drop-card layout to **All**, **Pets**, **Milestones**, **Combat**, **Diaries**, **Challenges**, **Events**, **Collection**, and **Misc**. The **Available rewards** tab lists enabled point-paying rules and automatic goals in the selected category; **Award history** keeps the existing ledger view. Categories without a configured reward have an empty catalog. Efficiency awards remain visible in award history; their gain-based rates are shown on the profile rather than represented as collectible rewards.

Cards show the current point value, item/pet sprite when available (or a category icon), one-time versus daily limits, and **Received / Not received yet** status. Received cards have a green outline, award count, and historical points earned. Use the status filter to show rewards already received or still missing. Status comes from the selected member's positive ledger awards, not merely owning an item or completing an unrewarded activity. Disabled and zero-point goals are omitted; staff-reviewed rewards show their review requirement.

This requires both a backend deployment and an updated Plugin Hub client. The backend adds `availableRewards` to points-log responses while retaining `availableDrops` for older clients; no schema migration is needed for this browser update. Generate an offline, illustrative preview with `gradlew.bat previewRewards` (saved under `build/reward-catalog-preview.png`).

Open **Profile → Points from: Drops → Available drops** to browse the current enabled reward rules before earning a drop. Cards show actual item icons where RuneLite can resolve them, points per drop, automatic versus staff-reviewed rewards, and daily caps. Search filters item names; Highest points sorts the catalog by reward. Matching NPC phase rules are combined into one item card. Award history remains in its own tab. The catalog uses the live database rule amounts, so disabled rewards are omitted and staff edits appear on the next reload. Deploy the updated backend and restart the updated plugin to use this view.

Choose **All drops**, **Not received yet**, or **Received** to filter the catalog. Received cards show the member's recorded award count and total points actually earned, independent of the current reward price or Owner deductions. NPC phase awards for matching item cards are combined. Duplicate observation retries do not increase the count. These statuses reflect recorded scoring history, not a historical import of the player's collection log; untracked past drops cannot be inferred. Staff-reviewed generic reward claims are labeled separately and reflect their own recorded approvals.

The v2 expansion adds **68 distinct items**, taking the catalog from 50 to **118**. Existing deployments receive only the additional source/item rules, preserving customized/disabled rules and leaving previously removed base rules removed. No historical drop backpay is issued. New reward groups:

| Encounter | Added rewards | Points |
| --- | --- | ---: |
| Barrows | All 24 undamaged equipment rewards | 50 each |
| Dagannoth Kings | Berserker/archers ring, seers ring, warrior ring, dragon axe | 200 / 150 / 100 / 100 |
| Corporeal Beast | Elysian, arcane, spectral sigils / spirit shield / holy elixir | 1,000 / 750 / 600 / 100 / 150 |
| King Black Dragon | Draconic visage, dragon pickaxe | 300 / 200 |
| Kalphite Queen | Dragon chainbody, dragon 2h sword | 200 / 100 |
| Sarachnis | Sarachnis cudgel | 200 |
| God Wars bosses | Armadyl/Bandos/Zamorak hilts, all three godsword shards | 350 / 300 / 250 / 100 |
| Nex | Damaged Torva helm/body/legs, nihil horn, ancient hilt | 750 / 900 / 800 / 1,000 / 500 |
| Phantom Muspah | Venator shard, ancient icon | 350 / 150 |
| Wilderness bosses | Voidwaker pieces, weapon upgrade components, boss rings, dragon pickaxe, dragon 2h sword | 600 / 300 / 200–300 / 200 / 100 |
| Desert Treasure II bosses | Respective vestiges, Virtus mask/top/bottom, chromium ingot | 600 / 500 / 600 / 600 / 150 |

Barrows requires the updated plugin and enabled RuneLite Loot Tracker, just like the raid container path. The catalog covers configured encounter NPC variants only; quest, arena, and other unlisted copies are not implied to be supported. Item/NPC constants and Barrows event naming were checked against cached RuneLite API/client sources; actual new encounter callbacks and live database deployment have not been verified in-game.

| Encounter | Supported items | Points per item |
| --- | --- | ---: |
| Abyssal demons | Abyssal whip | 100 |
| General Graardor | Bandos chestplate, tassets / boots | 300 / 150 |
| Commander Zilyana | Armadyl crossbow / Saradomin hilt | 350 / 300 |
| Kree'arra | Armadyl chestplate, chainskirt / helmet | 300 / 200 |
| K'ril Tsutsaroth | Zamorakian spear | 250 |
| Zulrah | Tanzanite fang, magic fang / serpentine visage | 250 / 200 |
| Vorkath | Draconic visage / skeletal visage / dragonbone necklace | 300 / 500 / 200 |
| Kraken | Full trident of the seas, kraken tentacle | 150 |
| Cerberus | Primordial / pegasian, eternal / smouldering stone | 300 / 200 / 150 |
| Thermonuclear smoke devil | Occult necklace | 150 |
| Alchemical Hydra | Hydra's claw / hydra leather | 500 / 300 |
| Chambers of Xeric | Twisted bow | 1,000 |
| Chambers of Xeric | Elder maul, Kodai insignia, ancestral pieces, Dinh's bulwark, dragon hunter crossbow, dexterous/arcane scrolls, dragon claws | 150–750; see the balance guide for individual rewards |
| Theatre of Blood | Uncharged scythe of vitur | 1,000 |
| Theatre of Blood | Ghrazi rapier, uncharged sanguinesti staff, avernic hilt, justiciar pieces | 300–600; see the balance guide for individual rewards |
| Tombs of Amascut | Uncharged Tumeken's shadow | 1,000 |
| Tombs of Amascut | Osmumten's fang, Elidinis' ward, Lightbearer, Masori pieces | 200–500; see the balance guide for individual rewards |

## Pets

The 72 named pet collection unlocks pay **500 points once per member per pet**, except **Chompy chick (250)** and **Scurry and Moxi (350 each)**. Examples include Pet kraken, Hellpuppy, Vorki, Ikkle hydra, Olmlet, Lil' zik, Tumeken's guardian, Heron, Beaver, Rocky, and Bloodhound. See the catalog for the complete exact-name allowlist. No wildcard pet messages are rewarded.

Enable the game's **collection log notification chat messages**. A supported `New item added to your collection log: <pet name>` system message triggers scoring. The generic funny-feeling message alone cannot identify the pet and does not score. Repeated collection messages and new request IDs cannot pay for the same named pet twice, including on another day. Recolours, transmogs, and duplicate pet drops do not receive separate rules.

This tracks newly observed collection unlocks, not the complete historical collection log. Pets owned before tracking do not automatically receive backpay. The existing unverified insurance probes remain tracking-only. Staff-reviewed claims still exist for historical or unsupported rewards; reviewers must reject claims already paid automatically. There is no automatic deduplication between those manual claims and automatic awards.

## Deployment and verification

1. Deploy the backend commit on Render; its pre-deploy `init` must succeed.
2. Run the updated plugin in a freshly started developer client. Backend deployment alone cannot add raid observation support to an old client.
3. Authenticate as the linked member, enable ranking and Loot Tracker, and enable collection-log chat notifications.
4. Receive a supported personal drop or new pet unlock. Check the Drops/Pets points log and balance after sync. A real in-game award has not been verified locally.

Gradle build, plugin/server unit tests, and IDE compilation passed. Tests cover raid event filtering, per-callback item deduplication, pet message parsing, and catalog bounds/uniqueness. PostgreSQL tests cover automatic awards, permanent pet deduplication, drop caps/retries, rejection of client-supplied points, and preservation of disabled/custom rules; they require a disposable database and have not been run locally.

These are authenticated client observations, not independent Jagex proof. A modified client can fabricate observations. The existing server authentication, membership checks, allowlists, limits, and immutable award records remain the enforcement boundary.


Barrows reward adjustment: the one-time deployment migration lowers unchanged enabled 150-point Barrows rules to 50 points per piece. Disabled rules, other custom amounts, and daily limits are preserved. Historical awards are retained at their original value; no points are deducted, and subsequent operator edits survive redeployment. This database migration has integration coverage but has not been executed against the live service from this workspace.

The additive slayer-drop-rewards-v1 migration extends abyssal whips (100 points, two/day) to greater Abyssal demons and the additional Catacombs variant. It adds crawling hands (10), cockatrice/basilisk heads (15), kurask heads (25), abyssal heads (50), and dust battlestaves (25), once per item/day. Skeletal wyvern draconic visages pay 300 points with the existing shared two/day visage cap. Accessible-drop-balance-v2 raises only unchanged enabled original accessible-drop defaults; custom values, disabled rules and historical awards are preserved.

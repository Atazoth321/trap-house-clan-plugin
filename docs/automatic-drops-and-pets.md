# Automatic drop and pet rewards

`init` installs the `automatic-rewards-v1` catalog once. It enables 50 named drop items and 55 named pet collection unlocks without replacing existing rules, including disabled rules. Subsequent deployments preserve staff edits. The catalog is in `server/src/main/java/com/traphouse/server/AutomaticRewards.java`.

## How scoring works

Members must enable ranking, authenticate their linked character, and be recognized as an active TRAP HOUSE member. The plugin sends observations; the backend selects points, enforces daily limits and character binding, and records positive ledger entries. Client-specified point amounts are rejected. Earned rank retention, protected leadership ranks, and Owner-only deductions are unchanged. Actual in-game ranks are never changed.

NPC drops use exact NPC/item pairs from RuneLite's `NpcLootReceived`. Only the personal loot event is submitted, once per distinct item in each callback; item quantity does not multiply points. Buying, trading, banking, or seeing someone else's drop message does not trigger this path.

Raids use RuneLite Loot Tracker's personal reward-container `LootReceived` event for Chambers of Xeric, Theatre of Blood, and Tombs of Amascut. Keep **Loot Tracker enabled**. Only EVENT records from those three source names are accepted by this plugin path; NPC records are ignored here to avoid double submission alongside NPC loot tracking. Mode/source names outside this exact allowlist are not covered.

Each enabled drop rule allows **two awards per member per UTC day per exact source/item key**. NPC phase variants have separate keys and therefore separate caps; this is not a global two-drop daily limit. Exact request retries cannot pay twice. A later genuine drop gets a new request ID and can pay again within its cap. Items outside the catalog earn no automatic drop points.

## Drop rewards

Open **Profile → Points from: Drops → Available drops** to browse the current enabled reward rules before earning a drop. Cards show actual item icons where RuneLite can resolve them, points per drop, automatic versus staff-reviewed rewards, and daily caps. Search filters item names; Highest points sorts the catalog by reward. Matching NPC phase rules are combined into one item card. Award history remains in its own tab. The catalog uses the live database rule amounts, so disabled rewards are omitted and staff edits appear on the next reload. Deploy the updated backend and restart the updated plugin to use this view.

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
| Chambers of Xeric | Elder maul, Kodai insignia, ancestral pieces, Dinh's bulwark, dragon hunter crossbow, dexterous/arcane scrolls, dragon claws | 500 |
| Theatre of Blood | Uncharged scythe of vitur | 1,000 |
| Theatre of Blood | Ghrazi rapier, uncharged sanguinesti staff, avernic hilt, justiciar pieces | 500 |
| Tombs of Amascut | Uncharged Tumeken's shadow | 1,000 |
| Tombs of Amascut | Osmumten's fang, Elidinis' ward, Lightbearer, Masori pieces | 500 |

## Pets

Each of the 55 named pet collection unlocks pays **750 points once per member per pet**. Examples include Pet kraken, Hellpuppy, Vorki, Ikkle hydra, Olmlet, Lil' zik, Tumeken's guardian, Heron, Beaver, Rocky, and Bloodhound. See the catalog for the complete exact-name allowlist. No wildcard pet messages are rewarded.

Enable the game's **collection log notification chat messages**. A supported `New item added to your collection log: <pet name>` system message triggers scoring. The generic funny-feeling message alone cannot identify the pet and does not score. Repeated collection messages and new request IDs cannot pay for the same named pet twice, including on another day. Recolours, transmogs, and duplicate pet drops do not receive separate rules.

This tracks newly observed collection unlocks, not the complete historical collection log. Pets owned before tracking do not automatically receive backpay. The existing unverified insurance probes remain tracking-only. Staff-reviewed claims still exist for historical or unsupported rewards; reviewers must reject claims already paid automatically. There is no automatic deduplication between those manual claims and automatic awards.

## Deployment and verification

1. Deploy the backend commit on Render; its pre-deploy `init` must succeed.
2. Run the updated plugin in a freshly started developer client. Backend deployment alone cannot add raid observation support to an old client.
3. Authenticate as the linked member, enable ranking and Loot Tracker, and enable collection-log chat notifications.
4. Receive a supported personal drop or new pet unlock. Check the Drops/Pets points log and balance after sync. A real in-game award has not been verified locally.

Gradle build, plugin/server unit tests, and IDE compilation passed. Tests cover raid event filtering, per-callback item deduplication, pet message parsing, and catalog bounds/uniqueness. PostgreSQL tests cover automatic awards, permanent pet deduplication, drop caps/retries, rejection of client-supplied points, and preservation of disabled/custom rules; they require a disposable database and have not been run locally.

These are authenticated client observations, not independent Jagex proof. A modified client can fabricate observations. The existing server authentication, membership checks, allowlists, limits, and immutable award records remain the enforcement boundary.

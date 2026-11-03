# Beginner, midgame and skilling drop rewards

| Drop | Source | Points |
| --- | --- | ---: |
| Scurrius' spine | Scurrius | 50 |
| Hill giant club | Obor | 100 |
| Bryophyta's essence | Bryophyta | 150 |
| Broken zombie axe | Armoured zombies | 150 |
| Warped sceptre (uncharged) | Warped creatures | 100 |
| Brine sabre | Brine rats | 75 |
| Leaf-bladed battleaxe | Kurasks / King kurasks | 100 |
| Dragon sword | Wyrms / Shadow wyrms | 150 |
| Dragon harpoon | Wyrms / Shadow wyrms | 200 |
| Drake's claw | Drakes / Guardian drakes | 150 |
| Drake's tooth | Drakes / Guardian drakes | 150 |
| Bottomless compost bucket | Hespori | 150 |
| Dark claw | Skotizo | 100 |
| Odium shard 1, 2, 3 | Chaos Fanatic, Crazy archaeologist, Scorpia respectively | 100 each |
| Malediction shard 1, 2, 3 | Chaos Fanatic, Crazy archaeologist, Scorpia respectively | 100 each |
| Tome of fire (empty) | Wintertodt | 150 |
| Tome of water (empty) | Tempoross | 150 |
| Fish barrel | Tempoross | 100 |
| Tackle box | Tempoross | 75 |
| Abyssal needle | Guardians of the Rift | 150 |
| Abyssal lantern | Guardians of the Rift | 200 |

These are 21 reward groups covering 25 distinct items. Each item pays at most twice per UTC day, shared across eligible NPC variants and container aliases. A stack counts as one award. Unique item types have independent limits.

Combat drops use the existing NPC loot events. Wintertodt supply crates and the reward cart map to one `wintertodt` source; Tempoross reward pool loot maps to `tempoross`; Guardians of the Rift searches retain their event name. All three use RuneLite's actual EVENT loot records. Inventory possession, repairing the zombie axe, charging a tome, buying an item, and ordinary player messages do not award these points. Only the broken axe, uncharged sceptre and empty tomes in the drop table qualify.

The source allowlist excludes the Drake duck, quest Hespori, inactive/Varrock quest zombies, decorative Kurasks and background Warped terrorbirds. Individual Wilderness shard numbers must match their actual boss.

Run the existing backend `init` command to apply `beginner-and-skilling-drop-rewards-v1`. Repeated deployment preserves staff-edited amounts, disabled rules and historical points. Publish the new client for skilling-container support. No old inventory holdings or historical drops are backfilled. Local tests cover the catalog, event mapping, payments, replay and caps; live gameplay callbacks still need confirmation.

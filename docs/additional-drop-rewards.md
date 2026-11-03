# Additional drop rewards

| Drop | Points |
| --- | ---: |
| Basilisk jaw | 250 |
| Zenyte shard from demonic gorillas | 250 |
| Dragon warhammer from Lizardman shamans | 400 |
| Abyssal dagger from abyssal demons | 200 |
| Bludgeon spine, claw or axon from Unsired exchange | 200 each |
| Abyssal dagger from Unsired exchange | 200 |
| Granite hammer from Grotesque Guardians | 75 |
| Giant egg sac (full) from Sarachnis | 50 |
| Dark bow from dark beasts | 100 |
| Blood shard from Vyrewatch Sentinels | 200 |
| Crystal tool seed from Zalcano | 200 |
| Uncut onyx from Zalcano | 100 |

Each item has a two-award daily limit shared across its allowlisted NPC variants, including existing equivalent item rules. An item stack produces one observation, not one reward per quantity. Different items retain independent limits. Purchases, inventory possession, pickpocketing and ordinary chat do not generate these drop rewards. The NPC allowlist excludes raid shamans, quest sentinels, noncombat gorillas, Sire transmogs, mounted demons and Zalcano cutscenes.

## Sire exchange rewards

The catalog also adds **Bludgeon spine**, **Bludgeon claw**, **Bludgeon axon**, and the exchanged **Abyssal dagger**, each at 200 points, under `chest:unsired:<item-id>`. These exchange rewards are enabled. Unsired drops themselves are disabled and award no new points. RuneLite reports the actual Font of Consumption exchange through its `Unsired` loot event. The direct abyssal-demon dagger reward remains enabled.

The one-time sire-exchanged-uniques-v2 migration switches existing installations to this policy and preserves historical points. An Unsired rewarded before the switch can still yield a paid unique afterwards; outstanding items are not linked to old drop events. No automatic inventory or historical backfill is performed.

## Deployment

The existing backend `init` command seeds these rules through `additional-drop-rewards-v1` and `sire-exchanged-uniques-v2`, preserving staff edits, disabled rules and historical awards. A new plugin release adds the Zalcano container and Unsired exchange event handling. Other listed NPC drops use the existing NPC loot callback. Drop and ledger checks have been tested locally, but actual live gameplay callbacks still need confirmation.

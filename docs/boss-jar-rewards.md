# Boss jar rewards

Each named jar pays once per member from either a personally tracked boss drop or viewing an obtained entry in the player's own collection log. Jar of miasma also qualifies through the personal Unsired exchange loot event. Both routes use the same canonical reward key, so receiving a drop and then opening the collection log cannot pay twice. Browse the relevant boss pages or collection-log search results so the game renders those entries; opening one page does not load every page. Positive obtained quantities qualify, including jars earned before installing the plugin. Empty slots, logs belonging to POH hosts, inventory/bank scans, purchases and trades do not qualify. New collection-log unlock notifications also remain supported. Jars appear under Drops with a one-time reward label and received status.

These are configured reward tiers chosen for rarity and encounter difficulty, rather than a fixed conversion from drop odds:

| Points | Jars |
| ---: | --- |
| 250 | Dirt, eyes, smoke |
| 350 | Swamp, souls, stone, venom, feathers |
| 500 | Sand, miasma, darkness, decay, chemicals, dreams, spirits, light |

Jar names and item IDs use RuneLite 1.13.5 game data. Only the 16 named jar unlocks qualify; the quest item Jar of congealed blood is excluded.

Deploy the backend and run `init` to apply `boss-jar-collection-rewards-v2`. It adds canonical collection rules, carries over custom prices and disabled settings, disables separate legacy jar drop rules, and preserves past awards. The server maps allowlisted personal jar drop observations to those canonical rules, so both drop and collection-log routes honor the same prices and enabled settings. Missing legacy sources remain disabled. Jars already paid through drops remain marked received and cannot repay via a collection notification. Later initialization preserves operator edits. Publish the updated plugin for historical collection-log recognition. The client passively observes the collection-item renderer (script 4100), canonicalizes IDs through enum 3721, ignores zero quantities and POH host logs, and retries queued observations during progress checks. It does not open pages or run game scripts.

Previously earned jars qualify when their obtained collection-log entries are viewed. The existing authenticated client observation trust model applies. Unit tests cover the named catalog, client message parser, all 16 historical jar entries, empty slots, host-log exclusion, canonical IDs, duplicate views, queue retry and session/character resets. PostgreSQL integration tests cover permanent deduplication, retry behavior, legacy award compatibility, both award orders, catalog status and safe seeding; they require a disposable database. Live in-game drops and log recognition still require verification.

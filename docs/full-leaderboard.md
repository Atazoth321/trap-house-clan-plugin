# Full clan browser and leaderboards

Open **Leaderboard > Open full leaderboard**. The native window has the reference layout: five main modes, ten statistic buttons, RuneScape-style text, a gold title, brown/olive tables, green scores, search, Find me, Refresh, and help. Each account-group table scrolls independently.

## Views

| View | Data and behavior |
| --- | --- |
| Collection log | Bosses, Raids, Clues, Minigames, and Other category tabs; category sidebar; item sprites, quantities, missing/unknown slots, completed-page highlights. Enter a clan character name and press Enter. |
| Luck | Completed logs ordered by the lowest frozen completion counter. Closest unfinished logs fill remaining places when fewer than ten have completed the category. |
| Deserved | Completed logs ordered by the highest frozen completion counter, with the same unfinished-log fallback. |
| Dryboard | Current matching category counter minus the last recorded matching collection drop counter. Duplicate drops reset the streak too. Categories without comparable counters are omitted. The category selector supports text suggestions. |
| Leaderboards | Collection log, Pets, Points, Greenlogs, Total XP, Monthly clog, Monthly points, Clues, Combat points, and Boss kills. Clues has all-tier and individual-tier buttons; Boss kills has an all-bosses or specific-boss selector. |

Global scores and Dryboard have separate **Mains**, **Ironman + GIM + UIM**, and **Hardcore Ironman** tables. Hardcore group ironmen are in the Hardcore table. Unsynced account types remain in a separate unclassified table rather than being presented as Mains. Tied scores share their rank. Searching and sorting retain each original group position. Double-clicking an account or pressing Enter opens its collection log for all visitors.

All ten score leaderboards and all collection browser views are public, including individual collection pages, catalogue details, Luck, Deserved and Dryboard. Uploading observations still requires an active member credential. These browser routes never change points, permission, character links, or rank assignments.

## Sources and history

Ranking sync now includes account type, total XP, the game collection counter, CA points, clue-tier counters, collection-page definitions, observed item quantities, and supported page counters. Members must opt in and use their linked character in TRAP HOUSE. The tracker passively reads game caches and script events from the member's own collection book; it rejects a POH host's book and never opens interfaces, sends game input, or runs scripts. Opening/browsing the in-game collection book or using its search supplies historical items. Unseen items stay unknown. Up to five bounded pages upload per normal sync, so a complete catalogue takes multiple syncs. Observations from later sessions merge without deleting known items.

The existing server-side Wise Old Man importer also imports account type, total XP, boss kill counts, and clue scores from its public player snapshot. Unranked negative sentinels are omitted. A client-observed account type is preserved because the public API does not distinguish every group-iron variant. The background importer processes accounts gradually and does not request player updates.

Points and monthly points use the server ledger. Monthly points are net point entries received during the current UTC calendar month. Monthly clog is the highest observed collection count minus the first synced count of that UTC month; earlier gains cannot be reconstructed. Greenlogs count fully obtained synced collection pages, not an inferred historical total. Pets use the fully observed All pets page. CA points use the game's achievement-point counter, not clan reward points. XP is a 64-bit total.

Completion counters freeze on the first observation of a completed page and are marked `~` because a historical sync cannot prove the exact completion counter. Pages without meaningful counters remain greenlogged without inventing a numeric score; when no comparable counter exists for the entire category, completed logs tie at #1. Dry streaks require a matching source/item drop observed by this plugin and a comparable current counter. Drops before installation or while offline cannot establish a last-drop counter. Live gameplay coverage still needs verification with members.

Each window caches up to 64 selections. Refresh bypasses the selected client/server cache and does not trigger a game sync. Server view caches expire after 15 seconds. Late responses from a previous selection, character/configuration generation, or closed window are discarded.

## Deployment and validation

Deploy the backend and run the existing `init` pre-deploy command. It adds `browser_statistics`, `browser_collection_pages`, and `browser_monthly_collection` through `clan-browser.sql`, preserving existing awards. A restricted runtime role needs SELECT, INSERT and UPDATE on those three tables in addition to its existing grants. Publish the client through Plugin Hub; a local build does not distribute it.

`GET /v1/browser?mode=leaderboard&metric=points` is anonymous. The same endpoint accepts the ten metric keys, optional clue-tier/boss `category`, and `refresh=1`. All other browser GET modes are also anonymous. `POST /v1/browser/sync` requires the existing authenticated member/character binding and bounded JSON body. Category names, item IDs, counts, account types and statistic fields are validated; observations cannot supply reward points or a target member ID. As with other client observations, a modified client can fabricate its own browser statistics; the data is not game-client attestation.

Run `gradlew.bat previewFullLeaderboard` for an offline image of the actual Swing window at `build/full-leaderboard-preview.png`. Its records are labelled sample data. Tests cover every statistic/mode, independent account groups, numeric sorting, preserved positions, Find me, refresh/cache behavior, request races, private/public access, passive item capture, source-specific drop tracking, real PostgreSQL merging, frozen counters, monthly gains and credential revocation.

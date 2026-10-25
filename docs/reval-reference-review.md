# Reval reference review and TRAP HOUSE implementation plan

Reviewed 2026-10-25, read-only, from `C:\Users\osrst\OneDrive\Desktop\reval-cc-plugin-master`. No Reval source, branding, assets, credentials, or endpoints are copied into TRAP HOUSE. Findings describe the downloaded source, not a verified running Reval deployment.

| Area | Evidence in the reference | Actual status |
| --- | --- | --- |
| Sidebar | `ui/RevalPanel.java` constructs and initializes Profile, Ranking, Events, Competitions, Achievements, Diaries and Leaderboard panels | Client implementation exists despite the README's “Sidepanel UI (Planned)” heading |
| Ranks and points | `ui/RankingPanel.java:load/buildContent`, `api/points/PointsResponse.java` | Fetches rank requirements, monthly maintenance, extra requirements and grouped point sources; displays server responses |
| Profile | `ui/ProfilePanel.java:loadAccount/buildContent/buildPointsSection` | Account lookup, point breakdown, progress to next rank, milestones and cached redraws are implemented |
| Leaderboard | `ui/LeaderboardPanel.java:refresh/buildLeaderboard` | API-backed standings, local search and opening another account's profile are implemented |
| Achievement tracking | `combatachievements/CombatAchievementManager.java`, `collectionlog/CollectionLogManager.java`, `notifiers/CombatAchievementNotifier.java`, `notifiers/CollectionNotifier.java` | Managers read game state; notifiers parse system messages and submit observations. CA suffixes include point counts. Full collection sync is user initiated |
| Loot and other events | `notifiers/LootNotifier.java`, other `notifiers/*`, registration and dispatch in `RevalClanPlugin.java` | NPC/event loot, buffered collection correlation, raid reward suppression and self-drop/gear-swap guards exist; also levels, pets, quests, clues, diaries, deaths and other notifications |
| API transport | `api/RevalApiService.java`, `util/WebhookService.java` | Async OkHttp JSON reads and writes; webhook gzip/multipart and optional screenshots; administrator requests have a separate credential header |
| Membership | `util/ClanValidator.java`, `util/ClanMembership.java` | Clan settings/channel checks, login-scoped membership cache. Comments identify the backend roster as the final authority |
| Synchronization | `PlayerDataCollector.java`, `util/SyncStateManager.java`, `session/SessionTracker.java` | Full sync, login/logout boundary payloads, SHA-256 state fingerprints, server acknowledgement, stale-state repair and session heartbeat handling exist. Collection log and KCs are deliberately excluded from the boundary fingerprint |
| Announcements | `util/AnnouncementService.java` | Login fetch, version hints, periodic polling, acknowledgements and generation guards exist |
| Administrator rank workflow | `ui/admin/PendingRankupsPanel.java`, `api/RevalApiService.java:actualizeRankChange` | Pending rank changes and an API action exist. This is not evidence that a client performs an in-game promotion |
| Administrator permissions | `ui/admin/AdminManager.java:setSession` | Explicit TODO: permissions filtering is future work |
| Scoring/database enforcement | No backend or database schema in this download | Cannot verify PostgreSQL, server point calculations, deduplication, authorization enforcement or reward approval from client DTOs alone |
| Tasks and diaries | API DTOs/endpoints and diary/achievement panels exist | API integration exists; definitions, scoring and backend execution cannot be verified here. A README “Planned” label alone does not establish absence |

## Staged implementation

1. Inspect existing TRAP HOUSE code and establish a passing build. Preserve Wise Old Man competition selection, Boss/Skill panels, alerts, links, prizes and overlays.
2. Add original Swing Profile, Ranks and Leaderboard tabs, populated by one server dashboard snapshot. Show eligible rank, next threshold, scoring sources and recent awards. Keep UI changes on the EDT.
3. Observe supported RuneLite NPC loot and system-message CA/collection unlocks. Require opt-in and clan membership; use background HTTP, bounded queues, stable retry IDs, timeout/rate limits and account/config generation guards. Do not automate UI interactions, game actions, collection scanning or promotions.
4. Run the original REST service separately. PostgreSQL holds an administrator-maintained verified roster, expiring hashed member credentials, scoring rules, thresholds, immutable award ledger, semantic unlock deduplication, daily caps, independent manual-reward approval, audit records and a transactional Discord outbox.
5. Compile/test each stage, including transport and policy tests and real PostgreSQL integration tests. Document deployment, consent, recovery limitations, supported events and remaining operational steps.

## Scope boundaries

TRAP HOUSE does not claim feature parity with Reval. The follow-up expansion adds configurable milestone, pet, quest/skill and CA-tier probes, grouped achievements and clan diaries, event registration, member-profile browsing, race standings and optional public efficiency metrics; see [expanded feature configuration](clan-features.md). Exhaustive historical CA/collection scans, PBs, raid party/death tracking, bingo, chat command links, screenshots, monthly maintenance and automatic roster import remain outside scope. Live observations and current item ownership do not prove a complete historical record. The server roster must be maintained by clan staff; joining a chat channel is not sufficient authorization.

## Plugin Hub considerations

Reviewed the official [Plugin Hub README](https://github.com/runelite/plugin-hub/blob/master/README.md), including standard builds, Java compatibility, resource loading, licensing, third-party dependencies and review. The plugin remains Java 11-compatible, uses RuneLite's Gson/OkHttp dependencies, bundled resources and supported event APIs. `build=standard` intentionally excludes custom backend source sets and JDBC dependencies from Hub packaging. The separate service is not run inside RuneLite.

Tracking is disabled by default and clearly describes data sent. Credentials are member-specific, not a shared embedded secret. Discord credentials remain on the server. No dynamic code downloads, game-input automation, automatic promotions or external image loading are added. See the privacy section and `server/README.md` for the trust boundary: HTTPS and token binding do not attest an unmodified RuneLite client. Plugin Hub/Jagex review and acceptance are external steps; this review does not certify acceptance.

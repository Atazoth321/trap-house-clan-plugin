# TRAP HOUSE clan features

The clan screens use the existing TRAP HOUSE logo, red gradient header, rounded cards, gold text and dark background. They are original implementations based on the requested functionality, not copies of Reval code or assets.

| Screen | Working behavior | Source of truth |
| --- | --- | --- |
| Profile | Points, eligible rank, next-rank progress, category breakdown, EHP/EHB, milestone/combat checklists, award history, manual requests | Server award ledger, configured goals, optional public WOM metrics |
| Ranks | Thresholds and enabled point sources | PostgreSQL rank/rule configuration |
| Leaderboard | Up to 1,000 active members, original standing retained during search, clickable profiles, sortable/searchable full window | Server totals; browsing requires a valid member credential |
| Achievements | Grouped milestones, pets, combat tiers, collection goals and challenges with observed progress/completion | Server-defined probes and server-computed event totals |
| Events | Existing Boss of the Week and Skill of the Month panels, plus scheduled clan events with register/cancel, capacity limits and closed-registration handling | Existing WOM competition API plus PostgreSQL events/registrations |
| Diary | Configurable sections and tasks; automatic event/progress criteria or independent staff approval | Server definitions, progress, approved manual rewards |
| Competitions | Race firsts, standings and projected placement prizes | Accepted server observations |

## Progress and rewards

On the game thread, every 50 game ticks, the opt-in tracker reads only enabled definitions returned by the clan server. It never opens interfaces or executes scripts. Each definition supports:

| Source | `source_key` | Observed value |
| --- | --- | --- |
| `TOTAL_LEVEL` | `total` | Current total level |
| `SKILL` | RuneLite skill enum, e.g. `ATTACK` | Unboosted level |
| `QUEST` | RuneLite quest enum | 1 if finished, otherwise 0 |
| `ITEM` | Numeric item ID | Quantity in inventory/equipment/currently available bank container |
| `VARBIT` / `VARP` | Numeric game ID | Current integer value |
| `CA_TIER` | Combat threshold varbit ID | 1 when current game CA points reach its live threshold; unknown if threshold is unavailable |
| `EVENT` | `TYPE:key` or `TYPE:*` | Server count of accepted observations; cannot be supplied by a player snapshot |
| `MANUAL` | Enabled manual rule key | Server count of approved requests; cannot be supplied by a player snapshot |

Progress is a **recorded high-water mark**, not proof of all historical account progress. Item ownership does not prove first acquisition or exclude trading. Bank contents are read only when available; no bank is opened automatically. Generic varbit/varp definitions must be reviewed for the current game revision before enabling rewards. Pet insurance flags are observations, not an exhaustive pet scan. Live CA/collection messages only contribute when accepted by an enabled scoring rule. Event goals count those accepted observations, not every game kill or loot quantity.

The service calculates rewards from configured thresholds/points and stores each completion atomically with at most one goal award and its Discord outbox. Repeated snapshots do not double-award. Manual criteria credit only their approved manual reward; they do not also receive a second goal payout. The initial goals have **zero reward points** and can track progress without inventing clan rewards. If staff later enable a positive reward on a completed tracking-only definition, it credits once on the next applicable synchronization. Already-paid goals never repay after a rule change. Use new goal IDs for distinct new challenges.

Run `runServer --args=init` on the existing service database to apply the additive schema changes and insert tracking-only starter definitions. It preserves member IDs, awards and administrator configuration. Future incompatible migrations still need separately reviewed versioned scripts. Deploy the updated service before enabling the expanded plugin features.

## Configure content

Clan administrators use restricted database tooling. Example original clan diary task (replace UUIDs and policy values with your own):

```sql
INSERT INTO goals(id,name,description,category,section,source,source_key,target,points,enabled)
VALUES ('44444444-4444-4444-8444-444444444444',
        'Ten clan-approved collection unlocks',
        'Record ten accepted collection observations.',
        'Diaries','Raider diary','EVENT','COLLECTION:*',10,0,true);
```

Set `points` only after agreeing the clan reward policy. Valid categories are `Milestones`, `Combat`, `Pets`, `Collection`, `Diaries`, `Challenges`; those awards appear in their profile breakdown. Manual and loot rules also accept a `category` and `display_name`; use the profile category names (`Drops`, `Pets`, `Milestones`, `Combat`, `Collection`, `Diaries`, `Challenges`, `Events`, `Misc`). Award categories are snapshotted when credited, so subsequent changes do not reclassify history.

To add an event, insert a `clan_events` record with an ID/title, UTC start/end/registration-close times, capacity and `published=true`. Registration is membership-bound and idempotent, and capacity changes serialize on the event row. Both joining and cancellation close at `registration_closes`; staff can manage exceptional cancellations in restricted tooling. An event registration is not evidence of attendance and awards no points.

To add a race, insert a `races` record with start/end times, `score_type` (`LOOT`, `COMBAT`, `COLLECTION`) and an exact normalized `score_key`, then define `race_prizes` by place and optional `race_firsts` by matching event type/key. At least one enabled scoring rule must accept the underlying observations. Scores count matching accepted observations in the server receipt window, not client-provided timestamps. Firsts use first server receipt; ties in standings use normalized names. Loot observations are not universal boss kill counts. Placement/first rewards are **projected only**: staff verify the outcome and approve configured manual rewards. No race payout or promotion is automated.

## Optional EHP/EHB

Set `TRAP_WOM_STATS_ENABLED=true` in the service environment to import public EHP/EHB from `api.wiseoldman.net`. This shares the verified roster's character names with WOM. The worker reads one eligible profile every 30 seconds and refreshes each no more than hourly. It does not create/update WOM profiles or award points. Missing/failed profiles remain ‚ÄúNot synced‚Äù and do not block other members. Imported values are saved with a fetch timestamp; they may be stale if upstream is unavailable. Keep this setting unset to avoid the extra external service.

## Added API routes

Existing HTTPS, bearer credential, request-size and rate-limit requirements apply.

| Route | Contract |
| --- | --- |
| `GET /v1/members?player=Name` | Read another active member's profile; excludes private reward requests and registration state |
| `POST /v1/progress` | `{"player":"Name","values":{"<goal UUID>":123}}`; at most 200 nonnegative integer observations; server allows only configured read-only probes |
| `POST /v1/clan-events/{UUID}/register` | Body `{}`; registration for credential owner |
| `POST /v1/clan-events/{UUID}/cancel` | Body `{}`; cancellation for credential owner |

Progress snapshots share the original trust boundary: they can be fabricated by a modified client. Tokens, server thresholds, deduplication and a member roster do not attest game state. Require independent staff evidence for valuable or disputed rewards. Per-member progress rows and read-only leaderboard browsing never authorize in-game actions.

## Development preview

```powershell
.\gradlew.bat previewClan
.\gradlew.bat previewClan --args=--window
```

This renders the **actual Swing components** using an explicitly labelled fixture in test resources. Output: `build/trap-house-clan-preview.png`. The optional window lets you browse the layout; its sample buttons do not submit anything. Preview code, sample data and test dependencies are excluded from the plugin JAR. The production plugin never displays fixture points or pretend live achievements. Connect it to the deployed clan service to view real data.

Member profile layout and navigation:

Profiles use a compact brown/gold layout with a name/points header, thin next-rank progress bar, three-column point-source tiles, separate EHP/EHB tiles, and single-line milestone/combat checklists. Full goal descriptions and exact source totals are available as tooltips. Automatic/assigned rank, locks, promotion eligibility, lifetime earnings and immutable rank history are available under ìRank details & historyî. Own manual reward requests remain available below the public profile sections.

A single click on a member in either leaderboard opens their public profile. The full leaderboard window closes after selecting a member; Back returns to the sidebar leaderboard, preserving the viewer's own profile and search. Public member profiles hide the viewer's private reward controls. Production uses the authenticated /v1/members endpoint and rejects responses for a different character or an outdated member selection. The standalone sample preview also supports these clicks with explicitly labelled fixture data. Generate own/member screenshots with `gradlew previewClan --args=--all`; use `--args=--window` for the interactive sample.

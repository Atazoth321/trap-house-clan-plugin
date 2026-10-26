# TRAP HOUSE ranking service

Original Java 11 REST service, deployed separately from RuneLite. PostgreSQL 16+ stores verified membership, credentials, rules, ranks, events, an award ledger, manual approvals and a Discord outbox. The plugin JAR contains no server classes or JDBC driver. No endpoint performs game actions or changes an in-game clan rank.

## Deployment

The backend now imports public WOM EHP/EHB by default and can award capped points for new gains after a fresh baseline. See [efficiency rewards](../docs/efficiency-rewards.md) for rates, limits, and configuration; older descriptions of this importer as display-only are superseded.

Deployment `init` also seeds [automatic drops and pets](../docs/automatic-drops-and-pets.md): exact NPC/item and raid reward rules plus once-per-member named pet collection unlocks. Existing custom and disabled rules are preserved.

For Render deployment of this existing project, see [the Render checklist](../docs/render-deployment.md) and the root `render.yaml`. It creates a separate API and database, leaving your website in place. That initial Blueprint uses the database owner for runtime too; follow the checklist's database privilege limitation before treating it as a hardened production deployment.

Use JDK 17+ for Gradle. Create a PostgreSQL database and dedicated database user. The schema owner initializes tables; use a separate runtime user with SELECT/INSERT/UPDATE and sequence usage, without schema creation or table deletion privileges. Events, ledger and audit records are append-only in application code: restrict runtime UPDATE/DELETE on those tables too.

Configure the service process through environment variables and a deployment secret store:

```text
TRAP_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/traphouse
TRAP_DATABASE_USER=<database-user>
TRAP_DATABASE_PASSWORD=<database-password>
TRAP_PORT=8090
TRAP_DISCORD_WEBHOOK=<optional HTTPS discord.com webhook URL>
```

For a remote database require certificate-verified TLS (`?sslmode=verify-full` and the appropriate CA). Never commit secrets. Initialize once, configure the roster/rules, then start:

```powershell
.\gradlew.bat runServer --args=init
.\gradlew.bat runServer
```

The listener defaults to **127.0.0.1**. Set `TRAP_BIND_ADDRESS=0.0.0.0` behind Render's HTTPS proxy; `PORT` takes precedence over `TRAP_PORT`. Render-style `postgresql://user:password@host/database` connection strings are accepted in `TRAP_DATABASE_URL` as well as the JDBC configuration above. Deploy an HTTPS reverse proxy with a publicly trusted certificate, request/header timeouts, connection limits and IP rate limits. Do not expose the raw listener or trust forwarded player names as authentication. The service enforces a 16 KiB JSON body limit, a per-member limit of 120 requests/minute and database statement/lock timeouts. Maintain database backups and retention for audit/outbox data.

`/health` reports process availability only; `/ready` also checks database access and the members table. Monitor pending outbox records separately. Startup never initializes/drops tables; the Render Blueprint explicitly runs `init` as its pre-deploy command. `schema.sql` includes the original schema and additive v2 feature changes; rerun `init` to upgrade an existing service and insert tracking-only starter goals. It is not a general migration engine; future incompatible changes require versioned deployment migrations. See [expanded feature configuration](../docs/clan-features.md).

## Membership and credentials

For browser-based member sign-in without manually distributing tokens, see [Discord sign-in and one-time character linking](../docs/discord-sign-in.md). This optional flow verifies Discord identity/server membership against operator-maintained character links and preserves the existing member permissions.

New members may also use [Discord linking requests and Owner approval](../docs/discord-joining.md) after the one-time bot setup. The signed `/discord/interactions` endpoint supports the Link RuneLite button, `/link`, and Owner-only `/pending` reviews; `discord-setup` registers commands and posts the button once.

Staff must independently verify **current TRAP HOUSE membership and character ownership** before adding a roster entry or issuing a token. A public name, clan-chat presence or client assertion is not proof. The administrator-maintained roster is the server authority; update it when members leave or rename.

Example SQL using a generated UUID and lowercase normalized name:

```sql
INSERT INTO members(id,name,normalized_name)
VALUES ('11111111-1111-4111-8111-111111111111','Example','example');
```

Issue credentials for an already verified active member:

```powershell
.\gradlew.bat runServer --args='issue-token Example member'
```

The CLI generates a random 256-bit token, displays it once, stores only its SHA-256 digest and sets 90-day expiry. Deliver it privately. Players enter only their own member token in RuneLite's secret configuration field. Masking does not encrypt RuneLite's local configuration. Staff tokens use `admin` instead of `member` and belong in separate staff tooling, outside the plugin.

```sql
UPDATE credentials SET revoked=true WHERE member_id='11111111-1111-4111-8111-111111111111';
UPDATE members SET active=false WHERE id='11111111-1111-4111-8111-111111111111';
```

Revocation takes effect on subsequent requests. Keep the member UUID through verified renames, updating display and normalized names; never transfer ownership based only on a name. Totals and unlock deduplication belong to that UUID.

## Rank thresholds and scoring

Administrators configure `ranks` and `rules` using restricted database tooling. The `init` deployment now applies the Owner-selected intervals once: Recruit 0, Pawn 500, Corporal 1500, Novice 3000, Sergeant 6000, Colonel 10000, Brigadier 17500, Admiral 27500 and Marshal 45000. Thresholds are unique/nonnegative and refresh from the server. Previously earned ranks are retained; eligibility is advisory and staff perform in-game promotions manually. See [expanded milestones and intervals](../docs/milestones.md) for the 150-goal catalog and migration behavior.

The initial scoring-rule table is **empty**. Explicitly define the clan's policy before observations award points. Keys are lowercase, trimmed and single-spaced. These are examples, not agreed clan rewards:

```sql
INSERT INTO rules(type,event_key,points,daily_limit,enabled) VALUES
('COLLECTION','abyssal whip',10,1,true),
('COMBAT','perfect kill',20,1,true),
('MANUAL','clan event attendance',25,1,true);
-- LOOT keys are '<npc ID>:<item ID>'; use verified RuneLite IDs.
UPDATE ranks SET threshold=200 WHERE name='Corporal';
```

Amounts are fixed per accepted observation (1–100,000). Loot quantities, game CA point suffixes and client GP values never determine clan points. Each NPC loot callback submits at most one observation per distinct item ID. Rule daily caps (1–100) reset at midnight UTC, using server receipt/review time. Combat/collection unlocks also have a unique member/type/key index. Exact retry UUIDs return their original award. Historical points never change when rules change.

Disable a rule with `UPDATE rules SET enabled=false ...`. A manual request snapshots its point amount; approval requires an enabled rule with the same amount and the current daily cap. Reject/resubmit when amounts change.

## API

All `/v1/*` requests require `Authorization: Bearer <token>`. POSTs require JSON and are limited to 16 KiB. Responses are JSON with `Cache-Control: no-store`; cookie authentication and CORS are not enabled.

| Route | Behavior |
| --- | --- |
| `GET /health` | Unauthenticated process health |
| `GET /v1/dashboard?player=Example` | Bound profile, eligible rank/next threshold, top 50 active members, enabled sources, recent 20 awards/manual requests |
| `POST /v1/events` | Observe supported event; calculate points on server |
| `POST /v1/manual-rewards` | Request a configured reward for the credential owner |
| `GET /v1/admin/rewards` | Administrator-only oldest 100 pending requests |
| `POST /v1/admin/rewards/{UUID}/approve` | Different administrator approves; body `{}` |
| `POST /v1/admin/rewards/{UUID}/reject` | Different administrator rejects; body `{}` |

Observation body (use a current time and new UUID; preserve the entire body on retries):

```json
{"id":"22222222-2222-4222-8222-222222222222","player":"Example","type":"COLLECTION","key":"abyssal whip","observedAt":"2026-10-25T12:00:00Z"}
```

Types are `LOOT`, `COMBAT`, `COLLECTION`; accepted timestamps are within the past 24 hours or next five minutes. A `points` field is rejected. Unconfigured rules never award points.

Manual request body:

```json
{"id":"33333333-3333-4333-8333-333333333333","key":"clan event attendance","reason":"Attended Sunday event; organizer can verify attendance."}
```

At most 20 requests can be pending per member. The credential determines the recipient. Requesting awards nothing. Self-approval is prohibited even for administrators. Staff verify evidence before reviewing. Approval atomically records status, reviewer, audit, ledger and Discord outbox. Repeated approval cannot double-award.

Errors: 400 invalid input, 401 credential/membership rejection, 403 wrong character/non-admin/self-approval, 404 unknown route/reward, 409 ID/evidence conflict, 413 oversized body, 415 non-JSON, 422 unconfigured rule/cap/time window, 429 rate limit, 503 transient failure. Retry 503/network failures with the **same UUID**. Respect `Retry-After` on 429 in external tooling.

## Discord

Only the server receives `TRAP_DISCORD_WEBHOOK`. The worker allows HTTPS `discord.com/api/webhooks/...` URLs, disables redirects and mentions, and sends committed awards through a transactional outbox. Failed delivery backs off from 60 seconds to one hour. Workers coordinate with `FOR UPDATE SKIP LOCKED`.

Delivery is **at least once**: if Discord accepts a message and the delivery acknowledgement fails to commit, a duplicate message is possible. Points remain deduplicated. Monitor `discord_outbox.attempts/next_attempt` for broken webhooks. Leaving the variable unset sends nothing and retains the pending outbox.

## Trust boundary and synchronization

TLS, random expiring tokens, member binding, roster checks, prepared SQL, deduplication and caps protect the API. They do **not** prove an unmodified RuneLite client: a stolen token or modified client can fabricate observations for its bound member within enabled rules/caps. Use conservative rules and staff-verified manual rewards for valuable awards.

The plugin observes live NPC loot and English system messages for combat tasks/collection unlocks. Enable the relevant in-game notifications. Configured progress probes also read current milestone items, skill/quest/varbit values and CA tier thresholds without opening game interfaces. It does not reconstruct historical loot/unlock histories, automate interfaces, read player chat, capture screenshots, handle raid/event chests or import Reval data. See [probe semantics and limits](../docs/clan-features.md).

Sync runs every 30 seconds, in batches of up to 20 events, with 15-second HTTP timeouts and a 200-observation memory queue. The same UUID survives retries in the current session. Events while disabled, before observation, after queue overflow or after a client restart cannot be recovered. Queue/manual submissions clear on logout, account/config change or shutdown; already-in-flight submissions may commit. Server awards and the Discord outbox persist. Account/config generation guards discard stale UI responses, and stale refreshes retain data only for the current identity.

## Tests

```powershell
.\gradlew.bat build
$env:TRAP_TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/traphouse_test'
$env:TRAP_TEST_DATABASE_USER='<test-user>'
$env:TRAP_TEST_DATABASE_PASSWORD='<test-password>'
.\gradlew.bat serverIntegrationTest
```

`build` runs plugin/UI/transport tests and server policy/HTTP tests. Integration tests create/drop a random isolated schema per test in an explicitly selected **disposable** database. They cover the actual PostgreSQL schema, scoring, member/credential checks, approvals, rollback, HTTP authorization, concurrent retry deduplication and caps. The integration task fails if the test URL is absent. No test contacts Reval or sends a real Discord notification.

Rank protection and permission integration:

Run `init` again with the schema owner to apply the additive migration. It preserves existing members, points, events, and credentials. The runtime database role needs SELECT/INSERT on the new tables, UPDATE on member_rank_state/member_permissions, and sequence usage for audit. Do not grant runtime UPDATE/DELETE/TRUNCATE on audit, point_adjustments, events, or ledger, or permission to change schema/functions/triggers. Audit and adjustments also reject UPDATE/DELETE/TRUNCATE with database triggers. Keep permanent backups; a database superuser remains trusted.

Provision the verified clan owner locally, using their existing member UUID:

```powershell
.\gradlew.bat runServer --args='bootstrap-owner <member-uuid>'
```

Owner is an API permission independent of any displayed rank. Existing admin credentials retain Administrator permission until an explicit member_permissions entry overrides them. Explicit permissions apply to every credential for that member and are read from the database on every request. No API can create another Owner or remove an Owner permission; operator access is required. Giving someone a displayed leadership rank never gives them API permissions.

| Permission | Access |
| --- | --- |
| Owner | All operations; assign/remove protected ranks, lock/unlock ranks, set non-owner permissions |
| Administrator | Existing independent reward reviews, positive manual awards, ordinary unlocked promotions, audit, event publishing; never deductions or demotions |
| Event Manager | Member features and publish/unpublish existing clan events; no rank, point, reward-review, permission or audit authority |
| Member | Existing own observations, reward requests, profiles and event registrations |

`ranks` remains the configurable automatic progression ladder. `protected_ranks` is a separate operator-maintained list (Owner, Deputy Owner, Master, Major and Proselyte initially). Names in protected_ranks are excluded from automatic progression even if also present in ranks. Configure these tables through trusted operator tooling; never remove protection from an existing leadership rank as a routine threshold edit.

A manual assigned rank overrides the displayed rank without changing points. REMOVE clears that assignment and restores automatic progression. LOCK snapshots the current rank when no assignment exists and blocks administrator changes; only Owner may lock/unlock or change a protected assignment, including removing it or replacing it with an ordinary rank. UNLOCK keeps the assigned override; use REMOVE to resume automatic rank display. Automatic ranks retain the highest earned historical tier even after a deduction. Removing an override also requires Owner permission if it would lower the effective rank. No process changes assigned ranks automatically.

Staff API (Bearer token and JSON required):

| Route | JSON / result |
| --- | --- |
| POST /v1/admin/members/{uuid}/rank | action: ASSIGN / REMOVE / LOCK / UNLOCK, reason; ASSIGN also requires rank |
| POST /v1/admin/members/{uuid}/points | id: unique adjustment UUID, points: signed integer (-100000..100000, nonzero), reason |
| POST /v1/admin/members/{uuid}/permission | permission: ADMINISTRATOR / EVENT_MANAGER / MEMBER, reason; Owner only |
| POST /v1/admin/clan-events/{uuid}/publish | published: boolean |
| GET /v1/admin/audit?after=0 | Up to 200 permanent records in ascending ID order; paginate using the last ID |

Example rank assignment: `{"action":"ASSIGN","rank":"Owner","reason":"Verified clan owner"}`. These staff operations are exposed through the authenticated API; the RuneLite sidebar displays the results. Point adjustments are retry-safe with the same UUID and identical body, cannot make totals negative, and share totals/breakdowns/leaderboards with earned points. Manual rank changes record before/after state, actor, recipient, time and reason; point adjustments record delta, old/new totals, member and acting staff UUIDs/names, timestamp and reason in a database insertion trigger. Only the Owner may submit negative adjustments; positive administrator awards remain supported. Existing reward approvals record recipient and awarded points alongside the permanent reward record.

Dashboard profiles expose automaticRank, assignedRank (nullable), rankLocked, protectedRank, points, and promotionEligible separately. The legacy rank field is the effective displayed application rank. Eligibility means the automatic threshold exceeds the assigned ordinary rank (or no assignment exists); locked and protected assignments are never eligible. It is advisory and does not modify assignments or game ranks.

Set TRAP_DISCORD_WEBHOOK to a webhook in a leadership channel. Every 30 seconds the server reconciles retained earned ranks/thresholds and enqueues qualifying promotions transactionally, once per member and rank, including qualifications earned offline or after threshold edits. Locked/protected members are excluded. Delivery uses the existing retrying outbox with mentions disabled; delivery is at least once, so transport retries may duplicate a Discord message. Notification state survives service restarts. No Discord network calls happen in a points transaction. Without a configured webhook, notices remain queued.

Verification: `gradlew test serverTest` includes permission/eligibility and route enforcement tests. `gradlew serverIntegrationTest` additionally exercises real PostgreSQL protection, adjustments, audit triggers, permission authentication and notification deduplication; configure the existing TRAP_TEST_DATABASE_* variables for a disposable database first.

Permanent lifetime rules:

Eligible activity observations continue earning server-calculated lifetime points using existing rules, deduplication and caps. There are no inactivity penalties, periodic deductions, or reset endpoints. Positive manual awards remain available to Owner and Administrator to preserve existing reward workflows. Negative adjustments require the explicit OWNER permission, regardless of legacy administrator credentials, displayed leadership rank, client fields, or UI access. Reasons are required for both awards and deductions. Only the server operator can bootstrap Owner permission; keep Owner tokens in staff tooling and use the existing HTTPS, expiry and revocation controls.

The negative adjustment check runs in the backend before target access and again in a PostgreSQL BEFORE INSERT trigger against the acting member's actual permission. Member row locks serialize deductions, idempotent retries do not double-charge, and both API and database reject balances below zero. Each accepted adjustment is audited atomically by a database trigger with member UUID/name, signed amount, required reason, actor UUID/name, timestamp, and old/new balances. Supplying actor or permission fields in JSON cannot change the authenticated identity. Runtime accounts must not own tables or have permission to disable triggers, change functions/schema, or truncate history.

The profile separates:

- points: current balance, including authorized adjustments.
- lifetimeEarnedPoints: cumulative positive earnings and awards; deductions never reduce it.
- balanceRank: the threshold rank calculated from the current balance.
- automaticRank: the highest retained earned tier, unaffected by deductions.
- earnedRankHistory: permanent rank/threshold snapshots, points at earning, and timestamps.
- assignedRank and rank: manual override and effective application rank.

The additive migration creates append-only earned_rank_history and guards the existing ledger against UPDATE/DELETE/TRUNCATE. Award, adjustment, membership and threshold triggers record earned qualifications without deleting old tiers. Existing members are backfilled from historical running-balance peaks using today's configured thresholds; backfill timestamps mark migration time because past threshold definitions were not stored. Existing point entries and assignments are preserved. Future deductions do not alter historical earnings or earned ranks. For an intentional demotion, the Owner must separately submit ASSIGN with the lower rank and a reason; deductions never imply demotion. Owner authorization also applies when removing a higher override would lower the effective rank. Protected leadership ranks remain exclusively under Owner control.

Upgrade with the schema owner using `gradlew runServer --args=init` before starting the updated service. Extend runtime grants with SELECT/INSERT on earned_rank_history and SELECT on automatic_rank_candidates/retained_automatic_ranks. Deny UPDATE/DELETE/TRUNCATE on earned_rank_history and ledger as well as the existing permanent audit/adjustment tables. Continue granting required INSERT/sequence access for audit triggers. No database changes are applied automatically at service startup.

`gradlew check` includes signed-adjustment validation, all-role deduction/demotion permission tests, direct API routing, and real HTTP requests with credential-only JDBC fixtures that prove Member, Event Manager and Administrator requests (including spoofed Owner fields) are rejected before target access. The PostgreSQL integration suite additionally exercises real credentials and revocation, Owner deductions, durable rank retention, explicit demotion, immutable history/audit, database bypass rejection, and negative balance prevention. Use a disposable PostgreSQL database with TRAP_TEST_DATABASE_* configured to run `gradlew serverIntegrationTest`.

Screenshot rank ladder and emblems:

The progression ladder now uses the owner's supplied order and extracted transparent pixel emblems:

| Rank | Default points |
| --- | ---: |
| Recruit | 0 |
| Pawn | 50 |
| Corporal | 100 |
| Novice | 250 |
| Sergeant | 500 |
| Colonel | 1000 |
| Brigadier | 1500 |
| Admiral | 3000 |
| Marshal | 5000 |

Owner, Deputy Owner, Master, Major, and Proselyte are protected management/staff ranks, displayed in a separate section with their supplied emblems. They have no point thresholds and require Owner assignment/removal. These displayed titles do not grant backend permission levels: Owner, Administrator, Event Manager and Member remain the independent authorization roles. Existing owner-only deductions, demotions and staff-rank controls remain enforced.

`init` renames the legacy defaults while preserving customized thresholds: Soldier -> Corporal, Enforcer -> Sergeant, Lieutenant -> Brigadier, TRAP Elite -> Marshal. It adds missing progression defaults without overwriting existing names/thresholds; an existing custom threshold collision takes precedence over a new default. Legacy Administrator/Event Manager display titles resolve to Deputy Owner/Major; backend permission values are unchanged. The rank_aliases table resolves existing assignments and immutable earned-rank records to the new display names, with originalRank retained in history responses. Permanent history and audits are never rewritten. Add SELECT on rank_aliases to runtime grants. Run the additive init migration before using the new server.

# Current reward balance

This is a beta balance policy, not a measured guarantee of progression speed. It preserves the Owner-selected rank thresholds and all historical points, deductions, awards, and earned ranks. The one-time `balanced-rewards-v1` deployment migration changes unchanged enabled default amounts only. Custom amounts and disabled rules remain under operator control. No positive or negative backpay is applied by this migration.

## Progression and one-time recognition

Recruit 0; Pawn 500; Corporal 1,500; Novice 3,000; Sergeant 6,000; Colonel 10,000; Brigadier 17,500; Admiral 27,500; Marshal 45,000.

The 143 milestone/quest/combat tier rewards remain **15,070 points combined**. That maximum includes very difficult achievements, not just maxed skills. All skill milestones total 5,520; all total-level milestones total 2,850. Existing completed supported milestones can qualify on sync. No blanket award is issued for maxed or high-ranked accounts.

Named automatic pet collection unlocks and the independently reviewed pet claim default now pay **500 points**. Automatic collection rules remain once per member per named pet, not once per day. Previously paid 750-point pets retain their full award. Historical/unsupported pet claims still need independent staff verification, including checking that the unlock was not already paid automatically.

## Drop rewards

Most boss drops retain their existing rarity-based rewards (roughly 100–1,000). Barrows pieces remain **50 points**. To reduce easy farming and phase-based cap bypasses:

- A supported automatic drop item shares its configured daily cap across all recorded LOOT sources/variants for that item ID. The default is two rewards per member per item per UTC day, rather than two per NPC phase. Existing custom numeric caps are retained.
- Barrows additionally has a **four-piece total daily limit** across all pieces, giving at most 200 points/day at default values. Rewards beyond a cap are not banked for another day. Existing same-day awards count toward the limits; none are removed if a limit has already been exceeded.
- Retries of an already-paid observation return the original award; they do not consume an additional cap slot. Supported sources, authenticated character binding, and server-selected amounts remain required. This does not create independent proof of an unmodified game client.
- Unlisted custom loot rules retain their per-source caps. Staff-reviewed claims use their own configured limits, not automatic loot item limits; reviewers must reject duplicate claims and avoid using claims to bypass limits.

Current raid rewards per personal unique:

| Raid | Item | Points |
| --- | --- | ---: |
| Chambers of Xeric | Arcane prayer scroll | 150 |
| Chambers of Xeric | Dexterous prayer scroll, Dinh's bulwark | 250 |
| Chambers of Xeric | Dragon hunter crossbow | 350 |
| Chambers of Xeric | Ancestral hat | 400 |
| Chambers of Xeric | Ancestral top/bottom, dragon claws | 500 |
| Chambers of Xeric | Kodai insignia | 600 |
| Chambers of Xeric | Elder maul | 750 |
| Chambers of Xeric | Twisted bow | 1,000 |
| Theatre of Blood | Justiciar faceguard / legguards / chestguard | 300 / 350 / 400 |
| Theatre of Blood | Avernic defender hilt | 400 |
| Theatre of Blood | Ghrazi rapier | 500 |
| Theatre of Blood | Uncharged sanguinesti staff | 600 |
| Theatre of Blood | Uncharged scythe | 1,000 |
| Tombs of Amascut | Lightbearer | 200 |
| Tombs of Amascut | Fang, ward | 250 |
| Tombs of Amascut | Masori mask / chaps / body | 400 / 450 / 500 |
| Tombs of Amascut | Uncharged shadow | 1,000 |

The Drops page reads actual deployed rule prices and shows whether the cap is per item or source, plus the Barrows family limit. Owner custom values can differ from the defaults in this document.

## Ongoing skilling, PvM, and clan participation

WOM efficiency gains now pay **10 points/EHP and 10 points/EHB**, with a **250-point combined weekly cap**. That avoids paying double for EHB even though PvM also receives drop rewards. The first fresh import still establishes a baseline without paying lifetime efficiency totals. Existing highs, snapshots, baseline, fractional carry, and current-week usage are preserved on deployment. Only new processed gains receive the new rate; past awards are not recalculated. The weekly allowance resets Monday 00:00 UTC, not the lifetime points balance.

Default staff-reviewed claims:

| Claim | Points |
| --- | ---: |
| Clan event attendance | 150 |
| Verified unsupported rare drop | 150 |
| Verified new pet unlock | 500 |
| Easy diary tier | 75 |
| Medium diary tier | 150 |
| Hard diary tier | 250 |
| Elite diary tier | 400 |

These require independent Administrator/Owner approval, a reason/evidence, and keep a default cap of one per claim type per member per UTC day. The legacy unspecified diary claim remains available at 75 points; prefer the explicit tiers and never approve two claims for the same region/tier. Manual requests with an outdated amount must be rejected and resubmitted, not approved at an unexpected price.

The starter clan diary bonuses become 50 for the first accepted combat task, 50 for the first accepted collection unlock, and 100 for five configured drops. They stack with source rewards and pay once. Already-paid bonuses stay unchanged. These are clan diary goals, not automatic coverage of every OSRS achievement diary.

## Expected pace and verification

For an illustration, 30,000 additional points takes 60 weeks at an average 500/week, or 30 weeks at 1,000/week. Those are arithmetic examples, not estimates of how many hours members will need. Rank history and Owner deductions remain separate from those calculations. The target is meaningful early ranks and sustained participation for the highest ranks; verify that against actual clan activity before public rollout.

Deploy the latest backend commit and let `init` finish, then restart the updated plugin. Confirm the live Drops page and efficiency tooltip show the new values. Observe 3–5 members with different play styles for two weeks: compare new one-time points with repeatable points, awards per activity, shared caps, and time between ranks. Adjust future defaults from that evidence rather than repeatedly increasing all sources.

Gradle build/serverDist, available tests, and IDE compilation passed. New unit/UI tests cover reward ordering, allowlisted shared cap identities, efficiency math, and visible limits. All 52 PostgreSQL integration tests passed against a fresh disposable local PostgreSQL 16.4 instance. They cover shared source caps, the four-piece Barrows limit, retained historical awards, custom/disabled rules, migration idempotence, efficiency state preservation, owner-only deductions (including direct HTTP requests), and protected ranks. A production initialization test runs init twice against the previous release's migration state and verifies retained historical pet awards and updated default prices. The fixture now supplies the migration registry created by production's rank-threshold migration. No Render database was used or changed. Live migration, awards, backup/restore, and measured clan progression remain unverified.

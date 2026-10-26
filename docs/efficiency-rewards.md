# Wise Old Man EHP/EHB and ranking points

The backend reads each active roster member's public WOM profile from `https://api.wiseoldman.net/v2/players/{name}`. It imports the top-level numeric EHP/EHB and WOM's `updatedAt` snapshot timestamp. No player-submitted efficiency values are accepted. No API key is required for these public reads. The importer is now enabled by default; explicitly set `TRAP_WOM_STATS_ENABLED=false` to stop it. The Render Blueprint explicitly enables it too.

## Rewards

- Each EHP gained earns **5 points**.
- Each EHB gained earns **10 points**.
- The two sources share a **100-point weekly cap per member**.
- Weeks start Monday at 00:00 UTC. Awards count in the week the server imports them, rather than reconstructing exact game activity times.
- The first fresh import establishes a baseline and grants **zero points for lifetime totals**. Baselines start when this feature first receives fresh data for that member, not retroactively on the member's historical joining date.
- Later strictly newer WOM snapshots earn positive differences above previously observed highs. Corrections/decreases never deduct points. Returning to an old high does not earn the same points again.
- Fractional points carry between imports within the same week. Fractional carry expires at the week boundary. Gains above the cap are consumed without banking them for future weeks. No lifetime points are reset.
- No points or baseline are created from a snapshot older than 24 hours, missing its timestamp, or more than five minutes in the future. Such a profile can still display EHP/EHB without generating rewards.

For example, after a baseline of 800 EHP and 100 EHB, a fresh snapshot of 801 EHP and 101 EHB earns 15 points. Re-importing the same snapshot earns nothing.

## Display and updating

EHP/EHB cards display the public WOM totals. Hover their container for the last import time, baseline status, and weekly reward usage. Awarded points appear under **Efficiency** in the profile breakdown and its clickable points log, and contribute to lifetime ranks like other positive ledger awards.

The worker processes at most one member every 30 seconds and normally refreshes each member hourly. Failed imports and members still waiting for a fresh baseline retry after five minutes. Display changes also wait for the plugin's next ranking sync, normally 30 seconds. This importer does not request WOM player updates: players should update their WOM profile through WOM before expecting new EHP/EHB to appear.

## Deployment

1. Deploy the commit on Render. The pre-deploy `init` adds `efficiency_reward_state` without replacing existing metrics, points, or ranks.
2. Check that the service environment does not explicitly set `TRAP_WOM_STATS_ENABLED=false`; use `true` to enable it.
3. Confirm each active roster character has a public WOM profile. For Atazoth, use `https://wiseoldman.net/players/Atazoth` and update that profile when needed.
4. Restart the development plugin to see the new Efficiency filter and tooltip. Wait for the backend import and plugin sync. No new Discord login is required if the stored credential remains valid.
5. Verify the first import shows totals without crediting lifetime history. Then verify a later fresh WOM gain, its positive ledger award, and weekly cap usage before clan rollout.

The runtime database role needs SELECT/INSERT/UPDATE on `efficiency_reward_state`, existing metric tables, and the existing event/ledger/outbox award privileges. Ordinary members, event managers, and administrators cannot submit efficiency events or deduct points. Historical ledger entries remain immutable; state and ledger changes are committed together under a member row lock. Duplicate snapshots and concurrent imports cannot pay the same increase twice. No automatic OSRS clan rank changes occur.

Unit tests verify rate math, combined caps, positive-only gains, UTC weeks, parser timestamps, and client observation rejection. PostgreSQL tests cover baseline, stale data, replay, downward corrections, weekly rollover, and fractional carry. Live WOM totals were read successfully during development; complete live point awards require deployment and later WOM gains, and database integration tests require the documented disposable database environment.

# PvP Arena achievement reports

PvP Arena uses a temporary character profile. The plugin now pauses progress probes,
browser snapshots, pet scans, and loot/chat achievement observations on worlds marked
`PVP_ARENA`. Quest speedrunning, beta, no-save and tournament worlds are also excluded.
Ordinary PvP worlds continue to work. Restart RuneLite with the updated plugin before
returning to the arena. After hopping back to a normal world, periodic observations
resume after 50 game ticks.

This prevents new observations; it does not reverse previously recorded progress.
The server retains the greatest observed goal value and records completed goals
and awarded points separately. Logging into a normal world cannot undo these records.

## Inspect MutantZK's recorded achievements

Run this read-only query in the ranking database and compare completion and award
times with the reported arena match. It includes goals that completed without points.
The result alone does not establish that an achievement was incorrectly awarded.

```sql
SELECT m.name, g.name AS achievement, g.source, p.value, g.target,
       p.completed_at, p.updated_at, p.award_event,
       e.received_at AS award_recorded_at, coalesce(l.points, 0) AS awarded_points
FROM members m
JOIN goal_progress p ON p.member_id = m.id
JOIN goals g ON g.id = p.goal_id
LEFT JOIN events e ON e.id = p.award_event
LEFT JOIN ledger l ON l.event_id = e.id
WHERE m.normalized_name = 'mutantzk'
ORDER BY p.completed_at DESC NULLS LAST, g.name;
```

## Reverse the incident's points and unlocks

Deploy this server update with the normal pre-deploy `init`. It adds the append-only
`reversed_goal_awards` table. Restricted runtime roles need SELECT on it. Repair commands
run only in the trusted operator shell, using the existing database Owner checks for
deductions. There is no public repair endpoint.

Keep the affected member signed out until their plugin includes the PvP Arena guard.
Otherwise an old plugin can submit the temporary profile again after repair.

In Render's API service Shell, preview the verified incident window. Supply UTC timestamps
with `Z`; the end is exclusive and the window must be no longer than 24 hours:

```sh
/app/bin/trap-house-server preview-arena-repair "MutantZK" START_UTC END_UTC
```

This prints every affected goal, its saved value, the original awards, the exact points
to reverse, and a `previewHash`. It selects client-observed goals first completed in that
window, including skill, quest, total-level, diary, item and milestone goals. Earlier
completions, other members, and event/manual rewards are excluded. Review the list: the
server cannot infer which observations came from the arena. Narrow the window if it
includes a legitimate completion. Goals without points are reset too.

After reviewing that exact preview, apply it with the active backend Owner's UUID and
the printed hash:

```sh
/app/bin/trap-house-server repair-arena-progress "MutantZK" START_UTC END_UTC OWNER_UUID PREVIEW_HASH "Reverse verified temporary-profile unlocks from the PvP Arena incident"
```

The command atomically deducts the actual awarded points (including goal top-ups), records
which awards were reversed, resets the selected goal values and completion flags, and
audits the complete before-state, reason and Owner reference. It rejects a changed preview,
a repeated application, a non-Owner, or a deduction that would make the balance negative.
Failure rolls back the entire repair. Do not separately deduct the same points manually.

The false unlocks disappear from the reward catalog and the achievement activity feed.
Original events, ledger entries, point history and audit records remain as evidence,
alongside the reversal adjustments. Retained earned ranks and lifetime positive-earnings
history keep their existing semantics; this command does not demote ranks.

On the next normal-world sync, legitimate current progress is restored automatically.
A repaired achievement can award points once when genuinely completed, because its
previous award was fully reversed. Achievements genuinely completed before the incident
window retain their existing progress and points.

The live database was not available from this workspace, so MutantZK's records and
points have not been inspected or changed.

# Weekly membership rewards

All clan members automatically earn 25 points per complete seven-day membership period, including members marked inactive. Logins and gameplay are not required.

Run the backend `init` command with the schema owner and restart the updated backend to enable the feature. Existing members start counting at the first successful migration; new members start when added to the server. No prior clan tenure earns points. Re-running initialization preserves the baseline and prior awards.

Changes to active status preserve the earning period. The worker checks for due members every second and recovers completed periods after service downtime. Each award is 25 points and uses the existing ledger, history, notifications and leaderboard totals. Member locks, transactional state updates and a unique weekly event index prevent duplicate awards across retries and concurrent workers. Recovery is limited to 52 weeks per member per transaction, with remaining weeks handled on subsequent passes.

The runtime database role needs SELECT/INSERT/UPDATE on membership_reward_state, plus its existing member-lock, event, ledger and notification permissions. The membership trigger also writes this state for new members.

## Verified historical dates (Owner only)

Open your own profile and use the **Verified clan membership** button below **Manual rewards** (Owner only). It opens a popup with a searchable member dropdown. Select any member, including your own account or an inactive member. Enter the verified start date, exclusive end date in YYYY-MM-DD format, and a verification note. The form previews 25 points per complete seven days; for example, January 1 to January 15 awards 50 points. Click **Award membership points** and wait for server confirmation.

Both Owner forms include **Search member names** above a dropdown. Type any part of a name to filter the roster, then select the recipient. No matches disables awarding. **Manual rewards** on the Owner's own profile accepts 1–100,000 points and a reason, then immediately applies the award without a staff approval request. The Owner can award their own account and inactive members. Other members keep the staff approval workflow. Direct awards use the existing audited, retry-safe `POST /v1/admin/members/{uuid}/points` endpoint. Restart the plugin after updating, and deploy the updated backend to allow Owner direct awards to inactive members.

Use **Manage manual awards** to open the direct award popup, just as **Manage player ranks** opens the rank editor. Each window retains its fields during dashboard refreshes and closes when you sign out or lose Owner permission. Member dropdowns use the existing active roster on older backends; the updated backend supplies all members and tracking dates. Verified dates remain disabled with an explicit update message if the backend does not supply tracking dates.

The end date must be on or before that member's automatic tracking date, shown in the form. Future dates and overlapping previously awarded ranges are rejected; adjacent ranges are allowed. Leftover days are discarded per submission. Historical awards are an explicit Owner exception to the automatic no-backpay rule. They do not change the automatic baseline. Server authorization permits self-awards by the Owner; administrators and ordinary members cannot use this endpoint.

Awards appear in normal point history and notifications. The recipient, verifying Owner, dates, note, weeks and points are recorded in the permanent audit. Identical retries use the same award ID and cannot award twice.

Deploy both plugin and backend updates and run backend `init` before using the form. Grant runtime SELECT/INSERT on verified_membership_awards; do not grant UPDATE/DELETE/TRUNCATE. API: `POST /v1/admin/members/{uuid}/membership` with `id` (UUID), `startDate`, `endDate`, and `reason`; points are calculated by the server.

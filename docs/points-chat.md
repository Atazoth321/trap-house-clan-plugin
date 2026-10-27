# Confirmed points in game chat

**Color in-game drop notices** also colors the game's existing valuable/untradeable drop notices gold, pet notices purple, and collection-log unlocks green. It is enabled by default and works independently of ranking sync and point messages. It shares the drop, pet and milestone color settings below. It recolors existing lines without adding duplicates; the game's loot notification settings still control which drop notices appear. Changes apply to new messages.

**Color point messages** is enabled by default. Drops use gold, pets use purple, milestones use green, and combat/diary/event/efficiency/other rewards use light blue. Each color can be customized in the plugin settings, or colors can be disabled to use ordinary game-chat text. **Points in chat** separately controls whether any point messages appear. These settings affect only the receiving player's private messages.

The notification feed supplies the category recorded on each award's ledger entry. Older clients ignore this additional field; a new client connected to an older backend uses the Other color when the field is absent. Deploy the backend and update the Plugin Hub client for category-specific colors. No new schema migration is needed. Changing message colors or visibility does not reset ranking sync or the notification cursor; connection/account changes still do. Reward names are sanitized before the client adds its own color tags.

With ranking sync enabled, **Points in chat** is enabled by default. After the server confirms a new award, the next successful ranking refresh queues a private game-chat message, for example:

```text
[Trap House] You earned 1 clan point for Cowhide (easy test drop).
[Trap House] You earned 150 clan points for First Fire cape.
```

Messages cover positive awards recorded by the server: supported drops, pet/collection rewards, milestones, combat/diary/event goals, efficiency gains and approved manual rewards. Pending requests, rejected/capped drops, duplicate submissions and point deductions do not produce earned-points messages. These messages are local game messages; they are not sent to public, clan or Discord chat.

The first successful connection establishes a silent baseline. Existing awards are not replayed on login, plugin restart or ranking-connection changes. New messages usually appear on the next ranking sync (approximately 30 seconds), and large batches drain at up to five messages per game tick. The feed returns up to 100 awards per page and continues from its cursor on later syncs. Network failures retain the cursor for retry; repeated pages cannot repeat messages. Logging out, changing accounts, or changing the ranking URL, credential or sync toggle clears queued messages and the session cursor.

The additive `point_notifications` table records each new ledger award's amount and source title in the same transaction. It does not backfill old ledger entries or change points, caps or rank rules. The authenticated `/v1/point-notifications` endpoint returns only the credential owner's awards; it accepts an optional `after` cursor, not a player-name parameter. It serializes reads with award writers using the member lock. Source labels are stripped of chat markup and control characters and length-limited before display.

Deploy the server first through the normal pre-deploy `init` command, which creates the table and index. Then release the updated client through the existing Plugin Hub listing. Older clients continue syncing against the updated server. New clients keep ranking sync working if notification support is unavailable on an older server. Live chat cannot be verified in normal RuneLite until that client release is distributed.

# Discord requests and Owner approval

Members no longer need an operator to run a Render command for each new character. After the one-time setup below:

1. A member clicks **Link RuneLite** in the clan Discord or runs `/link`.
2. They enter their OSRS character name. This creates a pending request, not a member credential or permission grant.
3. The configured review channel receives the request with **Approve after verification** and **Reject** buttons. The linked backend Owner can also run `/pending` to see the five oldest outstanding requests privately.
4. The Owner independently verifies character ownership and current TRAP HOUSE membership, clicks Approve, and enters a required verification reason. Rejections also require a reason.
5. The member uses **Sign in with Discord** in RuneLite. The existing sign-in flow supplies the member credential automatically. `/link` or the Link RuneLite button shows whether their request is pending or their account is linked.

Discord proves the Discord identity and guild context; it does not prove OSRS character ownership. Approval remains a human verification step. Discord roles do not grant Owner permissions, and no part of this flow changes in-game ranks.

## One-time setup for this deployment

Use the **same Discord application** already configured for RuneLite sign-in.

1. Deploy this commit to `trap-house-ranking-api` on Render. Its existing pre-deploy `init` command adds the join-request, interaction-response cache, and bot notification outbox tables. It preserves points, ranks, identity links, and permissions.
2. Open [Discord Developer Portal](https://discord.com/developers/applications). Select your application. From **General Information**, copy its **Public Key**. Under **Bot**, create a bot if needed and obtain/reset its bot token. Enter the token directly in Render; do not paste it into chat, Git, screenshots, or RuneLite.
3. In Render's API service **Environment**, add these four settings:

   | Variable | Value |
   | --- | --- |
   | `TRAP_DISCORD_PUBLIC_KEY` | The application's 64-character Public Key |
   | `TRAP_DISCORD_BOT_TOKEN` | The bot token from the application's Bot page |
   | `TRAP_DISCORD_LINK_CHANNEL_ID` | Channel ID where members should see the Link RuneLite button |
   | `TRAP_DISCORD_REVIEW_CHANNEL_ID` | A private review channel accessible to you and the bot |

   Keep the existing `TRAP_DISCORD_CLIENT_ID`, `TRAP_DISCORD_GUILD_ID`, `TRAP_DISCORD_CLIENT_SECRET`, and `TRAP_PUBLIC_URL`. Save and redeploy. This bot token is different from the existing OAuth client secret and optional promotion webhook.
4. In the Discord application **General Information → Interactions Endpoint URL**, enter:

   `https://trap-house-ranking-api.onrender.com/discord/interactions`

   Save after the new deployment is live and `TRAP_DISCORD_PUBLIC_KEY` is set. Discord verifies the signed ping. Keep the existing OAuth redirect URL ending in `/auth/discord/callback` unchanged.
5. Install the bot in your clan server using **OAuth2 → URL Generator**, selecting `bot` and `applications.commands` scopes. Grant **View Channels** and **Send Messages**, then authorize the generated URL. Ensure channel overrides allow the bot to view and send in both configured channels. Members need permission to use application commands in the relevant channel. Privileged gateway intents and a running gateway connection are not required.
6. Your Discord account must already link to the correct active member with backend **OWNER** permission. An Owner emblem, Discord role, or successful ordinary sign-in alone does not establish that permission. If not yet bootstrapped, the trusted Render operator can use the existing commands once:

   ```sh
   /app/bin/trap-house-server add-member "Atazoth"
   /app/bin/trap-house-server bootstrap-owner MEMBER_UUID_FROM_PREVIOUS_COMMAND
   /app/bin/trap-house-server link-discord "Atazoth" 384823194965639170
   ```

   These commands are for the independently verified clan Owner, not incoming members. Existing matching links are preserved; conflicting links fail instead of being replaced. Do not bootstrap other applicants as Owners.
7. Run this **once** in the Render API Shell:

   ```sh
   /app/bin/trap-house-server discord-setup
   ```

   It checks for a linked active Owner, upserts this application's `/link`, `/pending`, `/edit-link` and `/revoke-link` guild commands, and posts the public **Link RuneLite** button. Running it again posts another button message; do not add it to the automatic pre-deploy command. Use `discord-commands` to update commands without posting another message. Unrelated application commands are preserved.
8. Test with a separate ordinary clan member. Before approval, check that their request is pending and sign-in grants no access. A non-Owner clicking review buttons must receive a private rejection. Verify the character, approve it as Owner, then test their normal plugin sign-in and member points. The live application and database have not been tested from this workspace.

## Enforcement and records

### Correcting a name after approval

Owners can manage accepted links directly in the clan Discord. After deploying this update, run the following once in the Render API Shell to register the commands without posting another public linking message:

```sh
/app/bin/trap-house-server discord-commands
```

- `/edit-link member:@Person` opens a private form for the correct OSRS name and a verification reason. Verify the new name belongs to the same person. The member's Discord link, UUID, points, ranks and achievement history stay intact; their existing credentials and sign-in sessions are revoked, so they must sign in with Discord again.
- `/revoke-link member:@Person` opens a private confirmation form requiring a reason. Submitting removes the Discord link and revokes the member's existing credentials and sign-in sessions. It preserves the roster record, points and history. The person may submit a fresh `/link` request, which needs a new Owner approval. This revokes a link; it does not ban future requests.
- The original **Reject** button also opens the revoke form after approval, provided that approval still matches the current link. Pending requests retain their existing rejection flow. Original approval records remain unchanged as history; a separate audit entry records each correction or revocation.

All forms recheck the active backend Owner permission on submission. Stale forms fail if the name or link has changed. Names belonging to another member cannot be merged or overwritten. Owner links require operator management to avoid removing the Owner's own access. Discord roles do not bypass these checks. Replies are private and do not send mentions.

After independently verifying the correct character belongs to the same linked person, a trusted operator can run this in the Render API service Shell after deploying the updated server:

```sh
/app/bin/trap-house-server correct-member-name "BSB" "Oakie Pokie" "Correcting the mistaken name entered during Discord linking"
```

This transaction preserves the member UUID, Discord link, points, progress and history. It revokes existing member credentials, clears associated login sessions, and records the old/new names and reason in the operator audit. The member must sign in with Discord again in RuneLite. The original approval request remains as historical evidence. Missing/inactive/unlinked members and a corrected name already belonging to another record fail without merging or replacing identities. This command corrects a name for the same person; it does not transfer an account to a different person.

- The public interaction endpoint verifies Ed25519 signatures against the raw body and timestamp, rejects signatures older/newer than five minutes, checks application and guild IDs, and derives the Discord user from the signed guild member payload.
- `/pending`, approval buttons, and approval modal submissions all independently require a linked, active backend Owner. Administrator, Event Manager, Member, unlinked users, and forged role fields do not qualify. Discord success/error messages are private interaction replies.
- Requests are unique per pending Discord identity and pending normalized RSN. Exact duplicate submissions reuse the pending request; an applicant may submit at most five new requests per day. Requests remain pending until reviewed; they do not automatically grant access or expire into approval.
- Discord interaction IDs cache the committed response, so retries cannot duplicate a link/decision. Failed request mutations roll back. Approving a new character creates a normal member without a staff permission row. Approving an existing active unlinked character retains its UUID, points, and existing permissions. Inactive or conflicting identities require operator review; no existing link is silently replaced.
- Approval and rejection produce permanent audit entries with the authenticated Owner UUID, target/request identity, requested character, Discord ID, required reason, and timestamp. No points are awarded or deducted by linking. Owner-only deductions and protected rank restrictions remain unchanged.
- Requests and review records remain in PostgreSQL. Review notifications use a durable bot outbox with retries and disabled mentions. Notification failure does not approve a request, and `/pending` remains available even when channel delivery fails. At-least-once delivery can repeat a notification; stale buttons cannot review a request twice.
- Response caches older than a day are removed by the bot worker. Identity requests, audit records, points, and rank history are not deleted by that cleanup. The service uses Java 21 in its existing Docker deployment; Ed25519 requires a runtime with that algorithm available.
- Established links can be corrected or revoked by the linked backend Owner using the commands above, with credential revocation and audit. Transferring identities between existing records and changing Owner links remain operator tasks. Discord membership is rechecked by the existing OAuth flow at sign-in, not continuously.

The runtime database connection in the current project remains an owner connection. This change enforces authorization in the backend; it does not complete the separately documented least-privilege database hardening. When configuring a restricted runtime role, include access to the new request/cache/outbox tables and the existing member/link/audit tables required by approvals, with no ability to modify audit history.

## Validation

The server unit tests, serverDist packaging and all 54 PostgreSQL integration tests passed using a disposable local PostgreSQL 16.4 instance. Tests cover signed HTTP requests, forged roles and non-Owner management attempts, approval/rejection, name corrections, credential and session revocation, retained member identity/history, stale forms, conflicting names, protected Owner links and lost Owner permission. The integration fixture creates the migration registry that production init provides before reward migrations. Registering the commands and testing the live Discord forms still require the deployment steps above.

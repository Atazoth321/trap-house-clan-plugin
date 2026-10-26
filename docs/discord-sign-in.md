# Discord sign-in and verified character linking

For the easier member onboarding flow, configure [Discord link requests and Owner approval](discord-joining.md). Members can then use a Discord button or `/link`, and the Owner reviews in Discord. The operator linking commands below remain a supported fallback and bootstrap path.

Members click **Sign in with Discord** in the plugin's Profile tab. Their browser opens Discord's authorization page. After approval, the plugin receives its own credential automatically and enables ranking sync. No token needs to be sent through chat or pasted into settings. Credentials still expire after 90 days; sign in again to replace an expired credential. This release does not implement silent refresh or Discord-based permission assignment.

## One-time Owner setup

1. Deploy the branch containing this feature. The existing Render pre-deploy `init` command adds `discord_member_links` and `discord_login_sessions` without replacing points, permissions, ranks, or history.
2. In [Discord Developer Portal](https://discord.com/developers/applications), create or select the TRAP HOUSE application. Under **OAuth2 → Redirects**, add exactly:

   `https://trap-house-ranking-api.onrender.com/auth/discord/callback`

3. In the API service's Render **Environment**, configure:

   | Variable | Value |
   | --- | --- |
   | `TRAP_PUBLIC_URL` | `https://trap-house-ranking-api.onrender.com` |
   | `TRAP_DISCORD_CLIENT_ID` | Discord application's Application ID |
   | `TRAP_DISCORD_CLIENT_SECRET` | OAuth2 client secret, entered directly in Render |
   | `TRAP_DISCORD_GUILD_ID` | TRAP HOUSE Discord server ID |

   Enable Discord Developer Mode in **User Settings → Advanced**, then right-click your server to copy its ID. The application requests `identify` and `guilds.members.read`; a Discord bot token is not required. Keep the client secret out of Git, screenshots, plugin settings, and chat. This OAuth secret is separate from the optional promotion webhook.
4. Save and redeploy. Start the updated development client with `gradlew.bat run`. An already-running client must restart to show the new button. The Owner must still authenticate to OSRS through RuneLite's official Jagex auth panel separately.

## Link each member once

Discord proves ownership of a Discord account and membership in the configured Discord server. It cannot prove ownership of an OSRS character. Leadership must independently verify the character and current clan membership before linking. Discord usernames and nicknames are not identity proof.

1. Obtain the member's numeric Discord user ID by right-clicking their account with Discord Developer Mode enabled. A first-time login also displays the numeric ID on a linking-required page; ask leadership privately to verify it against the actual member.
2. In the Render API Shell, register an independently verified active member if needed:

   ```sh
   /app/bin/trap-house-server add-member "Atazoth"
   ```

3. Link the verified identities (replace the placeholder with the actual numeric user ID):

   ```sh
   /app/bin/trap-house-server link-discord "Atazoth" DISCORD_USER_ID
   ```

4. The member clicks **Sign in with Discord** again, authorizes in their browser, and returns to RuneLite. They then log into their linked clan character. A different logged-in character cannot submit points under that credential.

Repeat the one-time linking for other verified members. The API does not offer public self-claimed character linking. Linking requires trusted Render operator access. Both identities are unique; trying to replace a conflicting link fails. Existing staff and Owner permissions remain attached to the member UUID; Discord never grants Owner status or protected ranks.

## Revocation and account changes

For a departing or compromised member, set `members.active=false` and revoke their `credentials` using the existing operator procedures in `server/README.md`. Active roster membership is checked on every API request. Discord server membership is checked at sign-in, not continuously. Removing a Discord link alone does not invalidate previously issued credentials: revoke those too. Identity relinking requires operator review, an explicit audit entry, and revocation of old credentials and outstanding login sessions; this release deliberately does not expose an automated relinking UI.

## Security and verification

Login sessions expire in ten minutes. OAuth state, browser capabilities, and device secrets are random; only hashes are stored. Callback state is single-use and consumed before contacting Discord. Only the plugin's device secret can exchange the completed session for a credential, exactly once. Tokens are returned in a POST response, never in browser URLs. Login pages suppress referrers and caching. Client HTTP redirects are disabled and browser URLs must match the configured API origin. OAuth access tokens are held only during Discord verification, never stored. The existing RuneLite member-token setting stores the issued credential locally with masked display, not encryption.

Public login start requests have a global limit of 30 per minute and polling is limited per session. Deploy behind Render HTTPS; login sessions and identity links need database privileges when switching to a restricted runtime database role. Grant runtime SELECT on identity links (no INSERT/UPDATE/DELETE), SELECT/INSERT/UPDATE/DELETE on temporary sessions, and the existing credential/audit INSERT privileges. Trusted operators alone provision identity links.

Unit tests cover fail-closed configuration, malformed callbacks, secret validation, and origin restrictions. PostgreSQL integration tests additionally cover successful linking, OAuth replay, forged device secrets, single-use credential exchange, unlinked identities, and permission spoofing. Run them only with the documented disposable test database configuration. Live Discord OAuth requires the application settings above and must be tested before clan rollout.

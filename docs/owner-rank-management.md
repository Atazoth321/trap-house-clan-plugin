# Owner rank management

After deploying the backend and releasing the updated Plugin Hub client, sign in as the verified Owner (Atazoth) and open **Profile → Manage player ranks**. Choose a player, select a rank, enter a reason, and click **Save rank**. The form waits for server confirmation and then refreshes clan profiles and the leaderboard. Select **Automatic (points)** to remove a manual assignment and use the player's retained earned rank.

The rank dropdown includes Owner, Deputy Owner, Master, Major, Proselyte and the configured points ranks, with their emblems. The profile now explicitly shows its effective rank as well as its emblem. These are plugin display ranks; OSRS clan ranks must still be changed manually in the game. Assigning a management display rank does not grant backend administrator or Owner permissions.

The editor and its member IDs are included only in an authenticated Owner's dashboard. Public member profiles and non-Owner dashboards omit editor data. The backend checks the authenticated credential for every rank change and records the actor, player, previous/new state, and reason in the existing audit log. Account/settings changes close the form and discard unsent work. Failed or uncertain responses are shown without claiming the edit succeeded; refresh before retrying an uncertain request.

The additive `owner-display-rank-v1` initialization gives existing active backend Owners with no manually assigned rank a locked **Owner** display rank and emblem. It preserves points, earned ranks and existing explicit assignments, audits each assignment, and runs once. Future operator `bootstrap-owner` commands also assign the Owner display rank. This uses verified backend permissions, never the character name alone. Run the normal Render pre-deploy `/app/bin/trap-house-server init` to apply it.

Both the backend and client updates are required. Generate an offline preview with `gradlew.bat previewRankManagement`; it writes `build/owner-rank-management-preview.png` using illustrative data.

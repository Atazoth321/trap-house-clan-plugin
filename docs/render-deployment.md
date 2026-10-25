# Deploy the existing ranking API to Render

The root `render.yaml` adds a separate Docker API service and a dedicated PostgreSQL database. Your existing website stays in place. Both services use Oregon; change both regions before deployment if desired. The Blueprint selects paid Starter API and Basic database plans. Review Render's current price before creating services.

## Deploy

1. Commit and push the existing project, including `server/`, `gradle/`, `gradlew`, `build.gradle`, `settings.gradle`, `.dockerignore`, and `render.yaml`, to your GitHub repository. Review the commit for credentials first. The Docker build needs the repository root as its context.
2. In Render, select **New → Blueprint**, connect that repository, and select the branch containing these changes. If this repository already has a Blueprint, merge the service definitions into that Blueprint instead of replacing its existing website definitions.
3. Review the two new services and their paid plans, then deploy. Render supplies the private PostgreSQL connection string through an environment variable. Do not paste it into source code or chat. No separate database API key is needed.
4. The pre-deploy command runs the existing additive schema and starter-goal initialization. If it fails, inspect the deployment logs before retrying. Back up a populated database before schema changes. The API binds to Render's `PORT` on `0.0.0.0`.
5. Open `https://<your-api-service>.onrender.com/ready`. A successful response is `{"status":"ready"}`. `/health` checks only the running HTTP process; `/ready` also checks database access and the members table. Neither verifies all ranking features.
6. After independently verifying clan membership and character ownership, use the API service's Render Shell to run `/app/bin/trap-house-server add-member "Character"`. It prints the member UUID and preserves an existing active member's identity. It never reactivates an inactive member. Then run `/app/bin/trap-house-server bootstrap-owner <verified-member-uuid>` for the actual Owner. These are operator commands, unavailable through the public API. Restrict Render account access and enable MFA. Member creation is audited as a server operator action without an authenticated application actor.
7. Issue each verified member a separate credential using `/app/bin/trap-house-server issue-token "Character" member`. Tokens expire after 90 days. Deliver them privately. Owner permissions attach to the member identity, so an Owner's token grants Owner rights regardless of the token's `member` label. Never distribute the Owner's credential to members.

   Alternatively, configure [Discord sign-in](discord-sign-in.md). Members then obtain their credentials automatically through the plugin after leadership links each verified character once; manual token delivery is unnecessary.
8. Configure eligible activities and point values in the existing `rules` table using `server/README.md`. The initial rule set is empty, and starter goals award zero points. Hosting alone does not enable automatic awards.
9. Optional: create a Discord channel webhook and add it as the API's secret `TRAP_DISCORD_WEBHOOK` environment variable, then redeploy. Verify a real promotion notice. Treat the webhook as a password.
10. In the RuneLite plugin's ranking configuration, enter the API's HTTPS address and that member's credential. Verify a real eligible activity, its ledger entry, leaderboard update, and rank eligibility with a test member before clan rollout. Review `docs/readiness-audit.md` for activity coverage and trust limitations.
11. Configure database backups and retention in Render, and test recovery. Application audit tables are append-only through their existing triggers; database owners and Render operators remain trusted and can alter the database. This initial Blueprint uses the database owner connection for both migrations and runtime. It does not provide a separately restricted runtime database role or independently immutable external audit storage.

## Optional custom API domain

Render's generated HTTPS address is sufficient. To use `api.traphouseosrs.net`, add that custom domain to the **API service**, create the DNS record Render requests, and wait for HTTPS verification. Keep the website's existing DNS records. No website rebuild is required just to run this plugin API.

## Verification limits

Local Gradle tests cover configuration parsing, readiness failure, and the existing application tests. Running the Docker image, applying PostgreSQL migrations, database integration tests, and live Render/Discord checks require their respective environments. Do not treat a successful build or readiness response as proof those checks passed. PostgreSQL tests require a disposable database and `TRAP_TEST_DATABASE_URL`, `TRAP_TEST_DATABASE_USER`, and `TRAP_TEST_DATABASE_PASSWORD`; run `gradlew.bat serverIntegrationTest` only against that test database.

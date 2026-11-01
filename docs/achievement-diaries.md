# Automatic achievement diaries

All 48 achievement diary tiers (12 regions, Easy through Elite) use the game's diary completion varbits through the existing automatic progress sync. They appear under Diaries, grouped by region. Partial task completion does not complete a tier. Current completed tiers qualify, including tiers completed before joining the clan.

Each tier awards once: Easy 25, Medium 50, Hard 100, Elite 200 points. All four tiers total 375 per region, or 4,500 for all regions. Existing goal-progress and ledger handling prevents duplicate awards on repeated syncs. These observations have the same client-trust limitations as other progress goals.

Deploy the updated backend with the normal pre-deploy `init` command. The additive `achievement-diaries.sql` seed preserves existing rewards, disabled goals, and progress on subsequent deployments. The existing plugin's VARBIT tracking already supports these definitions; no new client reader is required. The default goal count rises from 150 to 198, within the current 200-definition sync limit; only two custom goal slots remain before pagination or a coordinated limit change is required.

The generic staff-reviewed diary reward remains available for existing workflows. It is separate from these once-only automatic tier rewards; staff should avoid approving the same achievement again as a manual reward.

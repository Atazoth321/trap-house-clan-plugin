package com.traphouse;

import net.runelite.api.clan.ClanSettings;
import net.runelite.client.util.Text;

final class ClanMembership {
    private ClanMembership() {}

    static String problem(ClanSettings settings, String player) {
        if (player == null) return "Log into your linked OSRS character to load your profile.";
        if (settings == null) return "RuneLite has not loaded the clan roster for " + player
            + ". Open the in-game Clan tab and check your membership.";
        String clan = settings.getName();
        if (clan == null || !"trap house".equals(normalize(clan)))
            return "Logged in as " + player + ". RuneLite reports clan: "
                + (clan == null ? "unknown" : Text.removeTags(clan)) + ". TRAP HOUSE membership is required.";
        if (settings.getMembers().stream().noneMatch(member -> normalize(player).equals(normalize(member.getName()))))
            return "RuneLite loaded TRAP HOUSE, but " + player + " is missing from its member roster. Check your linked character and clan membership.";
        return null;
    }

    private static String normalize(String name) {
        return RankingTracker.normalize(Text.removeTags(name)).replace('_', ' ');
    }
}

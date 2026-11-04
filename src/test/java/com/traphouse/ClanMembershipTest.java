package com.traphouse;

import java.lang.reflect.Proxy;
import java.util.Collections;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanMembershipTest {
    @Test public void acceptsFormattedClanAndEquivalentCharacterNames() {
        assertNull(ClanMembership.problem(settings("<col=ff0000>TRAP\u00a0HOUSE</col>", "Some_Player"), "some\u00a0player"));
    }

    @Test public void distinguishesMissingRosterWrongClanAndMissingMember() {
        assertTrue(ClanMembership.problem(null, "Raider").contains("not loaded"));
        assertTrue(ClanMembership.problem(settings("Other Clan", "Raider"), "Raider").contains("Other Clan"));
        assertTrue(ClanMembership.problem(settings("Trap House", "Other Player"), "Raider").contains("Raider is missing"));
        assertTrue(ClanMembership.problem(null, null).contains("Log into"));
    }

    private static ClanSettings settings(String name, String memberName) {
        ClanMember member = (ClanMember) Proxy.newProxyInstance(ClanMember.class.getClassLoader(),
            new Class<?>[]{ClanMember.class}, (proxy, method, args) -> {
                if (method.getName().equals("getName")) return memberName;
                throw new UnsupportedOperationException(method.getName());
            });
        return (ClanSettings) Proxy.newProxyInstance(ClanSettings.class.getClassLoader(),
            new Class<?>[]{ClanSettings.class}, (proxy, method, args) -> {
                if (method.getName().equals("getName")) return name;
                if (method.getName().equals("getMembers")) return Collections.singletonList(member);
                throw new UnsupportedOperationException(method.getName());
            });
    }
}

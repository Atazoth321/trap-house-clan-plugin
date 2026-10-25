package com.traphouse.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnboardingTest {
    @Test public void acceptsOwnerCharacterName() {
        assertEquals("Atazoth", RankingServer.verifiedName(" Atazoth "));
    }

    @Test public void rejectsInvalidNamesBeforeOpeningDatabase() throws Exception {
        for (String name : new String[]{"", "abcdefghijklmnop", "player';--", "name\nother"}) {
            try { RankingServer.command(new String[]{"add-member", name}); fail("Invalid name accepted"); }
            catch (IllegalArgumentException expected) { assertEquals("Invalid character name", expected.getMessage()); }
        }
    }
}

package com.traphouse.server;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanBrowserDataTest {
    private static JsonArray array(String value){return JsonParser.parseString(value).getAsJsonArray();}
    @Test public void unseenItemsStayUnknownAndHistoricalObservationsSurvivePartialSessions() {
        JsonArray initial=ClanBrowserData.mergeItems(array("[{id:1,quantity:3},{id:2}]"),new JsonArray());assertFalse(initial.get(1).getAsJsonObject().has("quantity"));assertFalse(ClanBrowserData.complete(initial));
        JsonArray merged=ClanBrowserData.mergeItems(array("[{id:1},{id:2,quantity:1}]"),initial);assertEquals(3,merged.get(0).getAsJsonObject().get("quantity").getAsInt());assertTrue(ClanBrowserData.complete(merged));
        assertEquals(3,ClanBrowserData.mergeItems(array("[{id:1,quantity:0},{id:2,quantity:1}]"),merged).get(0).getAsJsonObject().get("quantity").getAsInt());
    }
    @Test public void malformedCountsAndSpoofedRewardFieldsAreRejected() {
        for(String value:new String[]{"{points:999}","{accountType:'OWNER'}","{clog:-1}","{xp:1.5}","{bosses:{\"bad key\":1}}"}) {
            try{ClanBrowserData.stats(JsonParser.parseString(value).getAsJsonObject());fail(value);}catch(Policy.Rejected expected){assertEquals(400,expected.status);}
        }
        try{ClanBrowserData.mergeItems(array("[{id:1},{id:1}]"),new JsonArray());fail();}catch(Policy.Rejected expected){assertEquals(400,expected.status);}
    }
    @Test public void allIronVariantsHaveExplicitGroupsAndMissingTypesStayUnknown() {
        assertEquals("main",ClanBrowserData.group("NORMAL"));for(String type:new String[]{"IRONMAN","GROUP_IRONMAN","ULTIMATE_IRONMAN"})assertEquals("iron",ClanBrowserData.group(type));
        assertEquals("hardcore",ClanBrowserData.group("HARDCORE_IRONMAN"));assertEquals("hardcore",ClanBrowserData.group("HARDCORE_GROUP_IRONMAN"));assertEquals("unknown",ClanBrowserData.group(""));
    }
    @Test public void publicSelectionsRejectUnknownModesDuplicateKeysAndInvalidCategories() {
        assertEquals("monthly-clog",PublicClanBrowser.parameters("mode=leaderboard&metric=monthly-clog").get("metric"));
        for(String query:new String[]{"mode=leaderboard&mode=clog","metric=private","mode=clog","mode=luck","category=bad%27","player=abcdefghijklmnop","refresh=true","unknown=x"})try{PublicClanBrowser.parameters(query);fail(query);}catch(Policy.Rejected expected){assertEquals(400,expected.status);}
    }
}

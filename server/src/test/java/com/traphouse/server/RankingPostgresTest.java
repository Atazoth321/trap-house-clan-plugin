package com.traphouse.server;

import com.google.gson.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Uses a fresh isolated schema per test in an explicitly selected disposable PostgreSQL DB. */
public class RankingPostgresTest {
    @Test public void easyCowhideTestDropSeedsExistingInstallsAndEnforcesCapsWithoutDuplicateAwards()throws Exception {
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES ('automatic-rewards-v1'),('automatic-rewards-v2')");
        AutomaticRewards.seed(c);AutomaticRewards.seed(c);
        for(AutomaticRewards.Rule rule:AutomaticRewards.testDropRules())assertArrayEquals(new int[]{1,1},RankingStore.rule(c,"LOOT",rule.key));
        JsonObject first=event("LOOT","2790:1739");
        assertEquals(1,RankingStore.event(c,member,first).get("points").getAsInt());
        assertEquals("DUPLICATE",RankingStore.event(c,member,first).get("status").getAsString());
        rejected(422,()->RankingStore.event(c,member,event("LOOT","2790:1739")));
        rejected(422,()->RankingStore.event(c,member,event("LOOT","2792:1739")));
        rejected(422,()->RankingStore.event(c,member,event("LOOT","2790:526")));
        assertEquals(1,count("ledger"));
        RankingStore.execute(c,"UPDATE rules SET enabled=false,points=3 WHERE type='LOOT' AND event_key='2790:1739'");
        RankingStore.execute(c,"DELETE FROM rules WHERE type='LOOT' AND event_key='2791:1739'");
        AutomaticRewards.seed(c);
        rejected(422,()->RankingStore.rule(c,"LOOT","2790:1739"));
        rejected(422,()->RankingStore.rule(c,"LOOT","2791:1739"));
        assertEquals(1,count("ledger"));
    }
    private static final String LINK_OWNER="111111111111111111";
    private UUID approvedLink()throws Exception {
        joiningSchema();
        RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?)",LINK_OWNER,clanOwner.id);
        UUID request=DiscordJoining.request(c,DISCORD_ID,member.name);
        DiscordJoining.review(c,LINK_OWNER,request,true,"Verified character and membership");return request;
    }
    private JsonObject linkSubmission(JsonObject form,String name,String reason) {
        JsonObject data=form.getAsJsonObject("data").deepCopy();
        for(JsonElement row:data.getAsJsonArray("components"))for(JsonElement element:row.getAsJsonObject().getAsJsonArray("components")) {
            JsonObject input=element.getAsJsonObject();input.addProperty("value",input.get("custom_id").getAsString().equals("rsn")?name:reason);
        }
        return data;
    }
    @Test public void discordOwnerCanCorrectAcceptedLinkWithoutLosingHistory()throws Exception {
        UUID request=approvedLink();RankingStore.event(c,member,event("LOOT","1:2"));
        JsonObject form=DiscordLinkManagement.form(c,LINK_OWNER,DISCORD_ID,true);
        assertTrue(form.getAsJsonObject("data").get("custom_id").getAsString().length()<=100);
        assertEquals(2,form.getAsJsonObject("data").getAsJsonArray("components").size());
        JsonObject data=linkSubmission(form,"Oakie Pokie","Verified correct name for the same person");
        DiscordLinkManagement.submit(c,LINK_OWNER,data);
        try(PreparedStatement s=RankingStore.query(c,"SELECT m.name,l.member_id FROM members m JOIN discord_member_links l ON m.id=l.member_id WHERE l.discord_user_id=?",DISCORD_ID);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals("Oakie Pokie",r.getString(1));assertEquals(member.id,r.getObject(2,UUID.class));
        }
        assertEquals(1,count("ledger"));rejected(401,()->RankingStore.authenticate(c,"Bearer member-token"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT status,character_name FROM discord_join_requests WHERE id=?",request);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals("APPROVED",r.getString(1));assertEquals(member.name,r.getString(2));
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT actor,details->>'newName' FROM audit WHERE action='DISCORD_LINK_NAME_CORRECTED'");ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(clanOwner.id,r.getObject(1,UUID.class));assertEquals("Oakie Pokie",r.getString(2));
        }
        rejected(409,()->DiscordLinkManagement.submit(c,LINK_OWNER,data));
    }
    @Test public void rejectingAnApprovedLinkRevokesAccessButKeepsScoresAndAllowsNewRequest()throws Exception {
        UUID request=approvedLink();RankingStore.event(c,member,event("LOOT","1:2"));
        RankingStore.execute(c,"INSERT INTO discord_login_sessions(id,device_hash,browser_hash,member_id) VALUES (?,?,?,?)",UUID.randomUUID(),Policy.hash("device"),Policy.hash("browser"),member.id);
        JsonObject interaction=DiscordJoining.object("{\"type\":3,\"data\":{\"custom_id\":\"join:reject:"+request+"\"}}");
        JsonObject form=DiscordJoining.dispatch(c,interaction,LINK_OWNER);
        assertEquals(9,form.get("type").getAsInt());assertTrue(form.getAsJsonObject("data").get("title").getAsString().startsWith("Revoke"));
        DiscordLinkManagement.submit(c,LINK_OWNER,linkSubmission(form,null,"Approval was made for the wrong person"));
        assertEquals(1,count("discord_member_links"));assertEquals(0,count("discord_login_sessions"));assertEquals(1,count("ledger"));
        rejected(401,()->RankingStore.authenticate(c,"Bearer member-token"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT actor,target FROM audit WHERE action='DISCORD_LINK_REVOKED'");ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(clanOwner.id,r.getObject(1,UUID.class));assertEquals(member.id,r.getObject(2,UUID.class));
        }
        UUID next=DiscordJoining.request(c,DISCORD_ID,member.name);assertNotEquals(request,next);
        assertEquals(1,count("discord_member_links"));
        DiscordJoining.review(c,LINK_OWNER,next,true,"Verified fresh character ownership");
        assertEquals(2,count("discord_member_links"));assertEquals(1,count("ledger"));
    }
    @Test public void linkManagementRejectsConflictsShortReasonsChangedLinksAndLostOwnerPermission()throws Exception {
        UUID request=approvedLink();
        JsonObject edit=DiscordLinkManagement.form(c,LINK_OWNER,DISCORD_ID,true);
        rejected(409,()->DiscordLinkManagement.submit(c,LINK_OWNER,linkSubmission(edit,admin.name,"Verified correct character name")));
        rejected(400,()->DiscordLinkManagement.submit(c,LINK_OWNER,linkSubmission(edit,"Oakie Pokie","short")));
        JsonObject revoke=DiscordLinkManagement.form(c,LINK_OWNER,DISCORD_ID,false);
        RankingStore.execute(c,"UPDATE discord_member_links SET linked_at=linked_at+interval '1 second' WHERE discord_user_id=?",DISCORD_ID);
        rejected(409,()->DiscordLinkManagement.submit(c,LINK_OWNER,linkSubmission(revoke,null,"An outdated form must fail")));
        rejected(409,()->DiscordLinkManagement.rejectAccepted(c,LINK_OWNER,request));
        rejected(403,()->DiscordLinkManagement.form(c,LINK_OWNER,LINK_OWNER,false));
        JsonObject fresh=DiscordLinkManagement.form(c,LINK_OWNER,DISCORD_ID,false);
        RankingStore.execute(c,"UPDATE member_permissions SET permission='MEMBER' WHERE member_id=?",clanOwner.id);
        rejected(403,()->DiscordLinkManagement.submit(c,LINK_OWNER,linkSubmission(fresh,null,"Owner permission was removed")));
        assertEquals(2,count("discord_member_links"));
        assertNotNull(RankingStore.authenticate(c,"Bearer member-token"));
    }
    @Test public void operatorNameCorrectionPreservesHistoryAndRevokesOldAccess()throws Exception {
        RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?)",DISCORD_ID,member.id);
        RankingStore.event(c,member,event("LOOT","1:2"));
        RankingStore.execute(c,"INSERT INTO discord_login_sessions(id,device_hash,browser_hash,member_id) VALUES (?,?,?,?)",UUID.randomUUID(),Policy.hash("device"),Policy.hash("browser"),member.id);
        c.commit();long history=count("ledger");
        RankingServer.command(new String[]{"correct-member-name",member.name,"Oakie Pokie","Verified mistaken linking name"},this::connection);
        try(PreparedStatement s=RankingStore.query(c,"SELECT name,normalized_name FROM members WHERE id=?",member.id);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals("Oakie Pokie",r.getString(1));assertEquals("oakie pokie",r.getString(2));
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT member_id FROM discord_member_links WHERE discord_user_id=?",DISCORD_ID);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(member.id,r.getObject(1,UUID.class));
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT count(*) FROM credentials WHERE member_id=? AND NOT revoked",member.id);ResultSet r=s.executeQuery()) {
            r.next();assertEquals(0,r.getInt(1));
        }
        assertEquals(0,count("discord_login_sessions"));assertEquals(history,count("ledger"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT details->>'oldName',details->>'newName' FROM audit WHERE target=? AND action='MEMBER_NAME_CORRECTED'",member.id);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(member.name,r.getString(1));assertEquals("Oakie Pokie",r.getString(2));
        }
    }
    @Test public void operatorNameCorrectionRejectsExistingTargetWithoutChangingSource()throws Exception {
        RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?)",DISCORD_ID,member.id);c.commit();
        try {
            RankingServer.command(new String[]{"correct-member-name",member.name,admin.name,"Verified mistaken linking name"},this::connection);
            fail("Existing member must not be replaced");
        }catch(IllegalArgumentException expected) {assertTrue(expected.getMessage().contains("already has a member record"));}
        try(PreparedStatement s=RankingStore.query(c,"SELECT name FROM members WHERE id=?",member.id);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(member.name,r.getString(1));
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT count(*) FROM credentials WHERE member_id=? AND revoked",member.id);ResultSet r=s.executeQuery()) {
            r.next();assertEquals(0,r.getInt(1));
        }
    }
    @Test public void productionInitRunsTwiceWithoutChangingHistoricalPetAwards()throws Exception {
        // Match the previous production release: activity migration precedes catalog seeding.
        try(InputStream input=RankingServer.class.getResourceAsStream("/activity-rewards-v3.sql");Statement s=c.createStatement()) {
            s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        AutomaticRewards.seed(c);
        RankingStore.event(c,member,event("COLLECTION","pet kraken"));
        c.commit();
        RankingServer.command(new String[]{"init"},this::connection);
        RankingServer.command(new String[]{"init"},this::connection);
        assertEquals(750,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        assertArrayEquals(new int[]{500,1},RankingStore.rule(c,"COLLECTION","pet kraken"));
        assertArrayEquals(new int[]{50,2},RankingStore.rule(c,"LOOT","chest:barrows:4708"));
        assertEquals(1,count("ledger"));assertEquals(0,count("point_adjustments"));
    }
    @Test public void balancePolicyPreservesHistoryCustomRulesAndEfficiencyState()throws Exception {
        AutomaticRewards.seed(c);
        RankingStore.execute(c,"UPDATE rules SET points=333 WHERE type='LOOT' AND event_key='chest:chambers of xeric:21034'");
        RankingStore.execute(c,"UPDATE rules SET enabled=false WHERE type='LOOT' AND event_key='chest:tombs of amascut:25975'");
        RankingStore.event(c,member,event("COLLECTION","pet kraken"));
        Instant now=Instant.now();EfficiencyRewards.observe(c,member,java.math.BigDecimal.TEN,java.math.BigDecimal.ONE,now,now);
        BalancedRewards.apply(c);
        assertArrayEquals(new int[]{75,1},RankingStore.rule(c,"MANUAL","verified easy diary tier"));
        assertArrayEquals(new int[]{400,1},RankingStore.rule(c,"MANUAL","verified elite diary tier"));
        assertArrayEquals(new int[]{150,2},RankingStore.rule(c,"LOOT","chest:chambers of xeric:21079"));
        assertArrayEquals(new int[]{333,2},RankingStore.rule(c,"LOOT","chest:chambers of xeric:21034"));
        rejected(422,()->RankingStore.rule(c,"LOOT","chest:tombs of amascut:25975"));
        assertEquals(750,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        assertArrayEquals(new int[]{500,1},RankingStore.rule(c,"COLLECTION","pet kraken"));
        assertEquals(1,count("efficiency_reward_state"));assertEquals(0,count("point_adjustments"));
        RankingStore.execute(c,"UPDATE rules SET points=500 WHERE event_key='chest:chambers of xeric:21079'");
        BalancedRewards.apply(c);assertArrayEquals(new int[]{500,2},RankingStore.rule(c,"LOOT","chest:chambers of xeric:21079"));
        assertEquals(1,count("ledger"));
    }
    @Test public void variantItemCapsAndBarrowsFamilyCapCannotBeBypassed()throws Exception {
        AutomaticRewards.seed(c);BalancedRewards.apply(c);
        RankingStore.event(c,member,event("LOOT","2042:12922"));RankingStore.event(c,member,event("LOOT","2043:12922"));
        rejected(422,()->RankingStore.event(c,member,event("LOOT","2044:12922")));
        JsonObject first=event("LOOT","chest:barrows:4708");RankingStore.event(c,member,first);RankingStore.event(c,member,first);
        for(int item:new int[]{4710,4712,4714})RankingStore.event(c,member,event("LOOT","chest:barrows:"+item));
        rejected(422,()->RankingStore.event(c,member,event("LOOT","chest:barrows:4716")));
        assertEquals(6,count("ledger"));assertEquals(700,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
    }
    @Test public void lowerBarrowsPolicyChangesFutureAwardsWithoutDeductions()throws Exception {
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES ('automatic-rewards-v1'),('automatic-rewards-v2')");
        RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category) VALUES ('LOOT','chest:barrows:4708',150,2,true,'Drops'),('LOOT','chest:barrows:4710',25,1,true,'Drops'),('LOOT','chest:barrows:4712',150,1,false,'Drops')");
        JsonObject first=event("LOOT","chest:barrows:4708");RankingStore.event(c,member,first);
        AutomaticRewards.seed(c);
        assertArrayEquals(new int[]{50,2},RankingStore.rule(c,"LOOT","chest:barrows:4708"));
        assertArrayEquals(new int[]{25,1},RankingStore.rule(c,"LOOT","chest:barrows:4710"));
        rejected(422,()->RankingStore.rule(c,"LOOT","chest:barrows:4712"));
        assertEquals(150,RankingStore.event(c,member,first).get("points").getAsInt());
        assertEquals(50,RankingStore.event(c,member,event("LOOT","chest:barrows:4708")).get("points").getAsInt());
        assertEquals(200,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        assertEquals(0,count("point_adjustments"));assertEquals(2,count("ledger"));
        RankingStore.execute(c,"UPDATE rules SET points=150 WHERE event_key='chest:barrows:4708'");
        AutomaticRewards.seed(c);assertArrayEquals(new int[]{150,2},RankingStore.rule(c,"LOOT","chest:barrows:4708"));
    }
    private void joiningSchema()throws Exception {
        try(InputStream input=RankingServer.class.getResourceAsStream("/discord-joining.sql");Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
    }
    @Test public void discordJoiningNeedsOwnerApprovalAndDoesNotGrantPermissions()throws Exception {
        joiningSchema();String ownerDiscord="111111111111111111",applicant="222222222222222222",staff="333333333333333333";
        RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?),(?,?)",ownerDiscord,clanOwner.id,staff,admin.id);
        UUID request=DiscordJoining.request(c,applicant,"NewJoiner");assertEquals(request,DiscordJoining.request(c,applicant,"newjoiner"));
        assertEquals(3,count("members"));assertEquals(2,count("discord_member_links"));assertEquals(1,count("discord_join_outbox"));
        for(String role:new String[]{"ADMINISTRATOR","EVENT_MANAGER","MEMBER"}) {
            RankingStore.execute(c,"INSERT INTO member_permissions(member_id,permission) VALUES (?,?) ON CONFLICT(member_id) DO UPDATE SET permission=excluded.permission",admin.id,role);
            rejected(403,()->DiscordJoining.review(c,staff,request,true,"Verified in-game ownership"));
        }
        assertEquals(3,count("members"));assertEquals(0,count("audit"));
        rejected(400,()->DiscordJoining.review(c,ownerDiscord,request,true,"short"));
        DiscordJoining.review(c,ownerDiscord,request,true,"Verified in-game character ownership and current clan membership");
        assertEquals(4,count("members"));assertEquals(3,count("discord_member_links"));assertEquals(1,count("audit"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT m.id,p.permission FROM discord_member_links l JOIN members m ON m.id=l.member_id LEFT JOIN member_permissions p ON p.member_id=m.id WHERE l.discord_user_id=?",applicant);ResultSet r=s.executeQuery()){assertTrue(r.next());assertNull(r.getString(2));}
        rejected(409,()->DiscordJoining.review(c,ownerDiscord,request,true,"Repeated verified approval"));
        rejected(409,()->DiscordJoining.request(c,applicant,"AnotherName"));
        assertEquals(1,count("credentials")); // Only the preexisting fixture credential exists.
        assertEquals(0,count("ledger"));assertEquals(0,count("point_adjustments"));
    }
    @Test public void discordJoiningPreservesExistingPointsAndCannotReplaceLinks()throws Exception {
        joiningSchema();String ownerDiscord="111111111111111111",applicant="222222222222222222";
        RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?)",ownerDiscord,clanOwner.id);
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        UUID request=DiscordJoining.request(c,applicant,member.name);DiscordJoining.review(c,ownerDiscord,request,true,"Verified ownership and clan membership in game");
        try(PreparedStatement s=RankingStore.query(c,"SELECT member_id FROM discord_member_links WHERE discord_user_id=?",applicant);ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(member.id,r.getObject(1,UUID.class));}
        assertEquals(100,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());assertEquals(3,count("members"));
        UUID conflicting=DiscordJoining.request(c,"444444444444444444",member.name);
        rejected(409,()->DiscordJoining.review(c,ownerDiscord,conflicting,true,"Attempted conflicting identity link"));
        DiscordJoining.review(c,ownerDiscord,conflicting,false,"Rejected: character already linked to its verified owner");
        assertEquals(2,count("discord_member_links"));assertEquals(2,count("audit"));
        RankingStore.execute(c,"UPDATE member_permissions SET permission='MEMBER' WHERE member_id=?",clanOwner.id);
        rejected(403,()->DiscordJoining.pending(c,ownerDiscord));
    }
    @Test public void dropCatalogReceivedStatusIsPersonalAndUsesActualAwards()throws Exception {
        AutomaticRewards.seed(c);
        JsonObject before=PointsLog.dropRewards(c,member.id,"Bandos chestplate","newest").get(0).getAsJsonObject();
        assertFalse(before.get("received").getAsBoolean());assertEquals(0,before.get("receivedCount").getAsInt());
        JsonObject drop=event("LOOT","2215:11832");RankingStore.event(c,member,drop);RankingStore.event(c,member,drop);
        JsonObject after=PointsLog.dropRewards(c,member.id,"Bandos chestplate","newest").get(0).getAsJsonObject();
        assertTrue(after.get("received").getAsBoolean());assertEquals(1,after.get("receivedCount").getAsInt());assertEquals(300,after.get("earnedPoints").getAsInt());
        assertFalse(PointsLog.dropRewards(c,admin.id,"Bandos chestplate","newest").get(0).getAsJsonObject().get("received").getAsBoolean());
        RankingStore.event(c,member,event("LOOT","2042:12922"));RankingStore.event(c,member,event("LOOT","2043:12922"));
        JsonObject fang=PointsLog.dropRewards(c,member.id,"Tanzanite fang","newest").get(0).getAsJsonObject();
        assertEquals(2,fang.get("receivedCount").getAsInt());assertEquals(500,fang.get("earnedPoints").getAsInt());
    }
    @Test public void dropExpansionUpgradesV1OnceWithoutRestoringRemovedOrDisabledRules()throws Exception {
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES ('automatic-rewards-v1')");
        RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category) VALUES ('LOOT','319:12819',42,1,false,'Drops')");
        AutomaticRewards.seed(c);
        assertArrayEquals(new int[]{50,2},RankingStore.rule(c,"LOOT","chest:barrows:4708"));
        assertArrayEquals(new int[]{1000,2},RankingStore.rule(c,"LOOT","11278:26372"));
        rejected(422,()->RankingStore.rule(c,"LOOT","319:12819"));
        rejected(422,()->RankingStore.rule(c,"LOOT","2215:11832")); // Removed base rule stays removed.
        RankingStore.execute(c,"UPDATE rules SET points=45 WHERE event_key='chest:barrows:4708'");
        AutomaticRewards.seed(c);assertArrayEquals(new int[]{45,2},RankingStore.rule(c,"LOOT","chest:barrows:4708"));
        assertEquals(0,count("ledger"));
    }
    @Test public void dropCatalogUsesEnabledCurrentRulesWithoutRequiringAwards()throws Exception {
        AutomaticRewards.seed(c);
        RankingStore.execute(c,"UPDATE rules SET points=321 WHERE type='LOOT' AND event_key='2215:11832'");
        JsonObject log=PointsLog.read(c,"player=Member&category=Drops&search=Bandos+chestplate");
        assertEquals(0,log.get("count").getAsInt());
        JsonArray rewards=log.getAsJsonArray("availableDrops");assertEquals(1,rewards.size());
        assertEquals(321,rewards.get(0).getAsJsonObject().get("points").getAsInt());
        assertEquals(1,PointsLog.dropRewards(c,member.id,"Tanzanite fang","highest").size()); // Three NPC phases, one item card.
        RankingStore.execute(c,"UPDATE rules SET enabled=false WHERE type='LOOT' AND event_key='2215:11832'");
        assertEquals(0,PointsLog.dropRewards(c,member.id,"Bandos chestplate","highest").size());
        assertEquals(0,PointsLog.dropRewards(c,member.id,"' OR 1=1 --","newest").size());
        assertEquals(0,count("ledger"));
    }
    @Test public void automaticPetsPayOnceAndDropsUseServerAmountsAndCaps()throws Exception {
        AutomaticRewards.seed(c);
        JsonObject pet=event("COLLECTION","pet kraken");
        assertEquals(750,RankingStore.event(c,member,pet).get("points").getAsInt());
        assertEquals("DUPLICATE",RankingStore.event(c,member,pet).get("status").getAsString());
        assertEquals("DUPLICATE",RankingStore.event(c,member,event("COLLECTION","pet kraken")).get("status").getAsString());
        assertEquals(1,count("ledger"));
        JsonObject drop=event("LOOT","2215:11832");
        assertEquals(300,RankingStore.event(c,member,drop).get("points").getAsInt());
        RankingStore.event(c,member,drop);assertEquals(2,count("ledger"));
        RankingStore.event(c,member,event("LOOT","2215:11832"));assertEquals(3,count("ledger"));
        rejected(422,()->RankingStore.event(c,member,event("LOOT","2215:11832")));
        rejected(422,()->RankingStore.event(c,member,event("LOOT","6632:11832")));
        assertEquals(1350,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        JsonObject forged=event("LOOT","chest:tombs of amascut:27277");forged.addProperty("points",999999);
        rejected(400,()->RankingStore.event(c,member,forged));
        assertEquals(1000,RankingStore.event(c,member,event("LOOT","chest:tombs of amascut:27277")).get("points").getAsInt());
    }
    @Test public void automaticCatalogPreservesExistingDisabledRulesAndLaterEdits()throws Exception {
        RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category) VALUES ('COLLECTION','pet kraken',20,1,false,'Pets')");
        AutomaticRewards.seed(c);
        rejected(422,()->RankingStore.event(c,member,event("COLLECTION","pet kraken")));
        RankingStore.execute(c,"UPDATE rules SET points=42,enabled=false WHERE type='LOOT' AND event_key='2215:11832'");
        AutomaticRewards.seed(c);
        try(PreparedStatement s=RankingStore.query(c,"SELECT points,enabled FROM rules WHERE type='LOOT' AND event_key='2215:11832'");ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(42,r.getInt(1));assertFalse(r.getBoolean(2));}
        assertEquals(0,count("ledger"));
    }
    @Test public void efficiencyRewardsBaselineDeduplicateCapAndNeverDeductOrRepayCorrections()throws Exception {
        Instant now=Instant.parse("2026-10-25T12:00:00Z");
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("100"),new java.math.BigDecimal("50"),now,now);
        assertEquals(0,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("101"),new java.math.BigDecimal("51"),now.plusSeconds(60),now.plusSeconds(60));assertEquals(1,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("999"),new java.math.BigDecimal("999"),now.plusSeconds(60),now.plusSeconds(61));assertEquals(1,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("99"),new java.math.BigDecimal("49"),now.plusSeconds(120),now.plusSeconds(120));assertEquals(1,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("101"),new java.math.BigDecimal("51"),now.plusSeconds(180),now.plusSeconds(180));assertEquals(1,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("200"),new java.math.BigDecimal("100"),now.plusSeconds(240),now.plusSeconds(240));assertEquals(2,count("ledger"));
        assertEquals(250,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        Instant monday=Instant.parse("2026-10-26T12:00:00Z");
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("200"),new java.math.BigDecimal("100"),monday,monday);assertEquals(2,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("201"),new java.math.BigDecimal("101"),monday.plusSeconds(60),monday.plusSeconds(60));assertEquals(3,count("ledger"));
        assertEquals(270,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        assertEquals(0,count("point_adjustments"));
        assertEquals(270,PointsLog.read(c,"player=Member&category=Efficiency&sort=newest").get("total").getAsInt());
    }
    @Test public void efficiencyFractionalGainsAccumulateButStaleDataCannotStartBaseline()throws Exception {
        Instant now=Instant.parse("2026-10-25T12:00:00Z");
        EfficiencyRewards.observe(c,member,java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,now.minusSeconds(90000),now);assertEquals(0,count("efficiency_reward_state"));
        EfficiencyRewards.observe(c,member,java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,now,now);
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("10.05"),java.math.BigDecimal.ZERO,now.plusSeconds(10),now.plusSeconds(10));assertEquals(0,count("ledger"));
        EfficiencyRewards.observe(c,member,new java.math.BigDecimal("10.1"),java.math.BigDecimal.ZERO,now.plusSeconds(20),now.plusSeconds(20));assertEquals(1,count("ledger"));
    }
    @Test public void rewardBoostTopsUpPaidGoalsOnceWithoutRewritingAwardsOrDeductions()throws Exception {
        try(InputStream input=RankingServer.class.getResourceAsStream("/starter-goals.sql");Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        MilestoneCatalog.seed(c);
        try(InputStream input=RankingServer.class.getResourceAsStream("/rank-thresholds-v1.sql");Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        MilestoneCatalog.applyRewards(c);
        UUID goal=UUID.fromString("20000000-0000-4000-8000-000000000001");
        ClanFeatures.sync(c,member,observation(goal,50));assertEquals(1,count("ledger"));
        RankSecurity.adjust(c,owner(),member.id,adjustment(-2));
        MilestoneCatalog.applyRewardBoost(c);
        assertEquals(2,count("ledger"));assertEquals(8,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        MilestoneCatalog.applyRewardBoost(c);ClanFeatures.sync(c,member,observation(goal,50));assertEquals(2,count("ledger"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT count(*) FROM audit WHERE action='MILESTONE_REWARD_TOPUP' AND target=?",member.id);ResultSet r=s.executeQuery()){r.next();assertEquals(1,r.getInt(1));}
        try(PreparedStatement s=RankingStore.query(c,"SELECT points FROM ledger WHERE member_id=? ORDER BY points",member.id);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(5,r.getInt(1));assertTrue(r.next());assertEquals(5,r.getInt(1));assertFalse(r.next());
        }
        assertEquals(1,count("point_adjustments"));
        MilestoneCatalog.applyCurrentRewards(c);
        assertEquals(3,count("ledger"));
        assertEquals(18,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
        MilestoneCatalog.applyCurrentRewards(c);
        ClanFeatures.sync(c,member,observation(goal,50));
        assertEquals(3,count("ledger"));assertEquals(1,count("point_adjustments"));
    }
    @Test public void activityRewardsMigrateOncePreservingCapsAndDisabledRules()throws Exception {
        RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category) VALUES ('LOOT','test',70,2,true,'Drops'),('LOOT','disabled',80,3,false,'Pets')");
        for(int i=0;i<2;i++)try(InputStream input=RankingServer.class.getResourceAsStream("/activity-rewards-v3.sql");Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        assertArrayEquals(new int[]{140,2},RankingStore.rule(c,"LOOT","test"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT points,enabled FROM rules WHERE event_key='disabled'");ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(80,r.getInt(1));assertFalse(r.getBoolean(2));}
        assertArrayEquals(new int[]{750,1},RankingStore.rule(c,"MANUAL","verified pet unlock"));
        assertEquals(0,count("ledger"));
    }
    @Test public void rewardRolloutCreditsTrackedCompletionsOnceAndPreservesStaffEdits()throws Exception {
        try(InputStream input=RankingServer.class.getResourceAsStream("/starter-goals.sql");Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        MilestoneCatalog.seed(c);
        try(InputStream input=RankingServer.class.getResourceAsStream("/rank-thresholds-v1.sql");Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        UUID attack=UUID.fromString("20000000-0000-4000-8000-000000000001");
        UUID fire=UUID.fromString("10000000-0000-4000-8000-000000000002");
        ClanFeatures.sync(c,member,observation(attack,50));assertEquals(0,count("ledger"));
        RankingStore.execute(c,"UPDATE goals SET points=99,enabled=false WHERE id=?",fire);
        MilestoneCatalog.applyRewards(c);
        ClanFeatures.sync(c,member,observation(attack,50));ClanFeatures.sync(c,member,observation(attack,50));
        assertEquals(1,count("ledger"));
        RankingStore.execute(c,"UPDATE goals SET points=42 WHERE id=?",attack);
        MilestoneCatalog.applyRewards(c);
        try(PreparedStatement s=RankingStore.query(c,"SELECT points,enabled FROM goals WHERE id=?",fire);ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(99,r.getInt(1));assertFalse(r.getBoolean(2));}
        try(PreparedStatement s=RankingStore.query(c,"SELECT points FROM goals WHERE id=?",attack);ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(42,r.getInt(1));}
        ClanFeatures.sync(c,member,observation(attack,50));assertEquals(1,count("ledger"));
        assertEquals(5,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
    }
    @Test public void expandedMilestonesSeedIdempotentlyAndPreserveStaffRewards()throws Exception {
        MilestoneCatalog.seed(c);
        assertEquals(131,count("goals"));
        UUID id=UUID.fromString(MilestoneCatalog.read().get(0).getAsJsonObject().get("id").getAsString());
        RankingStore.execute(c,"UPDATE goals SET points=25,enabled=false WHERE id=?",id);
        MilestoneCatalog.seed(c);assertEquals(131,count("goals"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT points,enabled FROM goals WHERE id=?",id);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(25,r.getInt(1));assertFalse(r.getBoolean(2));
        }
    }
    @Test public void higherRankIntervalsPreserveEarnedRanksAndApplyOnlyOnce()throws Exception {
        RankSecurity.adjust(c,owner(),member.id,adjustment(100));
        assertEquals("Corporal",RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("automaticRank").getAsString());
        String sql;
        try(InputStream input=RankingServer.class.getResourceAsStream("/rank-thresholds-v1.sql")) {sql=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        try(Statement s=c.createStatement()){s.execute(sql);}
        try(PreparedStatement s=RankingStore.query(c,"SELECT threshold FROM ranks WHERE name='Sergeant'");ResultSet r=s.executeQuery()){r.next();assertEquals(6000,r.getLong(1));}
        assertEquals("Corporal",RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("automaticRank").getAsString());
        RankingStore.execute(c,"UPDATE ranks SET threshold=6500 WHERE name='Sergeant'");
        try(Statement s=c.createStatement()){s.execute(sql);}
        try(PreparedStatement s=RankingStore.query(c,"SELECT threshold FROM ranks WHERE name='Sergeant'");ResultSet r=s.executeQuery()){r.next();assertEquals(6500,r.getLong(1));}
        assertEquals(1,count("point_adjustments"));
    }
    @Test public void operatorOnboardingAndLinkingCommitWithRequiredAuditActor()throws Exception {
        c.commit();
        RankingServer.command(new String[]{"add-member","Atazoth"},this::connection);
        UUID registered;
        try(PreparedStatement s=RankingStore.query(c,"SELECT id FROM members WHERE normalized_name='atazoth'");ResultSet r=s.executeQuery()) {
            assertTrue(r.next());registered=r.getObject(1,UUID.class);
        }
        RankingServer.command(new String[]{"add-member","Atazoth"},this::connection);
        RankingServer.command(new String[]{"link-discord","Atazoth","384823194965639170"},this::connection);
        try(PreparedStatement s=RankingStore.query(c,"SELECT actor,action,details FROM audit WHERE target=? ORDER BY id",registered);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(registered,r.getObject(1,UUID.class));assertEquals("MEMBER_PROVISIONED",r.getString(2));
            assertEquals("SERVER_OPERATOR",JsonParser.parseString(r.getString(3)).getAsJsonObject().get("actorKind").getAsString());
            assertTrue(r.next());assertEquals(registered,r.getObject(1,UUID.class));assertEquals("DISCORD_IDENTITY_LINKED",r.getString(2));assertFalse(r.next());
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT member_id FROM discord_member_links WHERE discord_user_id='384823194965639170'");ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(registered,r.getObject(1,UUID.class));
        }
        assertEquals(0,count("point_adjustments"));assertEquals(0,count("ledger"));
        try(PreparedStatement s=RankingStore.query(c,"SELECT count(*) FROM member_permissions WHERE member_id=?",registered);ResultSet r=s.executeQuery()){r.next();assertEquals(0,r.getInt(1));}
    }
    private static final String DISCORD_ID="123456789012345678";
    private java.net.http.HttpResponse<String> authRequest(int port,String path,String body)throws Exception {
        java.net.http.HttpRequest.Builder request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path));
        if(body!=null)request.header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
        return java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()
            .send(request.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    @Test public void discordDeviceLoginIsSingleUseBoundToSecretAndPreservesMemberPermissions()throws Exception {
        RankingStore.execute(c,"INSERT INTO discord_member_links VALUES (?,?,now())",DISCORD_ID,member.id);c.commit();
        String secret=DiscordLogin.secret(), browser=DiscordLogin.secret();UUID session=UUID.randomUUID();
        RankingStore.execute(c,"INSERT INTO discord_login_sessions(id,device_hash,browser_hash) VALUES (?,?,?)",session,Policy.hash(secret),Policy.hash(browser));c.commit();
        java.util.concurrent.atomic.AtomicInteger exchanges=new java.util.concurrent.atomic.AtomicInteger();
        Map<String,String> env=new HashMap<>();env.put("TRAP_PUBLIC_URL","https://clan.example");env.put("TRAP_DISCORD_CLIENT_ID","123456789012345679");
        DiscordLogin login=new DiscordLogin(this::connection,env,code->{exchanges.incrementAndGet();return DISCORD_ID;});
        com.sun.net.httpserver.HttpServer http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/",login::handle);http.start();int port=http.getAddress().getPort();
        try {
            assertEquals(400,authRequest(port,"/auth/discord/start?session="+session+"&browser=wrong",null).statusCode());
            java.net.http.HttpResponse<String> redirect=authRequest(port,"/auth/discord/start?session="+session+"&browser="+browser,null);
            assertEquals(302,redirect.statusCode());
            String state=DiscordLogin.parameters(java.net.URI.create(redirect.headers().firstValue("Location").get()).getRawQuery()).get("state");
            assertEquals(400,authRequest(port,"/auth/discord/callback?code=fake&state="+DiscordLogin.secret(),null).statusCode());assertEquals(0,exchanges.get());
            String callback="/auth/discord/callback?code=approved&state="+state;
            assertEquals(200,authRequest(port,callback,null).statusCode());
            assertEquals(400,authRequest(port,callback,null).statusCode());assertEquals(1,exchanges.get());
            assertEquals(410,authRequest(port,"/auth/discord/poll","{\"session\":\""+session+"\",\"secret\":\""+DiscordLogin.secret()+"\"}").statusCode());
            String poll="{\"session\":\""+session+"\",\"secret\":\""+secret+"\",\"permission\":\"OWNER\",\"member\":\""+clanOwner.id+"\"}";
            java.net.http.HttpResponse<String> response=authRequest(port,"/auth/discord/poll",poll);assertEquals(200,response.statusCode());
            String token=JsonParser.parseString(response.body()).getAsJsonObject().get("token").getAsString();
            RankingStore.Actor authenticated=RankingStore.authenticate(c,"Bearer "+token);
            assertEquals(member.id,authenticated.id);assertEquals(RankSecurity.Role.MEMBER,authenticated.role);
            rejected(403,()->RankSecurity.adjust(c,authenticated,member.id,adjustment(-1)));
            assertEquals(410,authRequest(port,"/auth/discord/poll",poll).statusCode());
            try(PreparedStatement s=RankingStore.query(c,"SELECT count(*) FROM credentials WHERE token_hash=?",token);ResultSet r=s.executeQuery()){r.next();assertEquals(0,r.getInt(1));}
        } finally {http.stop(0);}
    }
    @Test public void unlinkedDiscordIdentityCannotClaimAnOwnerOrReceiveCredentials()throws Exception {
        String secret=DiscordLogin.secret(),state=DiscordLogin.secret();UUID session=UUID.randomUUID();
        RankingStore.execute(c,"INSERT INTO discord_login_sessions(id,device_hash,browser_hash,state_hash) VALUES (?,?,?,?)",session,Policy.hash(secret),Policy.hash(DiscordLogin.secret()),Policy.hash(state));c.commit();
        DiscordLogin login=new DiscordLogin(this::connection,Collections.emptyMap(),code->DISCORD_ID);
        com.sun.net.httpserver.HttpServer http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/",login::handle);http.start();int port=http.getAddress().getPort();
        try {
            assertEquals(403,authRequest(port,"/auth/discord/callback?code=fake&state="+state+"&name=Owner&permission=OWNER",null).statusCode());
            java.net.http.HttpResponse<String> pending=authRequest(port,"/auth/discord/poll","{\"session\":\""+session+"\",\"secret\":\""+secret+"\"}");
            assertEquals(200,pending.statusCode());assertFalse(pending.body().contains("token"));assertEquals(0,count("discord_member_links"));
        } finally {http.stop(0);}
    }
    @Test public void pointsLogPreservesCollectionIdentityForActualItemArtwork()throws Exception {
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        JsonObject log=PointsLog.read(c,"player="+java.net.URLEncoder.encode(member.name,StandardCharsets.UTF_8)+"&group=true");
        JsonObject entry=log.getAsJsonArray("entries").get(0).getAsJsonObject();assertEquals("COLLECTION",entry.get("sourceType").getAsString());assertEquals("abyssal whip",entry.get("sourceKey").getAsString());
    }
    @Test public void pointsLogPagesFullLedgerGroupsFiltersAndRetainsSignedAdjustments()throws Exception {
        for(int i=0;i<30;i++)RankSecurity.adjust(c,owner(),member.id,adjustment(2));
        RankSecurity.adjust(c,owner(),member.id,adjustment(-5));
        String query="player="+java.net.URLEncoder.encode(member.name,StandardCharsets.UTF_8)+"&category=Misc";
        JsonObject page=PointsLog.read(c,query);assertEquals(31,page.get("count").getAsInt());assertEquals(55,page.get("total").getAsInt());assertEquals(24,page.getAsJsonArray("entries").size());
        assertEquals(7,PointsLog.read(c,query+"&page=2").getAsJsonArray("entries").size());
        JsonObject grouped=PointsLog.read(c,query+"&group=true");assertEquals(1,grouped.get("count").getAsInt());assertEquals(31,grouped.getAsJsonArray("entries").get(0).getAsJsonObject().get("occurrences").getAsInt());
        assertEquals(0,PointsLog.read(c,query+"&search=missing").get("count").getAsInt());
        assertEquals(0,PointsLog.read(c,"player="+java.net.URLEncoder.encode(member.name,StandardCharsets.UTF_8)+"&category=Pets").get("count").getAsInt());
        boolean negative=false;
        for(int i=1;i<=2;i++)for(JsonElement entry:PointsLog.read(c,query+"&sort=oldest&page="+i).getAsJsonArray("entries"))negative|=entry.getAsJsonObject().get("points").getAsInt()<0;
        assertTrue(negative);
    }
    @Test public void allSuppliedStaffRanksRequireOwnerAndNeverGrantApiPermissions()throws Exception {
        JsonObject data=RankingStore.dashboard(c,member,member.name);
        assertEquals(9,data.getAsJsonArray("ranks").size());assertEquals(5,data.getAsJsonArray("protectedRanks").size());
        for(String rank:new String[]{"Owner","Deputy Owner","Master","Major","Proselyte"}) {
            rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("ASSIGN",rank)));
            RankSecurity.changeRank(c,owner(),member.id,rankAction("ASSIGN",rank));
            rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("REMOVE",null)));
            assertFalse(RankingStore.authenticate(c,"Bearer member-token").admin);
            assertFalse(RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("promotionEligible").getAsBoolean());
        }
    }
    @Test public void legacyLabelsResolveWithoutRewritingPermanentHistory()throws Exception {
        RankingStore.execute(c,"INSERT INTO earned_rank_history(member_id,rank,threshold,points_at_earning) VALUES (?,'Enforcer',500,500)",member.id);
        RankingStore.execute(c,"INSERT INTO member_rank_state VALUES (?,'Enforcer',false)",member.id);
        JsonObject profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
        assertEquals("Sergeant",profile.get("automaticRank").getAsString());assertEquals("Sergeant",profile.get("assignedRank").getAsString());
        try(PreparedStatement s=RankingStore.query(c,"SELECT rank FROM earned_rank_history WHERE member_id=? AND threshold=500",member.id);ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals("Enforcer",r.getString(1));
        }
        RankSecurity.changeRank(c,owner(),member.id,rankAction("REMOVE",null));
        assertEquals("Sergeant",RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("rank").getAsString());
    }
    private JsonObject adjustment(int amount) {
        JsonObject body=new JsonObject();body.addProperty("id",UUID.randomUUID().toString());
        body.addProperty("points",amount);body.addProperty("reason","Verified lifetime correction");return body;
    }
    @Test public void concurrentOwnerDeductionsCannotOverdrawTheBalance()throws Exception {
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));c.commit();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        Callable<String> deduct=()-> {
            try(Connection other=connection()) {
                other.setAutoCommit(false);
                try {RankSecurity.adjust(other,owner(),member.id,adjustment(-75));other.commit();return "deducted";}
                catch(Policy.Rejected failure){other.rollback();assertEquals(422,failure.status);return "rejected";}
            }
        };
        try {
            Future<String> first=pool.submit(deduct),second=pool.submit(deduct);
            assertEquals(new HashSet<>(Arrays.asList("deducted","rejected")),new HashSet<>(Arrays.asList(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))));
            JsonObject profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
            assertEquals(25,profile.get("points").getAsLong());assertEquals("Corporal",profile.get("rank").getAsString());
            assertEquals(1,count("point_adjustments"));assertEquals(1,count("audit"));
        } finally {pool.shutdownNow();}
    }
    @Test public void deductionsRetainEarnedRanksAndOnlyOwnerCanAuthorizeDemotion()throws Exception {
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        long earned=count("earned_rank_history");
        JsonObject deduction=adjustment(-100);
        rejected(403,()->RankSecurity.adjust(c,admin,member.id,deduction));
        rejected(403,()->RankSecurity.adjust(c,new RankingStore.Actor(member.id,member.name,RankSecurity.Role.EVENT_MANAGER),member.id,deduction));
        rejected(403,()->RankSecurity.adjust(c,member,member.id,deduction));
        RankSecurity.adjust(c,owner(),member.id,deduction);RankSecurity.adjust(c,owner(),member.id,deduction);
        JsonObject profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
        assertEquals(0,profile.get("points").getAsLong());
        assertEquals(100,profile.get("lifetimeEarnedPoints").getAsLong());
        assertEquals("Recruit",profile.get("balanceRank").getAsString());
        assertEquals("Corporal",profile.get("automaticRank").getAsString());
        assertEquals("Corporal",profile.get("rank").getAsString());
        assertEquals(earned,count("earned_rank_history"));
        assertEquals(3,profile.getAsJsonArray("earnedRankHistory").size());
        rejected(422,()->RankSecurity.adjust(c,owner(),member.id,adjustment(-1)));
        rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("ASSIGN","Recruit")));
        RankSecurity.changeRank(c,admin,member.id,rankAction("ASSIGN","Sergeant"));
        rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("REMOVE",null)));
        RankSecurity.changeRank(c,owner(),member.id,rankAction("ASSIGN","Recruit"));
        profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
        assertEquals("Recruit",profile.get("rank").getAsString());
        assertEquals("Corporal",profile.get("automaticRank").getAsString());
        JsonObject details=RankSecurity.auditLog(c,owner(),0).get(0).getAsJsonObject().getAsJsonObject("details");
        assertEquals(member.name,details.get("memberName").getAsString());
        assertEquals(clanOwner.name,details.get("actorName").getAsString());
        assertEquals(-100,details.get("points").getAsInt());
        assertEquals(1,count("point_adjustments"));
    }
    @Test public void databaseGuardsRejectNonOwnerDeductionsAndHistoryErasure()throws Exception {
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        Savepoint save=c.setSavepoint();
        try { RankingStore.execute(c,"INSERT INTO point_adjustments(id,member_id,points,actor,reason) VALUES (?,?, -1,?,'Direct bypass attempt')",UUID.randomUUID(),member.id,admin.id);fail("Database accepted non-owner deduction"); }
        catch(SQLException expected){c.rollback(save);}
        save=c.setSavepoint();
        try { RankingStore.execute(c,"INSERT INTO point_adjustments(id,member_id,points,actor,reason) VALUES (?,?, -101,?,'Overdraw')",UUID.randomUUID(),member.id,clanOwner.id);fail("Database accepted negative balance"); }
        catch(SQLException expected){c.rollback(save);}
        save=c.setSavepoint();
        try { RankingStore.execute(c,"DELETE FROM earned_rank_history");fail("Rank history erased"); }
        catch(SQLException expected){c.rollback(save);}
        save=c.setSavepoint();
        try { RankingStore.execute(c,"TRUNCATE ledger");fail("Lifetime earnings reset"); }
        catch(SQLException expected){c.rollback(save);}
        assertEquals(100,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsLong());
    }
    @Test public void realHttpCredentialsEnforceOwnerDeductionsAndRevocation()throws Exception {
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        RankingStore.execute(c,"INSERT INTO credentials VALUES (?,?,true,now()+interval '1 day',false)",Policy.hash("admin-token"),admin.id);
        RankingStore.execute(c,"INSERT INTO credentials VALUES (?,?,false,now()+interval '1 day',false)",Policy.hash("owner-token"),clanOwner.id);
        RankingStore.Actor manager=new RankingStore.Actor(UUID.randomUUID(),"Manager",RankSecurity.Role.EVENT_MANAGER);
        RankingStore.execute(c,"INSERT INTO members VALUES (?,?,?,true)",manager.id,manager.name,Policy.normalize(manager.name));
        RankingStore.execute(c,"INSERT INTO member_permissions VALUES (?,'EVENT_MANAGER')",manager.id);
        RankingStore.execute(c,"INSERT INTO credentials VALUES (?,?,false,now()+interval '1 day',false)",Policy.hash("manager-token"),manager.id);c.commit();
        com.sun.net.httpserver.HttpServer http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/",new RankingServer(this::connection)::handle);http.start();
        try {
            java.net.URI uri=java.net.URI.create("http://127.0.0.1:"+http.getAddress().getPort()+"/v1/admin/members/"+member.id+"/points");
            java.net.http.HttpClient client=java.net.http.HttpClient.newHttpClient();JsonObject body=adjustment(-25);
            body.addProperty("permission","OWNER");body.addProperty("actor",clanOwner.id.toString());
            for(String token:new String[]{"member-token","admin-token","manager-token","owner-token"}) {
                int expected=token.equals("owner-token")?200:403;
                assertEquals(token,expected,client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer "+token)
                    .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            RankingStore.execute(c,"UPDATE credentials SET revoked=true WHERE member_id=?",clanOwner.id);c.commit();
            assertEquals(401,client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner-token")
                .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(adjustment(-1).toString())).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(75,RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile").get("points").getAsInt());
            assertEquals(1,count("point_adjustments"));
        } finally {http.stop(0);}
    }
    private final RankingStore.Actor clanOwner = new RankingStore.Actor(UUID.randomUUID(),"ZOwner",RankSecurity.Role.OWNER);
    private RankingStore.Actor owner() { return clanOwner; }
    private JsonObject rankAction(String action,String rank) {
        JsonObject body=new JsonObject();body.addProperty("action",action);body.addProperty("reason","Verified staff decision");
        if(rank!=null)body.addProperty("rank",rank);return body;
    }
    @Test public void leadershipAndLocksSurvivePointChangesAndRequireOwnerForRemoval()throws Exception {
        RankSecurity.changeRank(c,owner(),member.id,rankAction("ASSIGN","Owner"));
        rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("REMOVE",null)));
        rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("ASSIGN","Corporal")));
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        JsonObject profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
        assertEquals("Corporal",profile.get("automaticRank").getAsString());
        assertEquals("Owner",profile.get("assignedRank").getAsString());
        assertEquals(100,profile.get("points").getAsInt());
        assertFalse(profile.get("promotionEligible").getAsBoolean());
        RankSecurity.changeRank(c,owner(),member.id,rankAction("REMOVE",null));
        RankSecurity.changeRank(c,owner(),member.id,rankAction("LOCK",null));
        rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("ASSIGN","Recruit")));
        rejected(403,()->RankSecurity.changeRank(c,admin,member.id,rankAction("UNLOCK",null)));
        RankSecurity.changeRank(c,owner(),member.id,rankAction("UNLOCK",null));
        RankSecurity.changeRank(c,admin,member.id,rankAction("REMOVE",null));
        profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
        assertTrue(profile.get("assignedRank").isJsonNull());
        assertEquals(100,profile.get("points").getAsInt());
        assertEquals(5,count("audit"));
    }
    @Test public void signedAdjustmentsAreAuditedRetrySafeAndPreserveOverrides()throws Exception {
        RankSecurity.changeRank(c,owner(),member.id,rankAction("ASSIGN","Owner"));
        JsonObject body=new JsonObject();body.addProperty("id",UUID.randomUUID().toString());body.addProperty("points",100);body.addProperty("reason","Attendance correction");
        rejected(403,()->RankSecurity.adjust(c,member,member.id,body));
        RankSecurity.adjust(c,admin,member.id,body);RankSecurity.adjust(c,admin,member.id,body);
        assertEquals(1,count("point_adjustments"));assertEquals(2,count("audit"));
        body.addProperty("points",99);rejected(409,()->RankSecurity.adjust(c,admin,member.id,body));
        body.addProperty("id",UUID.randomUUID().toString());body.addProperty("points",-25);RankSecurity.adjust(c,owner(),member.id,body);
        JsonObject profile=RankingStore.dashboard(c,member,member.name).getAsJsonObject("profile");
        assertEquals(75,profile.get("points").getAsInt());assertEquals("Owner",profile.get("assignedRank").getAsString());
        body.addProperty("id",UUID.randomUUID().toString());body.addProperty("points",-100);rejected(422,()->RankSecurity.adjust(c,owner(),member.id,body));
        JsonArray audit=RankSecurity.auditLog(c,admin,0);assertEquals(3,audit.size());
        assertEquals(-25,audit.get(2).getAsJsonObject().getAsJsonObject("details").get("points").getAsInt());
        java.sql.Savepoint save=c.setSavepoint();
        try { RankingStore.execute(c,"DELETE FROM audit");fail("Audit must be permanent"); }catch(SQLException expected){c.rollback(save);}
        save=c.setSavepoint();
        try { RankingStore.execute(c,"UPDATE point_adjustments SET points=1");fail("Adjustments must be permanent"); }catch(SQLException expected){c.rollback(save);}
    }
    @Test public void permissionsAreServerControlledAndDisplayedRanksDoNotGrantAuthority()throws Exception {
        JsonObject body=new JsonObject();body.addProperty("permission","EVENT_MANAGER");body.addProperty("reason","Manage clan calendar");
        rejected(403,()->RankSecurity.permission(c,admin,member.id,body));
        RankSecurity.permission(c,owner(),member.id,body);
        RankingStore.Actor manager=RankingStore.authenticate(c,"Bearer member-token");
        assertEquals(RankSecurity.Role.EVENT_MANAGER,manager.role);assertFalse(manager.admin);
        rejected(403,()->RankSecurity.changeRank(c,manager,member.id,rankAction("ASSIGN","Recruit")));
        rejected(403,()->RankingStore.pending(c,manager));
        UUID event=clanEvent(10);JsonObject publication=new JsonObject();publication.addProperty("published",false);
        RankSecurity.publishEvent(c,manager,event,publication);
        body.addProperty("permission","OWNER");rejected(403,()->RankSecurity.permission(c,owner(),member.id,body));
        body.addProperty("permission","MEMBER");RankSecurity.permission(c,owner(),member.id,body);
        assertEquals(RankSecurity.Role.MEMBER,RankingStore.authenticate(c,"Bearer member-token").role);
        RankSecurity.changeRank(c,owner(),member.id,rankAction("ASSIGN","Administrator"));
        assertFalse(RankingStore.authenticate(c,"Bearer member-token").admin);
    }
    @Test public void promotionReconciliationIsDurableDeduplicatedAndSkipsProtectedRanks()throws Exception {
        RankingStore.event(c,member,event("COLLECTION","abyssal whip"));
        RankSecurity.changeRank(c,owner(),admin.id,rankAction("ASSIGN","Owner"));
        c.commit();
        RankSecurity.promotions(this::connection);RankSecurity.promotions(this::connection);
        assertEquals(1,count("promotion_notices"));assertEquals(2,count("discord_outbox"));
        RankSecurity.changeRank(c,owner(),member.id,rankAction("LOCK",null));
        JsonObject extra=new JsonObject();extra.addProperty("id",UUID.randomUUID().toString());extra.addProperty("points",100);extra.addProperty("reason","Verified award");
        RankSecurity.adjust(c,owner(),member.id,extra);
        RankingStore.execute(c,"UPDATE ranks SET threshold=150 WHERE name='Sergeant'");c.commit();
        RankSecurity.promotions(this::connection);assertEquals(1,count("promotion_notices"));
        RankSecurity.changeRank(c,owner(),member.id,rankAction("UNLOCK",null));c.commit();
        RankSecurity.promotions(this::connection);assertEquals(2,count("promotion_notices"));
    }
    private Connection c;
    private String schema;
    private final RankingStore.Actor member = new RankingStore.Actor(UUID.randomUUID(), "Member", false);
    private final RankingStore.Actor admin = new RankingStore.Actor(UUID.randomUUID(), "Admin", true);

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(System.getenv("TRAP_TEST_DATABASE_URL"),
            System.getenv("TRAP_TEST_DATABASE_USER"), System.getenv("TRAP_TEST_DATABASE_PASSWORD"));
        if (schema != null) try (Statement s = connection.createStatement()) { s.execute("SET search_path TO " + schema); }
        return connection;
    }

    @Before public void setup() throws Exception {
        c = connection();
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (Statement s = c.createStatement()) { s.execute("CREATE SCHEMA " + schema); s.execute("SET search_path TO " + schema); }
        try (InputStream input = RankingServer.class.getResourceAsStream("/schema.sql"); Statement s = c.createStatement()) {
            s.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        // Production init creates this in rank-thresholds-v1.sql before reward migrations.
        // Keep legacy rank fixtures here while providing the same migration registry.
        RankingStore.execute(c,"CREATE TABLE configuration_migrations (name varchar(100) PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())");
        for (RankingStore.Actor actor : new RankingStore.Actor[] {member, admin, clanOwner}) {
            RankingStore.execute(c, "INSERT INTO members VALUES (?,?,?,true)", actor.id, actor.name, Policy.normalize(actor.name));
        }
        RankingStore.execute(c,"INSERT INTO member_permissions VALUES (?,'OWNER')",clanOwner.id);
        RankingStore.execute(c,"INSERT INTO member_rank_state VALUES (?,'Owner',false)",clanOwner.id);
        RankingStore.execute(c, "INSERT INTO credentials VALUES (?,?,false,now()+interval '1 day',false)", Policy.hash("member-token"), member.id);
        RankingStore.execute(c, "INSERT INTO rules(type,event_key,points,daily_limit,enabled) VALUES ('LOOT','1:2',25,2,true),('COLLECTION','abyssal whip',100,10,true),('COMBAT','perfect kill',50,10,true),('MANUAL','clan event',75,1,true)");
        c.setAutoCommit(false);
    }

    @After public void cleanup() throws Exception {
        if (c == null) return;
        c.rollback(); c.setAutoCommit(true);
        // schema is generated entirely above, contains only [a-z0-9_], and belongs to this test.
        if (schema != null) try (Statement s = c.createStatement()) { s.execute("DROP SCHEMA " + schema + " CASCADE"); }
        c.close();
    }

    private JsonObject event(String type, String key) {
        JsonObject body = new JsonObject(); body.addProperty("id", UUID.randomUUID().toString());
        body.addProperty("player", member.name); body.addProperty("type", type); body.addProperty("key", key);
        body.addProperty("observedAt", Instant.now().toString()); return body;
    }

    private JsonObject reward() {
        JsonObject body = new JsonObject(); body.addProperty("id", UUID.randomUUID().toString());
        body.addProperty("key", "clan event"); body.addProperty("reason", "Attended clan event; staff can verify attendance"); return body;
    }

    private long count(String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM " + table)) { r.next(); return r.getLong(1); }
    }

    private void rejected(int code, SqlCall call) throws Exception {
        try { call.run(); fail("Expected rejection"); }
        catch (Policy.Rejected failure) { assertEquals(code, failure.status); }
    }
    private interface SqlCall { void run() throws Exception; }

    @Test public void exactAndSemanticDuplicatesNeverDoubleAward() throws Exception {
        JsonObject body = event("COLLECTION", "abyssal whip");
        assertEquals("AWARDED", RankingStore.event(c, member, body).get("status").getAsString());
        assertEquals("DUPLICATE", RankingStore.event(c, member, body).get("status").getAsString());
        assertEquals("DUPLICATE", RankingStore.event(c, member, event("COLLECTION", "abyssal whip")).get("status").getAsString());
        assertEquals(1, count("events")); assertEquals(1, count("ledger")); assertEquals(1, count("discord_outbox"));
        assertEquals(100, RankingStore.dashboard(c, member, "Member").getAsJsonObject("profile").get("points").getAsLong());
    }

    @Test public void clientCannotChoosePointsOrAnotherRecipient() throws Exception {
        JsonObject body = event("LOOT", "1:2"); body.addProperty("points", 999999);
        rejected(400, () -> RankingStore.event(c, member, body));
        body.remove("points"); body.addProperty("player", admin.name);
        rejected(403, () -> RankingStore.event(c, member, body));
        rejected(422, () -> RankingStore.event(c, member, event("LOOT", "unknown")));
        assertEquals(0, count("ledger"));
    }

    @Test public void dailyLimitsAndIdReuseAreEnforced() throws Exception {
        JsonObject body = event("LOOT", "1:2");
        RankingStore.event(c, member, body);
        body.addProperty("key", "different");
        rejected(409, () -> RankingStore.event(c, member, body));
        RankingStore.event(c, member, event("LOOT", "1:2"));
        rejected(422, () -> RankingStore.event(c, member, event("LOOT", "1:2")));
        assertEquals(2, count("ledger"));
    }

    @Test public void pendingRewardsNeedDifferentAdministratorAndReviewIsIdempotent() throws Exception {
        JsonObject request = reward();
        JsonObject result = RankingStore.requestReward(c, member, request);
        UUID id = UUID.fromString(result.get("id").getAsString());
        assertEquals(id.toString(), RankingStore.requestReward(c, member, request).get("id").getAsString());
        assertEquals(0, count("ledger"));
        rejected(403, () -> RankingStore.review(c, member, id, true));
        rejected(403, () -> RankingStore.review(c, new RankingStore.Actor(member.id, member.name, true), id, true));
        RankingStore.review(c, admin, id, true); RankingStore.review(c, admin, id, true);
        assertEquals(1, count("ledger")); assertEquals(1, count("discord_outbox"));
        assertEquals(2, count("audit"));
        UUID second = UUID.fromString(RankingStore.requestReward(c, member, reward()).get("id").getAsString());
        rejected(422, () -> RankingStore.review(c, admin, second, true));
    }

    @Test public void rejectedRewardsNeverAwardAndDisabledRulesCannotBeApproved() throws Exception {
        UUID first = UUID.fromString(RankingStore.requestReward(c, member, reward()).get("id").getAsString());
        RankingStore.review(c, admin, first, false);
        assertEquals("REJECTED", RankingStore.review(c, admin, first, true).get("status").getAsString());
        UUID second = UUID.fromString(RankingStore.requestReward(c, member, reward()).get("id").getAsString());
        RankingStore.execute(c, "UPDATE rules SET enabled=false WHERE type='MANUAL'");
        rejected(422, () -> RankingStore.review(c, admin, second, true));
        assertEquals(0, count("ledger"));
    }

    @Test public void revokedExpiredAndInactiveMembersCannotAuthenticateOrEarn() throws Exception {
        assertEquals(member.id, RankingStore.authenticate(c, "Bearer member-token").id);
        rejected(401, () -> RankingStore.authenticate(c, "Bearer other"));
        RankingStore.execute(c, "UPDATE credentials SET revoked=true");
        rejected(401, () -> RankingStore.authenticate(c, "Bearer member-token"));
        RankingStore.execute(c, "UPDATE credentials SET revoked=false,expires_at=now()-interval '1 second'");
        rejected(401, () -> RankingStore.authenticate(c, "Bearer member-token"));
        RankingStore.execute(c, "UPDATE credentials SET expires_at=now()+interval '1 day'");
        RankingStore.execute(c, "UPDATE members SET active=false WHERE id=?", member.id);
        rejected(401, () -> RankingStore.authenticate(c, "Bearer member-token"));
        rejected(403, () -> RankingStore.event(c, member, event("LOOT", "1:2")));
    }

    @Test public void transactionRollbackRemovesAwardAndOutboxTogether() throws Exception {
        RankingStore.event(c, member, event("COMBAT", "perfect kill"));
        c.rollback();
        assertEquals(0, count("ledger")); assertEquals(0, count("events")); assertEquals(0, count("discord_outbox"));
    }

    @Test public void thresholdsAndSourcesAreServerConfiguredAndHistoryIsVisible() throws Exception {
        RankingStore.execute(c, "UPDATE ranks SET threshold=25 WHERE name='Pawn'");
        RankingStore.execute(c, "UPDATE ranks SET threshold=40 WHERE name='Corporal'");
        RankingStore.event(c, member, event("COMBAT", "perfect kill"));
        JsonObject data = RankingStore.dashboard(c, member, "Member");
        assertEquals("Corporal", data.getAsJsonObject("profile").get("rank").getAsString());
        assertEquals(4, data.getAsJsonArray("sources").size());
        assertEquals(1, data.getAsJsonArray("history").size());
        assertEquals(member.name, data.getAsJsonArray("leaderboard").get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test public void concurrentRetriesAwardExactlyOnce() throws Exception {
        c.commit();
        JsonObject body = event("LOOT", "1:2");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        Callable<String> call = () -> {
            try (Connection other = connection()) {
                other.setAutoCommit(false); ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                String status = RankingStore.event(other, member, body).get("status").getAsString();
                other.commit(); return status;
            }
        };
        try {
            Future<String> a = pool.submit(call), b = pool.submit(call);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            Set<String> statuses = new HashSet<>(Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)));
            assertEquals(new HashSet<>(Arrays.asList("AWARDED", "DUPLICATE")), statuses);
            assertEquals(1, count("ledger")); assertEquals(1, count("discord_outbox"));
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    @Test public void concurrentDistinctEventsCannotExceedDailyCap() throws Exception {
        RankingStore.execute(c, "UPDATE rules SET daily_limit=1 WHERE type='LOOT'"); c.commit();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> call = () -> {
            try (Connection other = connection()) {
                other.setAutoCommit(false);
                try {
                    RankingStore.event(other, member, event("LOOT", "1:2")); other.commit(); return "awarded";
                } catch (Policy.Rejected failure) { other.rollback(); assertEquals(422, failure.status); return "capped"; }
            }
        };
        try {
            Future<String> a = pool.submit(call), b = pool.submit(call);
            assertEquals(new HashSet<>(Arrays.asList("awarded", "capped")), new HashSet<>(Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))));
            assertEquals(1, count("ledger"));
        } finally { pool.shutdownNow(); }
    }

    @Test public void httpApiEnforcesCredentialAndAdministratorBoundaries() throws Exception {
        c.commit();
        com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        RankingServer service = new RankingServer(this::connection); http.createContext("/", service::handle); http.start();
        String origin = "http://127.0.0.1:" + http.getAddress().getPort();
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        try {
            java.net.URI dashboard = java.net.URI.create(origin + "/v1/dashboard?player=Member");
            assertEquals(401, client.send(java.net.http.HttpRequest.newBuilder(dashboard).build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            java.net.http.HttpResponse<String> valid = client.send(java.net.http.HttpRequest.newBuilder(dashboard).header("Authorization", "Bearer member-token").build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, valid.statusCode());
            assertEquals("Member", JsonParser.parseString(valid.body()).getAsJsonObject().getAsJsonObject("profile").get("name").getAsString());
            assertEquals(403, client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin + "/v1/admin/rewards")).header("Authorization", "Bearer member-token").build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            JsonObject spoof = event("LOOT", "1:2"); spoof.addProperty("player", "Admin");
            assertEquals(403, client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin + "/v1/events")).header("Authorization", "Bearer member-token").header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(spoof.toString())).build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally { http.stop(0); }
    }

    private UUID goal(String source,String key,String category,int target,int points)throws Exception {
        UUID id=UUID.randomUUID();
        RankingStore.execute(c,"INSERT INTO goals(id,name,category,section,source,source_key,target,points,enabled) VALUES (?,'Test goal',?,'Test section',?,?,?,?,true)",id,category,source,key,target,points);return id;
    }
    private JsonObject observation(UUID id,long value) {
        JsonObject body=new JsonObject(),values=new JsonObject();body.addProperty("player",member.name);values.addProperty(id.toString(),value);body.add("values",values);return body;
    }
    @Test public void progressRewardsAreServerCalculatedAndOnlyAwardOnce() throws Exception {
        UUID id=goal("TOTAL_LEVEL","total","Milestones",2000,75);
        ClanFeatures.sync(c,member,observation(id,1999));assertEquals(0,count("ledger"));
        ClanFeatures.sync(c,member,observation(id,2000));ClanFeatures.sync(c,member,observation(id,2100));
        assertEquals(1,count("ledger"));assertEquals(1,count("discord_outbox"));
        JsonObject dashboard=RankingStore.dashboard(c,member,"Member");
        assertEquals(75,dashboard.getAsJsonObject("profile").getAsJsonObject("breakdown").get("Milestones").getAsInt());
        assertTrue(dashboard.getAsJsonArray("goals").get(0).getAsJsonObject().get("completed").getAsBoolean());
        ClanFeatures.sync(c,member,observation(id,0));assertEquals(1,count("ledger"));
    }
    @Test public void clientsCannotCompleteManualOrEventGoalsThroughProgressPayload()throws Exception {
        UUID manual=goal("MANUAL","clan event","Diaries",1,500);
        UUID automatic=goal("EVENT","COMBAT:*","Combat",1,500);
        rejected(422,()->ClanFeatures.sync(c,member,observation(manual,1)));
        rejected(422,()->ClanFeatures.sync(c,member,observation(automatic,1)));
        assertEquals(0,count("ledger"));
    }
    @Test public void eventGoalsAndApprovedDiaryTasksBecomeCompleted()throws Exception {
        goal("EVENT","COMBAT:*","Combat",1,10);
        goal("MANUAL","clan event","Diaries",1,500);
        RankingStore.event(c,member,event("COMBAT","perfect kill"));assertEquals(2,count("ledger"));
        UUID reward=UUID.fromString(RankingStore.requestReward(c,member,reward()).get("id").getAsString());
        RankingStore.review(c,admin,reward,true);assertEquals(3,count("ledger"));
        JsonArray goals=RankingStore.dashboard(c,member,"Member").getAsJsonArray("goals");
        assertEquals(2,goals.size());for(JsonElement row:goals)assertTrue(row.getAsJsonObject().get("completed").getAsBoolean());
    }
    @Test public void trackingOnlyGoalsCanBeEnabledForRewardsOnce()throws Exception {
        UUID goal=goal("TOTAL_LEVEL","total","Milestones",1000,0);
        ClanFeatures.sync(c,member,observation(goal,2000));assertEquals(0,count("ledger"));
        RankingStore.execute(c,"UPDATE goals SET points=50 WHERE id=?",goal);
        ClanFeatures.sync(c,member,observation(goal,2000));ClanFeatures.sync(c,member,observation(goal,2000));assertEquals(1,count("ledger"));
    }
    private UUID clanEvent(int capacity)throws Exception {
        UUID id=UUID.randomUUID();RankingStore.execute(c,"INSERT INTO clan_events(id,title,starts_at,ends_at,registration_closes,capacity,published) VALUES (?,'Clan PvM',now()+interval '1 hour',now()+interval '3 hours',now()+interval '30 minutes',?,true)",id,capacity);return id;
    }
    @Test public void eventRegistrationIsIdempotentCapacityLimitedAndCancelable()throws Exception {
        UUID event=clanEvent(1);
        ClanFeatures.register(c,member,event,true);ClanFeatures.register(c,member,event,true);assertEquals(1,count("event_registrations"));
        rejected(422,()->ClanFeatures.register(c,admin,event,true));
        JsonObject registered=RankingStore.dashboard(c,member,"Member").getAsJsonArray("clanEvents").get(0).getAsJsonObject();
        assertTrue(registered.get("registered").getAsBoolean());
        ClanFeatures.register(c,member,event,false);ClanFeatures.register(c,admin,event,true);assertEquals(1,count("event_registrations"));
        RankingStore.execute(c,"UPDATE clan_events SET registration_closes=now()-interval '1 minute' WHERE id=?",event);
        rejected(422,()->ClanFeatures.register(c,member,event,true));
    }
    @Test public void memberBrowsingOmitsPrivateManualRequests()throws Exception {
        RankingStore.requestReward(c,member,reward());
        JsonObject memberProfile=ClanFeatures.member(c,"Member");
        assertFalse(memberProfile.has("rewards"));assertFalse(memberProfile.has("clanEvents"));
        assertEquals("Member",memberProfile.getAsJsonObject("profile").get("name").getAsString());
        rejected(404,()->ClanFeatures.member(c,"Unknown"));
    }
    @Test public void raceFirstsAndStandingsUseServerReceiptAndDoNotAutoPayPrizes()throws Exception {
        UUID race=UUID.randomUUID();RankingStore.execute(c,"INSERT INTO races VALUES (?,'Clan race','Test',now()-interval '1 hour',now()+interval '1 hour','LOOT','1:2',true)",race);
        RankingStore.execute(c,"INSERT INTO race_prizes VALUES (?,1,300)",race);
        RankingStore.execute(c,"INSERT INTO race_firsts VALUES (?,?,'First drop','LOOT','1:2',100)",UUID.randomUUID(),race);
        RankingStore.event(c,member,event("LOOT","1:2"));
        JsonObject data=RankingStore.dashboard(c,member,"Member").getAsJsonArray("races").get(0).getAsJsonObject();
        assertEquals("Member",data.getAsJsonArray("firsts").get(0).getAsJsonObject().get("winner").getAsString());
        assertEquals(300,data.getAsJsonArray("standings").get(0).getAsJsonObject().get("projectedPoints").getAsInt());
        assertEquals(25,RankingStore.dashboard(c,member,"Member").getAsJsonObject("profile").get("points").getAsInt());
    }
    @Test public void additiveSchemaAndStarterGoalsPreserveExistingAwards()throws Exception {
        RankingStore.event(c,member,event("LOOT","1:2"));
        for(String resource:new String[]{"/schema.sql","/starter-goals.sql","/starter-goals.sql"}) {
            try(InputStream input=RankingServer.class.getResourceAsStream(resource);Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        }
        assertEquals(1,count("ledger"));assertEquals(19,count("goals"));
        assertEquals(25,RankingStore.dashboard(c,member,"Member").getAsJsonObject("profile").get("points").getAsInt());
    }
}

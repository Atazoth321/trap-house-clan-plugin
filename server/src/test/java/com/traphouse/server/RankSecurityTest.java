package com.traphouse.server;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankSecurityTest {
    @Test public void ownerAdjustmentsRequireAReasonAndAnExactNonzeroInteger() throws Exception {
        JsonObject body=new JsonObject();body.addProperty("id",UUID.randomUUID().toString());body.addProperty("points",-1);
        for(String reason:new String[]{null,"","   "}) {
            if(reason==null)body.remove("reason");else body.addProperty("reason",reason);
            try {RankSecurity.adjust(null,actor(RankSecurity.Role.OWNER),UUID.randomUUID(),body);fail("Reason required");}
            catch(Policy.Rejected failure){assertEquals(400,failure.status);}
        }
        body.addProperty("reason","Verified correction");
        for(Number delta:new Number[]{-0.5,0,-100001,100001,Long.MIN_VALUE}) {
            body.addProperty("points",delta);
            try {RankSecurity.adjust(null,actor(RankSecurity.Role.OWNER),UUID.randomUUID(),body);fail("Invalid adjustment accepted");}
            catch(Policy.Rejected failure){assertEquals(400,failure.status);}
        }
    }
    @Test public void deductionsAlwaysRequireOwnerAndPositiveAwardsKeepExistingPermissions() {
        for(RankSecurity.Role role:RankSecurity.Role.values()) {
            RankingStore.Actor actor=actor(role);
            if(role==RankSecurity.Role.OWNER)RankSecurity.adjustmentPermission(actor,-1);
            else denied(()->RankSecurity.adjustmentPermission(actor,-1));
            if(role==RankSecurity.Role.OWNER||role==RankSecurity.Role.ADMINISTRATOR)RankSecurity.adjustmentPermission(actor,100);
            else denied(()->RankSecurity.adjustmentPermission(actor,100));
        }
    }
    @Test public void directDeductionApiRouteRejectsEveryNonOwnerBeforeTargetAccess() throws Exception {
        JsonObject body=new JsonObject();body.addProperty("id",UUID.randomUUID().toString());
        body.addProperty("points",-1);body.addProperty("reason","Attempted deduction");
        for(RankSecurity.Role role:RankSecurity.Role.values()) {
            if(role==RankSecurity.Role.OWNER)continue;
            try {RankingServer.route(null,actor(role),"POST","/v1/admin/members/"+UUID.randomUUID()+"/points",null,body);fail("Non-owner deduction accepted");}
            catch(Policy.Rejected failure){assertEquals(403,failure.status);}
        }
    }
    @Test public void onlyOwnerCanDemoteIncludingRemovingAHigherOverride() {
        for(RankSecurity.Role role:RankSecurity.Role.values()) {
            RankingStore.Actor actor=actor(role);
            if(role==RankSecurity.Role.OWNER)RankSecurity.demotionPermission(actor,"Sergeant","Recruit",500,0);
            else denied(()->RankSecurity.demotionPermission(actor,"Sergeant","Recruit",500,0));
            RankSecurity.demotionPermission(actor,"Recruit","Sergeant",0,500);
            RankSecurity.demotionPermission(actor,"Sergeant","Sergeant",500,500);
        }
    }
    private RankingStore.Actor actor(RankSecurity.Role role) { return new RankingStore.Actor(UUID.randomUUID(),"Test",role); }
    private void denied(Runnable action) {
        try { action.run(); fail("Expected forbidden"); } catch(Policy.Rejected rejected) { assertEquals(403,rejected.status); }
    }
    @Test public void onlyOwnerCanChangeProtectedOrLockedRanks() {
        for(RankSecurity.Role role:RankSecurity.Role.values()) {
            RankingStore.Actor actor=actor(role);
            if(role==RankSecurity.Role.OWNER) {
                RankSecurity.rankPermission(actor,true,true);
                RankSecurity.owner(actor);
            } else {
                denied(()->RankSecurity.rankPermission(actor,true,false));
                denied(()->RankSecurity.rankPermission(actor,false,true));
                denied(()->RankSecurity.owner(actor));
            }
        }
    }
    @Test public void ordinaryRanksRequireAdministratorOrOwner() {
        RankSecurity.rankPermission(actor(RankSecurity.Role.OWNER),false,false);
        RankSecurity.rankPermission(actor(RankSecurity.Role.ADMINISTRATOR),false,false);
        denied(()->RankSecurity.rankPermission(actor(RankSecurity.Role.EVENT_MANAGER),false,false));
        denied(()->RankSecurity.rankPermission(actor(RankSecurity.Role.MEMBER),false,false));
    }
    @Test public void overrideLockAndProtectionSuppressPromotionEligibility() {
        assertTrue(RankSecurity.eligible("Corporal",null,false,false,100,-1));
        assertTrue(RankSecurity.eligible("Sergeant","Corporal",false,false,500,100));
        assertFalse(RankSecurity.eligible("Corporal","Sergeant",false,false,100,500));
        assertFalse(RankSecurity.eligible("Sergeant","Sergeant",false,false,500,500));
        assertFalse(RankSecurity.eligible("Sergeant","Owner",false,true,500,-1));
        assertFalse(RankSecurity.eligible("Sergeant","Recruit",true,false,500,0));
        assertFalse(RankSecurity.eligible("Unranked",null,false,false,-1,-1));
    }
    @Test public void routesRejectUnprivilegedCallersBeforeDatabaseAccess() throws Exception {
        String member=UUID.randomUUID().toString();JsonObject body=new JsonObject();
        for(RankSecurity.Role role:new RankSecurity.Role[]{RankSecurity.Role.MEMBER,RankSecurity.Role.EVENT_MANAGER}) {
            for(String endpoint:new String[]{"rank","points","permission"}) {
                try { RankingServer.route(null,actor(role),"POST","/v1/admin/members/"+member+"/"+endpoint,null,body);fail("Expected forbidden"); }
                catch(Policy.Rejected failure){assertEquals(403,failure.status);}
            }
        }
        try { RankingServer.route(null,actor(RankSecurity.Role.MEMBER),"POST","/v1/admin/clan-events/"+member+"/publish",null,body);fail("Expected forbidden"); }
        catch(Policy.Rejected failure){assertEquals(403,failure.status);}
        for(RankSecurity.Role role:new RankSecurity.Role[]{RankSecurity.Role.MEMBER,RankSecurity.Role.EVENT_MANAGER}) {
            try { RankingServer.route(null,actor(role),"GET","/v1/admin/audit",null,body);fail("Expected forbidden"); }
            catch(Policy.Rejected failure){assertEquals(403,failure.status);}
        }
    }
}

package com.traphouse.server;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

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

    @Test public void operatorAuditSuppliesRequiredActorAndMarksItsMeaning()throws Exception {
        UUID member=UUID.randomUUID();
        for(String action:new String[]{"MEMBER_PROVISIONED","DISCORD_IDENTITY_LINKED"}) {
            Map<Integer,Object> parameters=new HashMap<>();
            boolean[] written={false};
            Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(!method.getName().equals("prepareStatement"))throw new AssertionError(method.getName());
                assertEquals("INSERT INTO audit(actor,action,target,details) VALUES (?,?,?,?::jsonb)",args[0]);
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(statement,operation,values)->{
                    switch(operation.getName()) {
                        case "setObject":parameters.put((Integer)values[0],values[1]);return null;
                        case "close":return null;
                        case "executeUpdate":
                            if(parameters.get(1)==null)throw new SQLException("audit.actor NOT NULL");
                            assertEquals(member,parameters.get(1));assertEquals(member,parameters.get(3));
                            assertEquals(action,parameters.get(2));written[0]=true;return 1;
                        default:throw new AssertionError(operation.getName());
                    }
                });
            });
            JsonObject details=new JsonObject();details.addProperty("source","server operator CLI");
            RankingServer.operatorAudit(connection,member,action,details);
            assertTrue(written[0]);assertEquals("SERVER_OPERATOR",details.get("actorKind").getAsString());
            assertTrue(details.get("actorReference").getAsString().contains("no authenticated API actor"));
        }
    }
}

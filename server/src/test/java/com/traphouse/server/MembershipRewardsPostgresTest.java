package com.traphouse.server;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.*;
import static org.junit.Assert.*;

public class MembershipRewardsPostgresTest {
    private Connection c;private String schema;
    private final UUID member=UUID.randomUUID();
    @Before public void setup()throws Exception {
        c=DriverManager.getConnection(System.getenv("TRAP_TEST_DATABASE_URL"),System.getenv("TRAP_TEST_DATABASE_USER"),System.getenv("TRAP_TEST_DATABASE_PASSWORD"));
        schema="membership_test_"+UUID.randomUUID().toString().replace("-","");
        RankingStore.execute(c,"CREATE SCHEMA "+schema);RankingStore.execute(c,"SET search_path TO "+schema);
        script("/schema.sql");
        RankingStore.execute(c,"INSERT INTO members VALUES (?,'Member','member',true)",member);
        script("/weekly-membership.sql");c.setAutoCommit(false);
    }
    private void script(String name)throws Exception {
        try(java.io.InputStream input=getClass().getResourceAsStream(name);Statement s=c.createStatement()) {
            s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private Instant start()throws Exception {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT started_at FROM membership_reward_state")){r.next();return r.getTimestamp(1).toInstant();}
    }
    private long points()throws Exception {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT coalesce(sum(points),0) FROM ledger")){r.next();return r.getLong(1);}
    }
    @After public void cleanup()throws Exception {
        if(c!=null)try{c.rollback();c.setAutoCommit(true);RankingStore.execute(c,"DROP SCHEMA "+schema+" CASCADE");}finally{c.close();}
    }
    @Test public void fullWeeksRetriesInactivityAndMigration()throws Exception {
        Instant start=start();MembershipRewards.award(c,member,start.plusSeconds(604799));assertEquals(0,points());
        MembershipRewards.award(c,member,start.plusSeconds(604800));assertEquals(25,points());
        c.rollback();assertEquals(0,points());
        MembershipRewards.award(c,member,start.plusSeconds(604800));c.commit();
        script("/weekly-membership.sql");assertEquals(start,start());
        MembershipRewards.award(c,member,start.plusSeconds(604800));assertEquals(25,points());
        RankingStore.execute(c,"UPDATE members SET active=false WHERE id=?",member);
        assertEquals(start,start());
        MembershipRewards.award(c,member,start.plusSeconds(1209600));assertEquals(50,points());
        RankingStore.execute(c,"UPDATE members SET active=true WHERE id=?",member);
        assertEquals(start,start());
        MembershipRewards.award(c,member,start.plusSeconds(1814400));assertEquals(75,points());
    }
    @Test public void newInactiveMembersStartFresh()throws Exception {
        UUID added=UUID.randomUUID();
        RankingStore.execute(c,"INSERT INTO members VALUES (?,'New','new',false)",added);
        MembershipRewards.award(c,added,Instant.now());assertEquals(0,points());
        Instant baseline;
        try(PreparedStatement s=RankingStore.query(c,"SELECT started_at FROM membership_reward_state WHERE member_id=?",added);ResultSet r=s.executeQuery()){assertTrue(r.next());baseline=r.getTimestamp(1).toInstant();}
        MembershipRewards.award(c,added,baseline.plusSeconds(604800));assertEquals(25,points());
        MembershipRewards.award(c,added,baseline.plusSeconds(604800));assertEquals(25,points());
    }
}

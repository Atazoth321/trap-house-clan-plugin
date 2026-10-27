package com.traphouse.server;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class DatabaseInitializationTest {
    private Connection connection(AtomicInteger commits,AtomicInteger rollbacks,AtomicInteger closes) {
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "setAutoCommit":return null;
                case "commit":commits.incrementAndGet();return null;
                case "rollback":rollbacks.incrementAndGet();return null;
                case "close":closes.incrementAndGet();return null;
                case "prepareStatement":return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                    if(m.getName().equals("executeUpdate"))return 0;
                    if(m.getName().equals("close"))return null;
                    throw new AssertionError(m.getName());
                });
                default:throw new AssertionError(method.getName());
            }
        });
    }
    @Test public void retriesOnlyTemporaryConflictsAndRollsBackBeforeReopening()throws Exception {
        AtomicInteger opens=new AtomicInteger(),commits=new AtomicInteger(),rollbacks=new AtomicInteger(),closes=new AtomicInteger();
        DatabaseInitialization.run(()->{opens.incrementAndGet();return connection(commits,rollbacks,closes);},c->{
            if(opens.get()<3)throw new SQLException("Temporary conflict",opens.get()==1?"40P01":"55P03");
        });
        assertEquals(3,opens.get());assertEquals(1,commits.get());assertEquals(2,rollbacks.get());assertEquals(3,closes.get());
        assertFalse(DatabaseInitialization.retryable(new SQLException("Invalid schema","42601")));
        assertFalse(DatabaseInitialization.retryable(new SQLException("Connection failed","08006")));
        assertTrue(DatabaseInitialization.retryable(new SQLException("Serialization conflict","40001")));
    }
    @Test public void permanentErrorsFailImmediatelyAndPersistentConflictsStopAfterFiveAttempts()throws Exception {
        for(String state:new String[]{"42601","40P01"}) {
            AtomicInteger opens=new AtomicInteger(),commits=new AtomicInteger(),rollbacks=new AtomicInteger(),closes=new AtomicInteger();
            try{DatabaseInitialization.run(()->{opens.incrementAndGet();return connection(commits,rollbacks,closes);},c->{throw new SQLException("Test failure",state);});fail("Expected failure");}
            catch(SQLException expected){assertEquals(state,expected.getSQLState());}
            assertEquals(state.equals("40P01")?5:1,opens.get());assertEquals(opens.get(),rollbacks.get());assertEquals(opens.get(),closes.get());assertEquals(0,commits.get());
        }
    }
}

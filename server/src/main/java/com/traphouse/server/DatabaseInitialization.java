package com.traphouse.server;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/** Retry the complete additive initialization transaction when live traffic conflicts with DDL. */
final class DatabaseInitialization {
    interface Migration {void apply(Connection c)throws Exception;}
    static void run(DiscordWorker.Connections connections)throws Exception {run(connections,DatabaseInitialization::apply);}
    static void run(DiscordWorker.Connections connections,Migration migration)throws Exception {
        for(int attempt=1;;attempt++) {
            try(Connection c=connections.open()) {
                c.setAutoCommit(false);
                try {
                    RankingStore.execute(c,"SET LOCAL lock_timeout='5s'");
                    migration.apply(c);c.commit();return;
                }catch(Exception failure) {
                    try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}
                    throw failure;
                }
            }catch(SQLException conflict) {
                if(attempt>=5||!retryable(conflict))throw conflict;
                System.err.println("Database initialization encountered temporary lock contention (SQLSTATE "+conflict.getSQLState()+"); retrying transaction "+(attempt+1)+"/5.");
                Thread.sleep(250L<<(attempt-1));
            }
        }
    }
    static boolean retryable(SQLException error){return "40P01".equals(error.getSQLState())||"55P03".equals(error.getSQLState())||"40001".equals(error.getSQLState());}
    private static void script(Connection c,String resource)throws Exception {
        try(InputStream input=RankingServer.class.getResourceAsStream(resource);Statement s=c.createStatement()) {
            if(input==null)throw new IllegalStateException("Initialization resource missing: "+resource);
            s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    static void apply(Connection c)throws Exception {
        script(c,"/schema.sql");script(c,"/starter-goals.sql");script(c,"/rank-thresholds-v1.sql");
        MilestoneCatalog.seed(c);MilestoneCatalog.applyRewards(c);MilestoneCatalog.applyRewardBoost(c);MilestoneCatalog.applyCurrentRewards(c);
        script(c,"/activity-rewards-v3.sql");AutomaticRewards.seed(c);BalancedRewards.apply(c);RankManagement.initializeOwnerRanks(c);script(c,"/discord-joining.sql");
        script(c,"/achievement-diaries.sql");
    }
}

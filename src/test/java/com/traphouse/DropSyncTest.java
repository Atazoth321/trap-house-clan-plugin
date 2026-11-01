package com.traphouse;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropSyncTest {
    @Test public void aBurstSchedulesOneSyncAndWorkDuringSyncGetsAFollowup() throws Exception {
        ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),followup=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        DropSync sync=new DropSync(executor,()->{
            if(calls.incrementAndGet()==1){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            else followup.countDown();
        });
        try {
            for(int i=0;i<20;i++)sync.request();
            assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals(1,calls.get());
            for(int i=0;i<20;i++)sync.request();
            release.countDown();assertTrue(followup.await(3,TimeUnit.SECONDS));
            sync.close();executor.shutdown();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));assertEquals(2,calls.get());
        } finally{release.countDown();sync.close();executor.shutdownNow();}
    }
    @Test public void closingCancelsPendingRequests() throws Exception {
        ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();AtomicInteger calls=new AtomicInteger();
        DropSync sync=new DropSync(executor,calls::incrementAndGet);
        try {
            sync.request();sync.close();sync.request();executor.shutdown();
            assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));assertEquals(0,calls.get());
        } finally{executor.shutdownNow();}
    }
}

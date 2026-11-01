package com.traphouse;

import java.util.concurrent.*;

/** Coalesces loot bursts and preserves requests arriving during an active sync. */
final class DropSync {
    private final ScheduledExecutorService executor;
    private final Runnable sync;
    private boolean scheduled, dirty, closed;
    private ScheduledFuture<?> pending;
    DropSync(ScheduledExecutorService executor,Runnable sync){this.executor=executor;this.sync=sync;}
    synchronized void request() {
        if(closed)return;
        dirty=true;
        if(!scheduled){scheduled=true;schedule();}
    }
    private void schedule(){pending=executor.schedule(this::run,150,TimeUnit.MILLISECONDS);}
    private void run() {
        synchronized(this){if(closed)return;dirty=false;}
        try{sync.run();}
        finally{synchronized(this){if(!closed&&dirty)schedule();else scheduled=false;}}
    }
    synchronized void close(){closed=true;dirty=false;if(pending!=null)pending.cancel(false);}
}

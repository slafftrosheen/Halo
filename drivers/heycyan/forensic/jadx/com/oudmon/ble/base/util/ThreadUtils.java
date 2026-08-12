/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.util;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ThreadUtils {
    private static final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private static final HashMap<TimeTask, ScheduledFuture> mRunnableCache = new HashMap();
    private static final Object mLock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void postDelay(TimeTask runnable, long delay) {
        Object object = mLock;
        synchronized (object) {
            ThreadUtils.cancel(runnable);
            ScheduledFuture<?> future = mExecutor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
            mRunnableCache.put(runnable, future);
        }
    }

    public static void cancel(TimeTask runnable) {
        ThreadUtils.cancel(runnable, false);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void cancel(TimeTask runnable, boolean mayInterruptIfRunning) {
        Object object = mLock;
        synchronized (object) {
            ScheduledFuture future = mRunnableCache.get(runnable);
            if (future != null) {
                future.cancel(mayInterruptIfRunning);
                mRunnableCache.remove(runnable);
            }
        }
    }

    public static abstract class TimeTask
    implements Runnable {
        @Override
        public void run() {
            this.task();
            mRunnableCache.remove(this);
        }

        protected abstract void task();
    }
}


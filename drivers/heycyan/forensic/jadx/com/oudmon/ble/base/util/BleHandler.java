/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.os.Handler
 *  android.os.HandlerThread
 *  android.os.Looper
 */
package com.oudmon.ble.base.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public class BleHandler
extends Handler {
    private static final String TAG = "BleHandler";
    private static BleHandler sHandler;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static BleHandler of() {
        Class<BleHandler> clazz = BleHandler.class;
        synchronized (BleHandler.class) {
            if (sHandler == null) {
                HandlerThread handlerThread = new HandlerThread("handler thread");
                handlerThread.start();
                sHandler = new BleHandler(handlerThread.getLooper());
            }
            // ** MonitorExit[var0] (shouldn't be in output)
            return sHandler;
        }
    }

    private BleHandler(Looper looper) {
        super(Looper.myLooper());
    }
}


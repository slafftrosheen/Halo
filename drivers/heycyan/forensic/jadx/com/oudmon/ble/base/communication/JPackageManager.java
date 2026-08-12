/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication;

public class JPackageManager {
    private int mLength = 244;
    private static JPackageManager mInstance;

    private JPackageManager() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static JPackageManager getInstance() {
        if (mInstance != null) return mInstance;
        Class<JPackageManager> clazz = JPackageManager.class;
        synchronized (JPackageManager.class) {
            if (mInstance != null) return mInstance;
            mInstance = new JPackageManager();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    public void setLength(int length) {
        this.mLength = length;
    }

    public int getLength() {
        return Math.max(this.mLength, 244);
    }
}


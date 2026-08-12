/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.bluetooth;

public class SDKInit {
    private static SDKInit sdkInit = null;
    public static int SDK_TYPE_QC = 1;
    public static int SDK_TYPE_MY = 2;
    private int currSDK = 1;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static SDKInit getInstance() {
        if (sdkInit != null) return sdkInit;
        Class<SDKInit> clazz = SDKInit.class;
        synchronized (SDKInit.class) {
            if (sdkInit != null) return sdkInit;
            sdkInit = new SDKInit();
            // ** MonitorExit[var0] (shouldn't be in output)
            return sdkInit;
        }
    }

    public int getSdkType() {
        return this.currSDK;
    }

    public void setSDKType(int type) {
        this.currSDK = type;
    }
}


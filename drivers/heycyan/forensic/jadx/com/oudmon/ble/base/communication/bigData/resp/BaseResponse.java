/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

public abstract class BaseResponse {
    public static final String TAG = "GLASSES_LOG";
    protected int cmdType;

    public abstract boolean acceptData(byte[] var1);

    public int getCmdType() {
        return this.cmdType;
    }

    public void setCmdType(int cmdType) {
        this.cmdType = cmdType;
    }
}


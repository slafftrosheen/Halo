/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

public abstract class BaseRspCmd {
    protected static final String TAG = "Jxr35";
    public static final int RESULT_OK = 0;
    protected int status;
    protected int cmdType;

    public abstract boolean acceptData(byte[] var1);

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getCmdType() {
        return this.cmdType;
    }

    public void setCmdType(int cmdType) {
        this.cmdType = cmdType;
    }
}


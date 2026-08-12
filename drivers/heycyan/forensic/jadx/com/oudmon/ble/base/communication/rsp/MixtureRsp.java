/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public abstract class MixtureRsp
extends BaseRspCmd {
    public static final byte ACTION_READ = 1;
    public static final byte ACTION_WRITE = 2;
    public static final byte ACTION_DELETE = 3;
    private byte action;

    @Override
    public boolean acceptData(byte[] data) {
        this.action = data[0];
        if (this.action == 1) {
            this.readSubData(data);
        }
        return false;
    }

    protected abstract void readSubData(byte[] var1);

    public byte getAction() {
        return this.action;
    }
}


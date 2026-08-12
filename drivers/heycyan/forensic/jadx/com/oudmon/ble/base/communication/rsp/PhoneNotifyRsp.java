/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class PhoneNotifyRsp
extends BaseRspCmd {
    private int action = 0;

    @Override
    public boolean acceptData(byte[] data) {
        this.action = data[0] & 0xFF;
        return false;
    }

    public int getAction() {
        return this.action;
    }

    public boolean isReject() {
        return this.action == 1;
    }
}


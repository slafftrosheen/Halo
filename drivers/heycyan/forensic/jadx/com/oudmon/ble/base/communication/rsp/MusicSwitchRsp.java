/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class MusicSwitchRsp
extends BaseRspCmd {
    private int action;
    private boolean enable;

    @Override
    public boolean acceptData(byte[] data) {
        this.action = data[0];
        this.enable = data[1] == 1;
        return false;
    }

    public boolean isEnable() {
        return this.enable;
    }

    public int getAction() {
        return this.action;
    }
}


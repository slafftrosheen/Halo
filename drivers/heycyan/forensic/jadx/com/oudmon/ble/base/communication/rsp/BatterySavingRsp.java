/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class BatterySavingRsp
extends BaseRspCmd {
    private boolean open;

    @Override
    public boolean acceptData(byte[] data) {
        this.open = data[1] == 1;
        return false;
    }

    public boolean isOpen() {
        return this.open;
    }
}


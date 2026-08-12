/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class SimpleStatusRsp
extends BaseRspCmd {
    @Override
    public boolean acceptData(byte[] data) {
        return false;
    }
}


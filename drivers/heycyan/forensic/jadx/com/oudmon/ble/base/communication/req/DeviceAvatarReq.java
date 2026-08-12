/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class DeviceAvatarReq
extends BaseReqCmd {
    public DeviceAvatarReq() {
        super((byte)50);
    }

    @Override
    protected byte[] getSubData() {
        return new byte[0];
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class SetMessagePushReq
extends BaseReqCmd {
    public SetMessagePushReq() {
        super((byte)97);
    }

    @Override
    protected byte[] getSubData() {
        return null;
    }
}


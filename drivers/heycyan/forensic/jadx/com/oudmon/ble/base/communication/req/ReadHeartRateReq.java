/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.ble.base.communication.utils.DataParseUtils;

public class ReadHeartRateReq
extends BaseReqCmd {
    private byte[] data;

    public ReadHeartRateReq(long time) {
        super((byte)21);
        this.data = DataParseUtils.intToByteArray((int)time);
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


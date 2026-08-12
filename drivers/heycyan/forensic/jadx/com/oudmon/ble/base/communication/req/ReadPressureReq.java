/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.ble.base.communication.utils.DataParseUtils;

public class ReadPressureReq
extends BaseReqCmd {
    private byte[] data;

    public ReadPressureReq(long time) {
        super((byte)20);
        byte[] timeArray = DataParseUtils.intToByteArray((int)time);
        this.data = new byte[timeArray.length + 2];
        System.arraycopy(timeArray, 0, this.data, 0, timeArray.length);
        this.data[4] = 0;
        this.data[5] = 50;
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


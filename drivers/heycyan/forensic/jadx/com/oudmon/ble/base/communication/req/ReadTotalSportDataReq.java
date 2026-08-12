/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class ReadTotalSportDataReq
extends BaseReqCmd {
    private int theDayOffset = 0;

    public ReadTotalSportDataReq(int theDayOffset) {
        super((byte)7);
        this.theDayOffset = theDayOffset;
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{(byte)(this.theDayOffset & 0xFF)};
    }
}


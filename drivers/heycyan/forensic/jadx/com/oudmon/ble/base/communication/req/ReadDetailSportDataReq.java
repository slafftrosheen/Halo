/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class ReadDetailSportDataReq
extends BaseReqCmd {
    private byte[] data;

    public ReadDetailSportDataReq(int dayOffset, int startIndex, int endIndex) {
        super((byte)67);
        if (dayOffset > 29) {
            throw new IllegalArgumentException("dayOffset \u6700\u5927\u53ea\u523029");
        }
        if (startIndex > endIndex || endIndex > 95) {
            throw new IllegalArgumentException("\u6570\u636e\u6bb5\u7d22\u5f15\u503c\u5f02\u5e38");
        }
        this.data = new byte[]{(byte)dayOffset, 15, (byte)startIndex, (byte)endIndex, 1};
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


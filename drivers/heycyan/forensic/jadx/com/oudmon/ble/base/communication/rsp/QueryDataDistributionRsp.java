/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class QueryDataDistributionRsp
extends BaseRspCmd {
    private int distribution;

    @Override
    public boolean acceptData(byte[] data) {
        this.distribution = BLEDataFormatUtils.bytes2Int(new byte[]{data[0], data[1], data[2], data[3]});
        return false;
    }

    public boolean isTheDayHasData(int dayOffset) {
        int tmp = this.distribution >> dayOffset;
        return (tmp & 1) != 0;
    }
}


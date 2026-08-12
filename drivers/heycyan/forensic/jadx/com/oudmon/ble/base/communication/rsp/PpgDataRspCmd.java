/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class PpgDataRspCmd
extends BaseRspCmd {
    public int mRate;
    public int mPpgValue;

    @Override
    public boolean acceptData(byte[] data) {
        this.mRate = data[0];
        this.mPpgValue = DataTransferUtils.bytesToInt(data, 1);
        return false;
    }
}


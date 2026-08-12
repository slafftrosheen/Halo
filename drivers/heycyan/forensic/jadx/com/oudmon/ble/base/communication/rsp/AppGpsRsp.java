/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class AppGpsRsp
extends BaseRspCmd {
    private int gpsStatus;
    private int timeStamp = 0;

    @Override
    public boolean acceptData(byte[] data) {
        this.gpsStatus = data[0];
        if (this.gpsStatus == 6) {
            this.timeStamp = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 2, 6));
        }
        return false;
    }

    public int getTimeStamp() {
        return this.timeStamp;
    }

    public int getGpsStatus() {
        return this.gpsStatus;
    }
}


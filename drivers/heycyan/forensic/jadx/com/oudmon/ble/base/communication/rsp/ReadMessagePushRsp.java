/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class ReadMessagePushRsp
extends BaseRspCmd {
    private int deviceSupport1 = 0;
    private int deviceSupport2 = 0;
    private int deviceSupport3 = 0;

    @Override
    public boolean acceptData(byte[] data) {
        this.deviceSupport1 = DataTransferUtils.bytesToInt(data, 2);
        this.deviceSupport2 = DataTransferUtils.bytesToInt(data, 4);
        this.deviceSupport3 = DataTransferUtils.bytesToInt(data, 6);
        return false;
    }

    public int getDeviceSupport1() {
        return this.deviceSupport1;
    }

    public int getDeviceSupport2() {
        return this.deviceSupport2;
    }

    public int getDeviceSupport3() {
        return this.deviceSupport3;
    }
}


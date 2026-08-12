/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class StopHeartRateRsp
extends BaseRspCmd {
    private byte type;
    private byte errCode;
    private byte value;
    private byte sbp;
    private byte dbp;

    @Override
    public boolean acceptData(byte[] data) {
        this.type = data[0];
        this.errCode = data[1];
        this.value = data[2];
        if (data.length >= 5) {
            this.sbp = data[3];
            this.dbp = data[4];
        }
        return false;
    }

    public byte getType() {
        return this.type;
    }

    public byte getErrCode() {
        return this.errCode;
    }

    public byte getValue() {
        return this.value;
    }

    public byte getSbp() {
        return this.sbp;
    }

    public byte getDbp() {
        return this.dbp;
    }
}


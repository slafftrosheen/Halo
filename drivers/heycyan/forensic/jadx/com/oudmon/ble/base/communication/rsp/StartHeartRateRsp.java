/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class StartHeartRateRsp
extends BaseRspCmd {
    private byte type;
    private byte errCode;
    private int value;
    private int sbp;
    private int dbp;

    @Override
    public boolean acceptData(byte[] data) {
        this.type = data[0];
        this.errCode = data[1];
        this.value = data[2] & 0xFF;
        if (data.length >= 5) {
            this.sbp = data[3];
            if (this.sbp < 0) {
                this.sbp = data[3] & 0xFF;
            }
            this.dbp = data[4];
            if (this.dbp < 0) {
                this.dbp = data[4] & 0xFF;
            }
        }
        return false;
    }

    public byte getType() {
        return this.type;
    }

    public byte getErrCode() {
        return this.errCode;
    }

    public int getValue() {
        return this.value;
    }

    public int getSbp() {
        return Math.abs(this.sbp);
    }

    public int getDbp() {
        return Math.abs(this.dbp);
    }
}


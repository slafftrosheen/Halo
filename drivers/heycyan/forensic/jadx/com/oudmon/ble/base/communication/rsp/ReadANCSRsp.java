/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class ReadANCSRsp
extends BaseRspCmd {
    private short stateMask;

    @Override
    public boolean acceptData(byte[] data) {
        this.stateMask = DataTransferUtils.bytesToShort(data, 0);
        return false;
    }

    public short getStateMask() {
        return this.stateMask;
    }

    public void setStateMask(short stateMask) {
        this.stateMask = stateMask;
    }
}


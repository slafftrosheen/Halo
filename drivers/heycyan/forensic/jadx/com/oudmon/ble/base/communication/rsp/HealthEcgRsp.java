/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class HealthEcgRsp
extends BaseRspCmd {
    public int mStatus;
    public int mEcgInterval;
    public int mPpgInterval;

    @Override
    public boolean acceptData(byte[] data) {
        if (data.length >= 3) {
            this.mStatus = data[0];
            this.mEcgInterval = data[1];
            this.mPpgInterval = data[2];
        }
        return false;
    }
}


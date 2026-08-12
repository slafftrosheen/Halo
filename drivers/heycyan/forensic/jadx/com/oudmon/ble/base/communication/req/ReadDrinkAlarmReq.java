/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class ReadDrinkAlarmReq
extends BaseReqCmd {
    private int alarmIndex;

    public ReadDrinkAlarmReq(int alarmIndex) {
        super((byte)40);
        if (alarmIndex > 7) {
            throw new IllegalArgumentException("\u95f9\u949f\u7d22\u5f15\u53ea\u80fd0 \u5230 7");
        }
        this.alarmIndex = alarmIndex;
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{(byte)this.alarmIndex};
    }
}


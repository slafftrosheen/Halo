/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class ReadAlarmReq
extends BaseReqCmd {
    private int alarmIndex;

    public ReadAlarmReq(int alarmIndex) {
        super((byte)36);
        if (alarmIndex > 4) {
            throw new IllegalArgumentException("\u95f9\u949f\u7d22\u5f15\u53ea\u80fd0 \u5230 4");
        }
        this.alarmIndex = alarmIndex;
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{(byte)this.alarmIndex};
    }
}


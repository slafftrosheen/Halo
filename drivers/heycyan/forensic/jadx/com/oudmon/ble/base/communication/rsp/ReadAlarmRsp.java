/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.entity.AlarmEntity;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class ReadAlarmRsp
extends BaseRspCmd {
    private AlarmEntity alarmEntity;

    @Override
    public boolean acceptData(byte[] data) {
        byte weekMask = 0;
        for (int i = 0; i < 7; ++i) {
            weekMask = (byte)(weekMask | data[4 + i] << i);
        }
        this.alarmEntity = new AlarmEntity(data[0], data[1], BLEDataFormatUtils.BCDToDecimal(data[2]), BLEDataFormatUtils.BCDToDecimal(data[3]), weekMask);
        return false;
    }

    public AlarmEntity getAlarmEntity() {
        return this.alarmEntity;
    }
}


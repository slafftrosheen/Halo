/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.entity.AlarmEntity;
import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class SetAlarmReq
extends BaseReqCmd {
    private byte[] data;

    public SetAlarmReq(AlarmEntity alarmEntity) {
        super((byte)35);
        if (alarmEntity.getAlarmIndex() > 4) {
            throw new IllegalArgumentException("\u95f9\u949f\u7d22\u5f15\u53ea\u80fd0 \u5230 4");
        }
        if (alarmEntity.getEnable() > 2) {
            throw new IllegalArgumentException("\u95f9\u949f\u4f7f\u80fd\u8bbe\u7f6e\u53ea\u80fd0 \u5230 2");
        }
        byte weekMask = alarmEntity.getWeekMask();
        this.data = new byte[]{(byte)alarmEntity.getAlarmIndex(), (byte)alarmEntity.getEnable(), BLEDataFormatUtils.decimalToBCD(alarmEntity.getHour()), BLEDataFormatUtils.decimalToBCD(alarmEntity.getMinute()), (byte)(weekMask >> 0 & 1), (byte)(weekMask >> 1 & 1), (byte)(weekMask >> 2 & 1), (byte)(weekMask >> 3 & 1), (byte)(weekMask >> 4 & 1), (byte)(weekMask >> 5 & 1), (byte)(weekMask >> 6 & 1)};
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.entity.StartEndTimeEntity;
import com.oudmon.ble.base.communication.req.MixtureReq;

public class BpSettingReq
extends MixtureReq {
    private BpSettingReq() {
        super((byte)12);
        this.subData = new byte[]{1};
    }

    private BpSettingReq(boolean isEnable, StartEndTimeEntity startEndTimeEntity, int multiple) {
        super((byte)12);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 0), (byte)(startEndTimeEntity.getStartHour() & 0xFF), (byte)(startEndTimeEntity.getStartMinute() & 0xFF), (byte)(startEndTimeEntity.getEndHour() & 0xFF), (byte)(startEndTimeEntity.getEndMinute() & 0xFF), (byte)(multiple & 0xFF)};
    }

    public static BpSettingReq getReadInstance() {
        return new BpSettingReq();
    }

    public static BpSettingReq getWriteInstance(boolean isEnable, StartEndTimeEntity startEndTimeEntity, int multiple) {
        return new BpSettingReq(isEnable, startEndTimeEntity, multiple);
    }
}


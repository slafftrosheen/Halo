/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.entity.StartEndTimeEntity;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class ReadSitLongRsp
extends BaseRspCmd {
    private StartEndTimeEntity startEndTimeEntity;
    private byte weekMask;
    private int cycle;

    @Override
    public boolean acceptData(byte[] data) {
        this.startEndTimeEntity = new StartEndTimeEntity(BLEDataFormatUtils.BCDToDecimal(data[0]), BLEDataFormatUtils.BCDToDecimal(data[1]), BLEDataFormatUtils.BCDToDecimal(data[2]), BLEDataFormatUtils.BCDToDecimal(data[3]));
        this.weekMask = data[4];
        this.cycle = data[5];
        return false;
    }

    public StartEndTimeEntity getStartEndTimeEntity() {
        return this.startEndTimeEntity;
    }

    public byte getWeekMask() {
        return this.weekMask;
    }

    public int getCycle() {
        return this.cycle;
    }

    public boolean isEnable() {
        return this.weekMask != 0;
    }

    public void setEnableAll(boolean isEnable) {
        this.weekMask = (byte)(isEnable ? 127 : 0);
    }

    public ReadSitLongRsp cloneMySelf() {
        ReadSitLongRsp copy = new ReadSitLongRsp();
        copy.weekMask = this.weekMask;
        copy.cycle = this.cycle;
        copy.startEndTimeEntity = new StartEndTimeEntity(this.startEndTimeEntity.getStartHour(), this.startEndTimeEntity.getStartMinute(), this.startEndTimeEntity.getEndHour(), this.startEndTimeEntity.getEndMinute());
        return copy;
    }

    public void enableTheWeek(int index, boolean isEnable) {
        this.weekMask = (byte)(~(1 << index) & this.weekMask);
        if (isEnable) {
            this.weekMask = (byte)(1 << index | this.weekMask);
        }
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.entity.BleStepTotal;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;
import java.util.Calendar;

public class TodaySportDataRsp
extends BaseRspCmd {
    private BleStepTotal sportTotal;

    @Override
    public boolean acceptData(byte[] data) {
        this.sportTotal = new BleStepTotal();
        this.sportTotal.setTotalSteps(BLEDataFormatUtils.bytes2Int(new byte[]{data[0], data[1], data[2]}));
        this.sportTotal.setRunningSteps(BLEDataFormatUtils.bytes2Int(new byte[]{data[3], data[4], data[5]}));
        this.sportTotal.setCalorie(BLEDataFormatUtils.bytes2Int(new byte[]{data[6], data[7], data[8]}));
        this.sportTotal.setWalkDistance(BLEDataFormatUtils.bytes2Int(new byte[]{data[9], data[10], data[11]}));
        this.sportTotal.setSportDuration(BLEDataFormatUtils.bytes2Int(new byte[]{data[12], data[13]}) * 60);
        Calendar calendar = Calendar.getInstance();
        this.sportTotal.setDaysAgo(0);
        this.sportTotal.setYear(calendar.get(1));
        this.sportTotal.setMonth(calendar.get(2) + 1);
        this.sportTotal.setDay(calendar.get(5));
        return false;
    }

    public BleStepTotal getSportTotal() {
        return this.sportTotal;
    }
}


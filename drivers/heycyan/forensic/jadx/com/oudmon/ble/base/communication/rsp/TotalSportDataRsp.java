/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.entity.BleStepTotal;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class TotalSportDataRsp
extends BaseRspCmd {
    private int pocketCount = 2;
    private int curIndex = 0;
    private BleStepTotal bleStepTotal;

    @Override
    public boolean acceptData(byte[] data) {
        byte pocketIndex = data[0];
        if (pocketIndex != this.curIndex || pocketIndex >= this.pocketCount) {
            Log.e((String)"Jxr35", (String)("acceptData: index \u9519\u8bef need=" + this.curIndex + " received=" + pocketIndex));
            this.bleStepTotal = null;
            return false;
        }
        if (data[2] == 0 && data[3] == 0 && data[4] == 0) {
            Log.d((String)"Jxr35", (String)"\u6ca1\u6709\u5b58\u50a8\u6570\u636e");
            this.bleStepTotal = null;
            return false;
        }
        if (this.curIndex == 0) {
            this.bleStepTotal = new BleStepTotal();
            this.bleStepTotal.setDaysAgo(BLEDataFormatUtils.BCDToDecimal(data[1]));
            this.bleStepTotal.setYear(BLEDataFormatUtils.BCDToDecimal(data[2]) + 2000);
            this.bleStepTotal.setMonth(BLEDataFormatUtils.BCDToDecimal(data[3]));
            this.bleStepTotal.setDay(BLEDataFormatUtils.BCDToDecimal(data[4]));
            this.bleStepTotal.setTotalSteps(BLEDataFormatUtils.bytes2Int(new byte[]{data[5], data[6], data[7]}));
            this.bleStepTotal.setRunningSteps(BLEDataFormatUtils.bytes2Int(new byte[]{data[8], data[9], data[10]}));
            this.bleStepTotal.setCalorie(BLEDataFormatUtils.bytes2Int(new byte[]{data[11], data[12], data[13]}));
        } else if (this.curIndex == 1) {
            int daysAgo = BLEDataFormatUtils.BCDToDecimal(data[1]);
            int year = BLEDataFormatUtils.BCDToDecimal(data[2]) + 2000;
            int month = BLEDataFormatUtils.BCDToDecimal(data[3]);
            int day = BLEDataFormatUtils.BCDToDecimal(data[4]);
            if (this.bleStepTotal != null && this.bleStepTotal.getDaysAgo() == daysAgo && this.bleStepTotal.getYear() == year && this.bleStepTotal.getMonth() == month && this.bleStepTotal.getDay() == day) {
                this.bleStepTotal.setWalkDistance(BLEDataFormatUtils.bytes2Int(new byte[]{data[5], data[6], data[7]}));
                this.bleStepTotal.setSportDuration(BLEDataFormatUtils.bytes2Int(new byte[]{data[8], data[9]}) * 60);
                this.bleStepTotal.setSleepDuration(BLEDataFormatUtils.bytes2Int(new byte[]{data[10], data[11]}) * 60);
            }
        }
        ++this.curIndex;
        return this.curIndex != this.pocketCount;
    }

    public BleStepTotal getBleStepTotal() {
        return this.bleStepTotal;
    }
}


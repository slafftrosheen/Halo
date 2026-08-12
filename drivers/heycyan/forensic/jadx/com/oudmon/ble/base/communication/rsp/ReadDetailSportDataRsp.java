/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.entity.BleStepDetails;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;
import java.util.ArrayList;

public class ReadDetailSportDataRsp
extends BaseRspCmd {
    private ArrayList<BleStepDetails> bleStepDetailses = new ArrayList();
    private int index = 0;
    private boolean calorieNewProtocol = false;

    @Override
    public boolean acceptData(byte[] data) {
        byte flag = data[0];
        if (this.index == 0 && (flag & 0xFF) == 255) {
            this.bleStepDetailses.clear();
            return false;
        }
        if (this.index == 0 && (flag & 0xFF) == 240) {
            if (data[2] == 1) {
                this.calorieNewProtocol = true;
            }
            ++this.index;
            this.bleStepDetailses.clear();
        } else {
            BleStepDetails stepDetail = new BleStepDetails();
            stepDetail.setYear(BLEDataFormatUtils.BCDToDecimal(data[0]) + 2000);
            stepDetail.setMonth(BLEDataFormatUtils.BCDToDecimal(data[1]));
            stepDetail.setDay(BLEDataFormatUtils.BCDToDecimal(data[2]));
            stepDetail.setTimeIndex(data[3]);
            int calorie = BLEDataFormatUtils.bytes2Int(new byte[]{data[7], data[6]});
            if (this.calorieNewProtocol) {
                calorie *= 10;
            }
            stepDetail.setCalorie(calorie);
            stepDetail.setWalkSteps(BLEDataFormatUtils.bytes2Int(new byte[]{data[9], data[8]}));
            int distance = BLEDataFormatUtils.bytes2Int(new byte[]{data[11], data[10]});
            stepDetail.setDistance(distance);
            this.bleStepDetailses.add(stepDetail);
            ++this.index;
            if (data[4] == data[5] - 1) {
                return false;
            }
        }
        return true;
    }

    public ArrayList<BleStepDetails> getBleStepDetailses() {
        return this.bleStepDetailses;
    }
}


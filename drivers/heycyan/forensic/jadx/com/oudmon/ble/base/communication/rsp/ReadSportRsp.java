/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.entity.BleSport;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.DataParseUtils;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class ReadSportRsp
extends BaseRspCmd {
    private int size = 0;
    private int index = 0;
    private byte[] valueData;
    private BleSport sport = new BleSport();
    private boolean endFlag = false;

    @Override
    public boolean acceptData(byte[] data) {
        byte flag = data[0];
        if ((flag & 0xFF) == 255) {
            this.endFlag = true;
            return false;
        }
        if ((flag & 0xFF) == 0) {
            this.endFlag = false;
            this.size = data[1];
            this.valueData = new byte[this.size * 13];
            Log.i((String)"Jxr35", (String)("0x00.. size: " + this.size + ", valueData: " + this.valueData));
        } else {
            for (int i = 1; i < data.length; ++i) {
                this.valueData[this.index + i - 1] = data[i];
            }
            Log.e((String)"Jxr35", (String)("valueData = " + DataTransferUtils.getHexString(this.valueData)));
            Log.i((String)"Jxr35", (String)("0x00.. size: " + this.size + ", valueData: " + this.valueData));
            this.index += 13;
            if (flag == this.size - 1) {
                byte[] sub = new byte[4];
                int rateCount = 0;
                block10: for (int i = 0; i < 7; ++i) {
                    for (int j = 0; j < 4; ++j) {
                        sub[j] = this.valueData[i * 4 + j];
                    }
                    switch (i) {
                        case 0: {
                            this.sport.setStartTime(DataParseUtils.byteArrayToInt(sub));
                            continue block10;
                        }
                        case 1: {
                            this.sport.setDuration(DataParseUtils.byteArrayToInt(sub));
                            continue block10;
                        }
                        case 2: {
                            this.sport.setSportType(DataParseUtils.byteArrayToInt(sub));
                            continue block10;
                        }
                        case 3: {
                            this.sport.setStepCount(DataParseUtils.byteArrayToInt(sub));
                            continue block10;
                        }
                        case 4: {
                            this.sport.setDistance(DataParseUtils.byteArrayToInt(sub));
                            continue block10;
                        }
                        case 5: {
                            this.sport.setCalories(DataParseUtils.byteArrayToInt(sub));
                            continue block10;
                        }
                        case 6: {
                            rateCount = DataParseUtils.byteArrayToInt(sub);
                        }
                    }
                }
                Log.e((String)"Jxr35", (String)("rateCount = " + rateCount));
                int[] rateArray = new int[rateCount];
                int index = 0;
                for (int i = 28; i < rateCount + 28; ++i) {
                    int value;
                    rateArray[index] = value = this.valueData[i] & 0xFF;
                    ++index;
                }
                this.sport.setRateValue(rateArray);
                return false;
            }
        }
        return true;
    }

    public BleSport getBleSport() {
        return this.sport;
    }

    public boolean isEndFlag() {
        return this.endFlag;
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import com.oudmon.qc_utils.date.DateUtil;
import java.util.TimeZone;

public class ReadHeartRateRspBackup
extends BaseRspCmd {
    private int size = 0;
    private int index = 0;
    private int mUtcTime;
    private byte[] mHeartRateArray;
    private boolean endFlag = false;
    private int range = 5;

    @Override
    public boolean acceptData(byte[] data) {
        try {
            byte flag = data[0];
            if ((flag & 0xFF) == 255 || new DateUtil((long)this.getmUtcTime(), true).isToday() && flag == 23) {
                this.endFlag = true;
                return false;
            }
            if ((flag & 0xFF) == 0) {
                this.endFlag = false;
                this.size = data[1];
                this.range = data[2];
                this.mHeartRateArray = new byte[this.size * 13];
                Log.i((String)"Jxr35", (String)("0x00.. size: " + this.size));
            } else if ((flag & 0xFF) == 1) {
                byte[] utcTime = new byte[]{data[1], data[2], data[3], data[4]};
                this.mUtcTime = DataTransferUtils.bytesToInt(utcTime, 0);
                int time = (int)(ReadHeartRateRspBackup.getTimeZone() * 3600.0f);
                this.mUtcTime -= time;
                System.arraycopy(data, 5, this.mHeartRateArray, 0, data.length - 5);
                this.index += data.length - 5;
            } else {
                System.arraycopy(data, 1, this.mHeartRateArray, this.index, data.length - 1);
                this.index += 13;
                if (flag == this.size - 1) {
                    return false;
                }
            }
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isEndFlag() {
        return this.endFlag;
    }

    public byte[] getmHeartRateArray() {
        if (this.mHeartRateArray != null) {
            byte[] array = new byte[288];
            if (this.mHeartRateArray.length > 288) {
                System.arraycopy(this.mHeartRateArray, 0, array, 0, 288);
                return array;
            }
            if (this.mHeartRateArray.length < 288) {
                System.arraycopy(this.mHeartRateArray, 0, array, 0, this.mHeartRateArray.length);
                return array;
            }
            DateUtil dateUtil = new DateUtil((long)this.mUtcTime, true);
            if (dateUtil.isToday()) {
                int min = dateUtil.getTodayMin();
                int size = min / 5;
                for (int i = 0; i < this.mHeartRateArray.length; ++i) {
                    if (i <= size) continue;
                    this.mHeartRateArray[i] = 0;
                }
            }
        }
        return this.mHeartRateArray == null ? new byte[]{} : this.mHeartRateArray;
    }

    public int getmUtcTime() {
        return this.mUtcTime;
    }

    public static float getTimeZone() {
        return (float)TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0f;
    }
}


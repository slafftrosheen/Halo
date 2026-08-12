/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.qc_utils.date.DateUtil;

public class MuslimRsp
extends BaseRspCmd {
    private int size = 0;
    private int index = 0;
    private byte[] pressureArray;
    private boolean endFlag = false;
    private int range = 60;
    private DateUtil today;
    private int offset = -1;

    @Override
    public boolean acceptData(byte[] data) {
        try {
            byte flag = data[0];
            if ((flag & 0xFF) == 255) {
                this.endFlag = true;
                return false;
            }
            if ((flag & 0xFF) == 0) {
                this.endFlag = false;
                this.size = data[1];
                this.range = data[2];
                this.pressureArray = new byte[this.size * 13];
                Log.i((String)"Jxr35", (String)("0x00.. size: " + this.size));
            } else if ((flag & 0xFF) == 1) {
                int day;
                this.offset = day = data[1];
                this.today = new DateUtil();
                this.today.addDay(-day);
                System.arraycopy(data, 2, this.pressureArray, 0, data.length - 2);
                this.index += data.length - 2;
            } else {
                System.arraycopy(data, 1, this.pressureArray, this.index, data.length - 1);
                this.index += 13;
                if (flag == this.size - 1) {
                    this.endFlag = true;
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

    public int getOffset() {
        return this.offset;
    }

    public boolean isEndFlag() {
        return this.endFlag;
    }

    public byte[] getPressureArray() {
        if (this.pressureArray == null) {
            this.pressureArray = new byte[0];
        }
        return this.pressureArray;
    }

    public int getRange() {
        return this.range;
    }

    public DateUtil getToday() {
        return this.today;
    }
}


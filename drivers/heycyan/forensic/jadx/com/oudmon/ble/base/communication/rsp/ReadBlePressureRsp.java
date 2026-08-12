/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.entity.BlePressure;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.DataParseUtils;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class ReadBlePressureRsp
extends BaseRspCmd {
    private int mCount = 0;
    private List<BlePressure> mValueList = new ArrayList<BlePressure>();
    private Calendar mCalendar = Calendar.getInstance();

    @Override
    public boolean acceptData(byte[] data) {
        Log.i((String)"Jxr35", (String)("ReadBlePressureRsp -> acceptData -> data: " + DataTransferUtils.getHexString(data)));
        byte[] time = Arrays.copyOfRange(data, 0, 4);
        if ("ffffffff".equalsIgnoreCase(DataTransferUtils.getHexString(time))) {
            this.mCount = 0;
            return false;
        }
        ++this.mCount;
        long timeStamp = DataParseUtils.byteArrayToInt(time);
        int timeOffset = (int)(ReadBlePressureRsp.getTimeZone() * 3600.0f);
        Log.i((String)"Jxr35", (String)("timeStamp: " + (timeStamp -= (long)timeOffset) + ", timeOffset: " + timeOffset));
        int dbp = data[4] & 0xFF;
        int sbp = data[5] & 0xFF;
        this.mValueList.add(new BlePressure(timeStamp, sbp, dbp));
        if (this.mCount >= 50) {
            this.mCount = 0;
            return false;
        }
        return true;
    }

    public List<BlePressure> getValueList() {
        return this.mValueList;
    }

    public static float getTimeZone() {
        return (float)TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0f;
    }
}


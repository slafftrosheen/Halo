/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.req;

import android.util.Log;
import com.oudmon.ble.base.communication.entity.StartEndTimeEntity;
import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class SetSitLongReq
extends BaseReqCmd {
    private byte[] data;

    public SetSitLongReq(StartEndTimeEntity startEndTimeEntity, byte weekMask, int cycle) {
        super((byte)37);
        if (cycle != 30 && cycle != 60 && cycle != 90) {
            Log.i((String)"Jxr35", (String)("\u65f6\u95f4\u5468\u671f\u53c2\u6570\u9519\u8bef\uff0c\u5df2\u8c03\u6574\u4e3a\u6b63\u5e38\u768460s\uff0c\u539f\u53c2\u6570\u4e3a: " + cycle));
            cycle = 60;
        }
        this.data = new byte[]{BLEDataFormatUtils.decimalToBCD(startEndTimeEntity.getStartHour()), BLEDataFormatUtils.decimalToBCD(startEndTimeEntity.getStartMinute()), BLEDataFormatUtils.decimalToBCD(startEndTimeEntity.getEndHour()), BLEDataFormatUtils.decimalToBCD(startEndTimeEntity.getEndMinute()), weekMask, (byte)cycle};
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


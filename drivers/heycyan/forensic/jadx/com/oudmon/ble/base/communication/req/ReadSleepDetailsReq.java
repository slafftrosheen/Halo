/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.req;

import android.util.Log;
import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class ReadSleepDetailsReq
extends BaseReqCmd {
    private byte[] data;

    public ReadSleepDetailsReq(int dayOffset, int startIndex, int endIndex) {
        super((byte)68);
        if (dayOffset > 29) {
            throw new IllegalArgumentException("dayOffset \u6700\u5927\u53ea\u523029");
        }
        if (startIndex > endIndex || endIndex > 95) {
            throw new IllegalArgumentException("\u6570\u636e\u6bb5\u7d22\u5f15\u503c\u5f02\u5e38");
        }
        Log.i((String)this.getClass().getSimpleName(), (String)("ReadSleepDetailsReq: dayOffset=" + dayOffset));
        this.data = new byte[]{(byte)dayOffset, 15, (byte)startIndex, (byte)endIndex};
        Log.i((String)this.getClass().getSimpleName(), (String)("ReadSleepDetailsReq: data=" + DataTransferUtils.getHexString(this.data)));
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


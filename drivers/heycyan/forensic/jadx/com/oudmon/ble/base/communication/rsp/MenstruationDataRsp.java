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

public class MenstruationDataRsp
extends BaseRspCmd {
    @Override
    public boolean acceptData(byte[] data) {
        Log.i((String)"Jxr35", (String)("acceptData.. data: " + DataTransferUtils.getHexString(data)));
        return true;
    }
}


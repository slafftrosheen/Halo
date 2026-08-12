/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.ByteUtil;

public class AppRevisionResp
extends BaseRspCmd {
    private int dataType;
    private int result;

    @Override
    public boolean acceptData(byte[] data) {
        Log.i((String)"Jxr35", (String)ByteUtil.byteArrayToString(data));
        this.dataType = data[0];
        this.result = data[9];
        return false;
    }

    public int getDataType() {
        return this.dataType;
    }

    public int getResult() {
        return this.result;
    }
}


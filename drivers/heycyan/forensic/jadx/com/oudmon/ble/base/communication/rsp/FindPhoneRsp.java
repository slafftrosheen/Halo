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

public class FindPhoneRsp
extends BaseRspCmd {
    private int openOrClose;

    @Override
    public boolean acceptData(byte[] data) {
        this.openOrClose = data[0];
        Log.i((String)"Jxr35", (String)ByteUtil.byteArrayToString(data));
        return false;
    }

    public int getSwitchStatue() {
        return this.openOrClose;
    }
}


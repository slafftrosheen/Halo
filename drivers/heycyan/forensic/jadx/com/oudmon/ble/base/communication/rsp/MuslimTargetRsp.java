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

public class MuslimTargetRsp
extends BaseRspCmd {
    private int muslimTarget = 0;

    @Override
    public boolean acceptData(byte[] data) {
        Log.i((String)"Jxr35", (String)ByteUtil.byteArrayToString(data));
        try {
            this.muslimTarget = ByteUtil.bytesToInt(new byte[]{data[2], data[3], data[4], data[5]});
            return false;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getMuslimTarget() {
        return this.muslimTarget;
    }
}


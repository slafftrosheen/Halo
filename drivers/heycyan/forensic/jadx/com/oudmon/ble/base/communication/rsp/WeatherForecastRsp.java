/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.rsp.MixtureRsp;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class WeatherForecastRsp
extends MixtureRsp {
    private boolean isSuccess = false;

    @Override
    protected void readSubData(byte[] subData) {
        Log.i((String)"Jxr35", (String)("WeatherForecastRsp.. readSubData: " + DataTransferUtils.getHexString(subData)));
        this.isSuccess = subData[0] == 26;
    }

    public boolean isSuccess() {
        return this.isSuccess;
    }

    public String toString() {
        return "WeatherForecastRsp{isSuccess=" + this.isSuccess + ", status=" + this.status + '}';
    }
}


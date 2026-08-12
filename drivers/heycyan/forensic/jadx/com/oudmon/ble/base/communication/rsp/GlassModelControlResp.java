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
import java.util.Arrays;

public class GlassModelControlResp
extends BaseRspCmd {
    private int dataType;
    private int glassWorkType;
    private int imageCount;
    private int videoCount;
    private String wifiMac;

    @Override
    public boolean acceptData(byte[] data) {
        Log.i((String)"Jxr35", (String)ByteUtil.byteArrayToString(data));
        this.dataType = data[1];
        if (this.dataType == 4) {
            this.imageCount = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 2, 4));
            this.videoCount = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 4, 6));
        } else if (this.dataType == 1) {
            this.glassWorkType = data[2];
            this.wifiMac = String.format("%02X", data[4]) + ":" + String.format("%02X", data[5]) + ":" + String.format("%02X", data[6]) + ":" + String.format("%02X", data[7]) + ":" + String.format("%02X", data[8]) + ":" + String.format("%02X", data[9]);
        }
        return false;
    }

    public int getDataType() {
        return this.dataType;
    }

    public int getGlassWorkType() {
        return this.glassWorkType;
    }

    public int getImageCount() {
        return this.imageCount;
    }

    public int getVideoCount() {
        return this.videoCount;
    }

    public String getWifiMac() {
        return this.wifiMac;
    }
}


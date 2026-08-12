/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.bigData.resp;

import android.util.Log;
import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DeviceInfoResponse
extends BaseResponse {
    private byte[] subData;
    private String firmwareVersion = "";
    private String hardwareVersion = "";
    private String wifiFirmwareVersion = "";
    private String wifiHardwareVersion = "";

    @Override
    public boolean acceptData(byte[] data) {
        this.subData = data;
        try {
            int twsFm = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 9));
            int twsHw = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 9, 11));
            int socFm = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 11, 13));
            int socHw = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 13, 15));
            this.firmwareVersion = new String(Arrays.copyOfRange(data, 15, 15 + twsFm), StandardCharsets.UTF_8);
            this.hardwareVersion = new String(Arrays.copyOfRange(data, 15 + twsFm, 15 + twsFm + twsHw), StandardCharsets.UTF_8);
            this.wifiFirmwareVersion = new String(Arrays.copyOfRange(data, 15 + twsFm + twsHw, 15 + twsFm + twsHw + socFm), StandardCharsets.UTF_8);
            this.wifiHardwareVersion = new String(Arrays.copyOfRange(data, 15 + twsFm + twsHw + socFm, 15 + twsFm + twsHw + socFm + socHw), StandardCharsets.UTF_8);
            Log.i((String)"GLASSES_LOG", (String)("firmwareVersion=" + this.firmwareVersion + ",hardwareVersion=" + this.hardwareVersion + ",wifiFirmwareVersion=" + this.wifiFirmwareVersion + ",wifiHardwareVersion=" + this.wifiHardwareVersion));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public byte[] getSubData() {
        return this.subData;
    }

    public String getFirmwareVersion() {
        return this.firmwareVersion;
    }

    public String getHardwareVersion() {
        return this.hardwareVersion;
    }

    public String getWifiFirmwareVersion() {
        return this.wifiFirmwareVersion;
    }

    public String getWifiHardwareVersion() {
        return this.wifiHardwareVersion;
    }
}


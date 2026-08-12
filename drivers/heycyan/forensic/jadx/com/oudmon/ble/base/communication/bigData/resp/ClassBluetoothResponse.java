/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ClassBluetoothResponse
extends BaseResponse {
    private byte[] subData;
    private String btAddress;
    private String btName;

    @Override
    public boolean acceptData(byte[] data) {
        this.subData = data;
        this.btAddress = this.bytesToMac(Arrays.copyOfRange(data, 6, 12));
        this.btName = new String(Arrays.copyOfRange(data, 13, 13 + data[12]), StandardCharsets.UTF_8);
        return false;
    }

    public byte[] getSubData() {
        return this.subData;
    }

    public String getBtAddress() {
        return this.btAddress;
    }

    public String getBtName() {
        return this.btName;
    }

    public String bytesToMac(byte[] copyOfRange) {
        StringBuilder sb = new StringBuilder();
        for (byte b : copyOfRange) {
            String i = Integer.toHexString(b & 0xFF).toUpperCase();
            i = i.length() == 1 ? "0" + i : i;
            sb.append(i).append(":");
        }
        return sb.substring(0, sb.length() - 1);
    }
}


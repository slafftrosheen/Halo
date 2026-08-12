/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.Constants;

public abstract class BaseReqCmd {
    protected static final String TAG = "Jxr35";
    protected byte key;
    protected int type;

    public BaseReqCmd(byte key) {
        this.key = key;
    }

    public byte[] getData() {
        byte[] bytes = new byte[Constants.CMD_DATA_LENGTH];
        bytes[0] = this.key;
        byte[] subData = this.getSubData();
        if (subData != null) {
            System.arraycopy(subData, 0, bytes, 1, subData.length);
        }
        this.addCRC(bytes);
        return bytes;
    }

    protected abstract byte[] getSubData();

    private void addCRC(byte[] data) {
        int crc = 0;
        for (int i = 0; i < data.length - 1; ++i) {
            crc += data[i];
        }
        data[data.length - 1] = (byte)(crc & 0xFF);
    }
}


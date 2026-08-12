/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.bean;

import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.nio.charset.Charset;

public class WifiInfoReq {
    private byte[] mData = new byte[2];

    public WifiInfoReq(String url) {
        this.mData[0] = 2;
        this.mData[1] = (byte)url.length();
        byte[] urlBytes = url.getBytes(Charset.forName("UTF-8"));
        this.mData = ByteUtil.concat(this.mData, urlBytes);
    }

    public byte[] getSubData() {
        return this.mData;
    }
}


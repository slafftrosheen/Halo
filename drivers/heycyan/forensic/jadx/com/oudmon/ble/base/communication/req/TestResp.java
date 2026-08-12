/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class TestResp
extends MixtureRsp {
    String data;
    StringBuilder sb = new StringBuilder();

    @Override
    protected void readSubData(byte[] subData) {
        this.sb.delete(0, this.sb.length());
        int x1 = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 0, 2));
        int y1 = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 2, 4));
        int z1 = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 4, 6));
        int x2 = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 6, 8));
        int y2 = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 8, 10));
        int z2 = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 10, 12));
        this.sb.append(x1).append(",");
        this.sb.append(y1).append(",");
        this.sb.append(z1).append(",");
        this.sb.append(x2).append(",");
        this.sb.append(y2).append(",");
        this.sb.append(z2).append("\n");
    }

    public String getData() {
        return this.sb.toString();
    }
}


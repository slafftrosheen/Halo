/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class GlassesAiVoicePlayStatusRsp
extends BaseResponse {
    int status;

    @Override
    public boolean acceptData(byte[] data) {
        this.status = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8));
        return false;
    }

    public int getStatus() {
        return this.status;
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class GlassesAiVoiceRsp
extends BaseResponse {
    boolean open;

    @Override
    public boolean acceptData(byte[] data) {
        this.open = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8)) == 1;
        return false;
    }

    public boolean isOpen() {
        return this.open;
    }
}


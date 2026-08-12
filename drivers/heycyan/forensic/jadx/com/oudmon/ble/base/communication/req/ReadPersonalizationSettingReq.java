/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class ReadPersonalizationSettingReq
extends BaseReqCmd {
    private byte[] data = new byte[]{1, 2, 3};

    private ReadPersonalizationSettingReq() {
        super((byte)23);
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }

    public static ReadPersonalizationSettingReq getReadInstance() {
        return new ReadPersonalizationSettingReq();
    }
}


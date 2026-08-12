/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class CameraReq
extends BaseReqCmd {
    public static final byte ACTION_INTO_CAMARA_UI = 4;
    public static final byte ACTION_KEEP_SCREEN_ON = 5;
    public static final byte ACTION_FINISH = 6;
    private byte action = 0;

    public CameraReq(byte action) {
        super((byte)2);
        if (action > 6 || action < 4) {
            throw new IllegalArgumentException("action \u8303\u56f4\u51fa\u9519");
        }
        this.action = action;
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{this.action};
    }
}


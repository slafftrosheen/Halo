/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class CameraNotifyRsp
extends BaseRspCmd {
    public static final int ACTION_INTO_CAMERA_UI = 1;
    public static final int ACTION_TAKE_PHOTO = 2;
    public static final int ACTION_FINISH = 3;
    private int action = 0;

    @Override
    public boolean acceptData(byte[] data) {
        this.action = data[0];
        return false;
    }

    public int getAction() {
        return this.action;
    }
}


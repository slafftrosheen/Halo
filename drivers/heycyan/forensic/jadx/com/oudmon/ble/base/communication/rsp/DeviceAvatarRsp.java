/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class DeviceAvatarRsp
extends BaseRspCmd {
    private int screenType;
    private int avatarWidth;
    private int avatarHeight;

    @Override
    public boolean acceptData(byte[] data) {
        this.screenType = data[0];
        this.avatarWidth = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 1, 3));
        this.avatarHeight = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 3, 5));
        return false;
    }

    public int getScreenType() {
        return this.screenType;
    }

    public int getAvatarWidth() {
        return this.avatarWidth;
    }

    public int getAvatarHeight() {
        return this.avatarHeight;
    }
}


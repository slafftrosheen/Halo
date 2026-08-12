/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class BloodSugarLipidsSettingRsp
extends BaseRspCmd {
    private boolean read;
    private byte type;
    private boolean isEnable;
    private int value;

    public boolean isEnable() {
        return this.isEnable;
    }

    public int getValue() {
        return this.value;
    }

    public byte getType() {
        return this.type;
    }

    @Override
    public boolean acceptData(byte[] data) {
        boolean bl = this.read = data[1] == 1;
        if (data[1] == 1) {
            this.type = data[0];
            this.isEnable = data[2] == 1;
            this.value = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 3, 5));
        }
        return false;
    }
}


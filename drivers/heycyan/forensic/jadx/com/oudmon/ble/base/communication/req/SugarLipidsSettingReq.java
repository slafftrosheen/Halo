/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;
import com.oudmon.ble.base.communication.utils.ByteUtil;

public class SugarLipidsSettingReq
extends MixtureReq {
    private SugarLipidsSettingReq(byte type) {
        super((byte)58);
        this.subData = new byte[]{type, 1};
    }

    private SugarLipidsSettingReq(byte type, boolean isEnable, int value) {
        super((byte)58);
        this.subData = new byte[]{type, 2, (byte)(isEnable ? 1 : 0), (byte)ByteUtil.loword(value), (byte)ByteUtil.hiword(value)};
    }

    public static SugarLipidsSettingReq getReadInstance(byte type) {
        return new SugarLipidsSettingReq(type);
    }

    public static SugarLipidsSettingReq getWriteInstance(byte type, boolean isEnable, int value) {
        return new SugarLipidsSettingReq(type, isEnable, value);
    }
}


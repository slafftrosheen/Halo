/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class TargetSettingRsp
extends MixtureRsp {
    private int mStep;
    private int mCalorie;
    private int mDistance;
    private int mSport;
    private int mSleep;

    @Override
    protected void readSubData(byte[] subData) {
        this.mStep = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 1, 4));
        this.mCalorie = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 4, 7));
        this.mDistance = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 7, 10));
        this.mSport = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 10, 12));
        this.mSleep = ByteUtil.bytesToInt(Arrays.copyOfRange(subData, 12, 14));
    }

    public int getStep() {
        return this.mStep;
    }

    public int getCalorie() {
        return this.mCalorie;
    }

    public int getDistance() {
        return this.mDistance;
    }

    public int getmSport() {
        return this.mSport;
    }

    public int getmSleep() {
        return this.mSleep;
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;
import com.oudmon.ble.base.communication.utils.ByteUtil;

public class TimeFormatRsp
extends MixtureRsp {
    private boolean is24;
    private boolean isMetric;
    public int gender;
    public int age;
    public int height;
    public int weight;
    public int sbp;
    public int dbp;
    public int warmingHeart;
    public int open = 2;

    @Override
    protected void readSubData(byte[] subData) {
        this.is24 = subData[1] == 0;
        this.isMetric = subData[2] == 0;
        this.gender = subData[3];
        this.age = subData[4];
        this.height = subData[5];
        this.weight = subData[6];
        this.sbp = subData[7];
        this.dbp = subData[8];
        this.warmingHeart = ByteUtil.byteToInt(subData[9]);
        this.open = subData[10];
    }

    public boolean is24() {
        return this.is24;
    }

    public boolean isMetric() {
        return this.isMetric;
    }

    public String toString() {
        return "TimeFormatRsp{is24=" + this.is24 + '}';
    }
}


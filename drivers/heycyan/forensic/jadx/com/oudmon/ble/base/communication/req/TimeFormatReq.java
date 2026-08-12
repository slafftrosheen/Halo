/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class TimeFormatReq
extends MixtureReq {
    private TimeFormatReq() {
        super((byte)10);
        this.subData = new byte[]{1};
    }

    private TimeFormatReq(boolean is24, byte metric) {
        super((byte)10);
        this.subData = new byte[]{2, (byte)(!is24 ? 1 : 0), metric};
    }

    public static TimeFormatReq getReadInstance() {
        return new TimeFormatReq();
    }

    public static TimeFormatReq getWriteInstance(boolean is24, byte metric) {
        return new TimeFormatReq(is24, metric);
    }

    public static TimeFormatReq getWriteInstance(final boolean is24, final int metric, final int sex, final int age, final int height, final int weight, final int sbp, final int dbp, final int rateWarn) {
        return new TimeFormatReq(){
            {
                this.subData = new byte[]{2, (byte)(!is24 ? 1 : 0), (byte)metric, (byte)sex, (byte)age, (byte)height, (byte)weight, (byte)sbp, (byte)dbp, (byte)rateWarn};
            }
        };
    }

    public static TimeFormatReq getWriteInstance(final boolean is24, final boolean metric, final int sex, final int age, final int height, final int weight, final int sbp, final int dbp, final int rateWarn, final int open) {
        return new TimeFormatReq(){
            {
                this.subData = new byte[]{2, (byte)(!is24 ? 1 : 0), (byte)(!metric ? 1 : 0), (byte)sex, (byte)age, (byte)height, (byte)weight, (byte)sbp, (byte)dbp, (byte)rateWarn, (byte)open};
            }
        };
    }
}


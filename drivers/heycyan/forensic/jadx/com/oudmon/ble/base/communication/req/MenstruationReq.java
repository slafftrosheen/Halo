/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class MenstruationReq
extends MixtureReq {
    public MenstruationReq(byte key) {
        super((byte)43);
        this.subData = new byte[]{1};
    }

    public MenstruationReq(boolean isEnable, int during, int cycle, int lastStart, int lastEnd, boolean alarmEnable, int mAlert, int oAlert, int hour, int min) {
        super((byte)43);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 0), (byte)(during & 0xFF), (byte)(cycle & 0xFF), (byte)(lastStart & 0xFF), (byte)(lastEnd & 0xFF), (byte)(alarmEnable ? 1 : 0), (byte)(mAlert & 0xFF), (byte)(oAlert & 0xFF), (byte)(hour & 0xFF), (byte)(min & 0xFF)};
    }
}


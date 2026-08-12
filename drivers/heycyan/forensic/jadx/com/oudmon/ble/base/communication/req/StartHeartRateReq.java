/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;

public class StartHeartRateReq
extends BaseReqCmd {
    public static final byte TYPE_HEARTRATE = 1;
    public static final byte TYPE_BLOODPRESSURE = 2;
    public static final byte TYPE_BLOODOXYGEN = 3;
    public static final byte TYPE_FATIGUE = 4;
    public static final byte TYPE_HEALTHCHECK = 5;
    public static final byte TYPE_REALTIMEHEARTRATE = 6;
    public static final byte TYPE_ECG = 7;
    public static final byte TYPE_PRESSURE = 8;
    public static final byte TYPE_BLOOD_SUGAR = 9;
    public static final byte TYPE_HRV = 10;
    public static final byte TYPE_BODY_TEMPERATURE = 11;
    private byte type;
    public static final byte ACTION_START = 1;
    public static final byte ACTION_PAUSE = 2;
    public static final byte ACTION_CONTINUE = 3;
    public static final byte ACTION_STOP = 4;
    private byte sub;

    private StartHeartRateReq(byte type, byte sub) {
        super((byte)105);
        this.type = type;
        this.sub = sub;
    }

    public static StartHeartRateReq getSimpleReq(byte type) {
        return new StartHeartRateReq(type, type < 3 ? (byte)0 : BLEDataFormatUtils.decimalToBCD(25));
    }

    public static StartHeartRateReq getRealtimeHeartRate(byte action) {
        return new StartHeartRateReq(6, action);
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{this.type, this.sub};
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class StopHeartRateReq
extends BaseReqCmd {
    private byte[] data;

    private StopHeartRateReq(byte type, byte bb, byte cc) {
        super((byte)106);
        this.data = new byte[]{type, bb, cc};
    }

    public static StopHeartRateReq stopHeartRate(byte hrValue) {
        return new StopHeartRateReq(1, hrValue, 0);
    }

    public static StopHeartRateReq stopBloodPressure(byte sbp, byte dbp) {
        return new StopHeartRateReq(2, sbp, dbp);
    }

    public static StopHeartRateReq stopBloodOxygen(byte oxyValue) {
        return new StopHeartRateReq(3, oxyValue, 0);
    }

    public static StopHeartRateReq stopFatigue(byte fatigueScore) {
        return new StopHeartRateReq(4, fatigueScore, 0);
    }

    public static StopHeartRateReq stopHealthCheck() {
        return new StopHeartRateReq(5, 0, 0);
    }

    public static StopHeartRateReq stopTemperatureCheck() {
        return new StopHeartRateReq(11, 0, 0);
    }

    public static StopHeartRateReq stopEcg(int ecgType) {
        return new StopHeartRateReq(7, (byte)ecgType, 0);
    }

    public static StopHeartRateReq stopPressure(byte value) {
        return new StopHeartRateReq(8, value, 0);
    }

    public static StopHeartRateReq stopBloodSugar(byte value) {
        return new StopHeartRateReq(9, value, 0);
    }

    public static StopHeartRateReq stopHrv(byte value) {
        return new StopHeartRateReq(10, value, 0);
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}


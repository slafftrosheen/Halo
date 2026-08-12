/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class DeviceSupportFunctionRsp
extends BaseRspCmd {
    public boolean supportTouch;
    public boolean supportMoslin;
    public boolean supportAPPRevision;
    public boolean supportBlePair;
    public boolean supportGesture;
    public boolean supportRingMusic;
    public boolean supportRingVideo;
    public boolean supportRingEbook;
    public boolean supportRingCamera;
    public boolean supportRingPhoneCall;
    public boolean supportRingGame;
    public boolean supportHeart;
    public boolean supportLongSit;
    public boolean supportDrink;

    @Override
    public boolean acceptData(byte[] data) {
        this.supportTouch = (data[1] & 1) != 0;
        this.supportMoslin = (data[1] & 2) != 0;
        this.supportAPPRevision = (data[1] & 4) != 0;
        this.supportBlePair = (data[1] & 8) != 0;
        this.supportGesture = (data[1] & 0x80) != 0;
        this.supportRingMusic = (data[2] & 1) != 0;
        this.supportRingVideo = (data[2] & 2) != 0;
        this.supportRingEbook = (data[2] & 4) != 0;
        this.supportRingCamera = (data[2] & 8) != 0;
        this.supportRingPhoneCall = (data[2] & 0x10) != 0;
        this.supportRingGame = (data[2] & 0x20) != 0;
        this.supportHeart = (data[2] & 0x40) != 0;
        this.supportLongSit = (data[4] & 4) != 0;
        this.supportDrink = (data[4] & 8) != 0;
        return false;
    }
}


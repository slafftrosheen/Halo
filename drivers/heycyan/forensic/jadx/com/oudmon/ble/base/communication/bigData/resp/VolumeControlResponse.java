/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;

public class VolumeControlResponse
extends BaseResponse {
    private byte[] subData;
    private int currVolumeType;
    private int minVolumeMusic;
    private int maxVolumeMusic;
    private int currVolumeMusic;
    private int minVolumeSystem;
    private int maxVolumeSystem;
    private int currVolumeSystem;
    private int minVolumeCall;
    private int maxVolumeCall;
    private int currVolumeCall;

    @Override
    public boolean acceptData(byte[] data) {
        this.subData = data;
        this.minVolumeMusic = data[8];
        this.maxVolumeMusic = data[9];
        this.currVolumeMusic = data[10];
        this.minVolumeCall = data[12];
        this.maxVolumeCall = data[13];
        this.currVolumeCall = data[14];
        this.minVolumeSystem = data[16];
        this.maxVolumeSystem = data[17];
        this.currVolumeSystem = data[18];
        this.currVolumeType = data[19];
        return false;
    }

    public byte[] getSubData() {
        return this.subData;
    }

    public int getCurrVolumeType() {
        return this.currVolumeType;
    }

    public int getMinVolumeMusic() {
        return this.minVolumeMusic;
    }

    public int getMaxVolumeMusic() {
        return this.maxVolumeMusic;
    }

    public int getCurrVolumeMusic() {
        return this.currVolumeMusic;
    }

    public int getMinVolumeSystem() {
        return this.minVolumeSystem;
    }

    public int getMaxVolumeSystem() {
        return this.maxVolumeSystem;
    }

    public int getCurrVolumeSystem() {
        return this.currVolumeSystem;
    }

    public int getMinVolumeCall() {
        return this.minVolumeCall;
    }

    public int getMaxVolumeCall() {
        return this.maxVolumeCall;
    }

    public int getCurrVolumeCall() {
        return this.currVolumeCall;
    }
}


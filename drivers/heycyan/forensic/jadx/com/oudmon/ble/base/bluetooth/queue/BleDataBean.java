/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.bluetooth.queue;

public class BleDataBean {
    private byte[] data;
    private int subLength;
    private int sleepTime;

    public BleDataBean(byte[] data, int subLength) {
        this.data = data;
        this.subLength = subLength;
    }

    public BleDataBean(byte[] data, int subLength, int sleepTime) {
        this.data = data;
        this.subLength = subLength;
        this.sleepTime = sleepTime;
    }

    public int getSleepTime() {
        return this.sleepTime;
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getSubLength() {
        return this.subLength;
    }

    public void setSubLength(int subLength) {
        this.subLength = subLength;
    }
}


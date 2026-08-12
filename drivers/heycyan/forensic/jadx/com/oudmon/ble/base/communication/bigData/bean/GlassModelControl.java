/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.bean;

public class GlassModelControl {
    protected byte[] subData;

    public GlassModelControl(byte[] sendData) {
        this.subData = sendData;
    }

    public byte[] getSubData() {
        return this.subData;
    }
}


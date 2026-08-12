/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.bean;

public class ECardEntity {
    int type;
    String url;
    boolean support;
    int deviceError;
    int readOrWrite;

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSupport() {
        return this.support;
    }

    public void setSupport(boolean support) {
        this.support = support;
    }

    public int getDeviceError() {
        return this.deviceError;
    }

    public void setDeviceError(int deviceError) {
        this.deviceError = deviceError;
    }

    public int getReadOrWrite() {
        return this.readOrWrite;
    }

    public void setReadOrWrite(int readOrWrite) {
        this.readOrWrite = readOrWrite;
    }
}


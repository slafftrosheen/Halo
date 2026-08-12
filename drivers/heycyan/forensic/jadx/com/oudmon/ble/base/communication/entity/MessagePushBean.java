/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.entity;

public class MessagePushBean {
    private String message;
    private long time;

    public MessagePushBean(String message, long time) {
        this.message = message;
        this.time = time;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}


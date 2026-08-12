/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.entity;

public class BlePressure {
    public long time;
    public int dbp;
    public int sbp;

    public BlePressure(long time, int sbp, int dbp) {
        this.time = time;
        this.sbp = sbp;
        this.dbp = dbp;
    }
}


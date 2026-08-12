/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.dfu_temperature;

import java.util.Arrays;

public class TemperatureEntity {
    public int mIndex;
    public int mTimeSpan;
    public float[] mValues;

    public void clear() {
        this.mIndex = 0;
        this.mTimeSpan = 0;
        this.mValues = null;
    }

    public String toString() {
        return "TemperatureEntity{mIndex=" + this.mIndex + ", mTimeSpan=" + this.mTimeSpan + ", mValues=" + Arrays.toString(this.mValues) + '}';
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.entity;

public class AlarmEntity {
    private int alarmIndex;
    private int enable;
    private int hour;
    private int minute;
    private byte weekMask;

    public AlarmEntity(int alarmIndex, int enable, int hour, int minute, byte weekMask) {
        this.alarmIndex = alarmIndex;
        this.enable = enable;
        this.hour = hour;
        this.minute = minute;
        this.weekMask = weekMask;
    }

    public int getAlarmIndex() {
        return this.alarmIndex;
    }

    public int getEnable() {
        return this.enable;
    }

    public boolean isEnable() {
        return this.enable != 0;
    }

    public int getHour() {
        return this.hour;
    }

    public int getMinute() {
        return this.minute;
    }

    public byte getWeekMask() {
        return this.weekMask;
    }

    public void setEnable(boolean enable) {
        this.enable = enable ? 1 : 0;
    }

    public void setWeekMask(byte weekMask) {
        this.weekMask = weekMask;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public void enableTheWeek(int index, boolean isEnable) {
        this.weekMask = (byte)(~(1 << index) & this.weekMask);
        if (isEnable) {
            this.weekMask = (byte)(1 << index | this.weekMask);
        }
    }

    public AlarmEntity cloneMyself() {
        return new AlarmEntity(this.alarmIndex, this.enable, this.hour, this.minute, this.weekMask);
    }
}


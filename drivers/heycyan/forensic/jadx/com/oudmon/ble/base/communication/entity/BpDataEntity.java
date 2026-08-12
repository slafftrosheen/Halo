/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.entity;

import java.util.ArrayList;

public class BpDataEntity {
    private int year;
    private int mouth;
    private int day;
    private int timeDelay;
    private ArrayList<BpValue> bpValues;

    public BpDataEntity(int year, int mouth, int day, int timeDelay) {
        this.year = year;
        this.mouth = mouth;
        this.day = day;
        this.timeDelay = timeDelay;
        this.bpValues = new ArrayList();
    }

    public void addBpIndex(int timeMinute) {
        this.bpValues.add(new BpValue(timeMinute));
    }

    public void addRealValue(int offset, byte[] bytes) {
        int remain = Math.min(this.bpValues.size() - offset, bytes.length);
        for (int i = 0; i < remain; ++i) {
            this.bpValues.get((int)(i + offset)).value = bytes[i] & 0xFF;
        }
    }

    public int getYear() {
        return this.year;
    }

    public int getMouth() {
        return this.mouth;
    }

    public int getDay() {
        return this.day;
    }

    public int getTimeDelay() {
        return this.timeDelay;
    }

    public ArrayList<BpValue> getBpValues() {
        return this.bpValues;
    }

    public class BpValue {
        int timeMinute;
        int value;

        public BpValue(int timeMinute) {
            this.timeMinute = timeMinute;
        }

        public int getTimeMinute() {
            return this.timeMinute;
        }

        public int getValue() {
            return this.value;
        }
    }
}


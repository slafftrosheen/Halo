/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.entity;

public class BleStepDetails {
    private int year;
    private int month;
    private int day;
    private int timeIndex = 0;
    private int calorie = 0;
    private int walkSteps = 0;
    private int distance = 0;
    private int runSteps = 0;

    public String toString() {
        return "BleStepDetails{year=" + this.year + ", month=" + this.month + ", day=" + this.day + ", timeIndex=" + this.timeIndex + ", calorie=" + this.calorie + ", walkSteps=" + this.walkSteps + ", distance=" + this.distance + ", runSteps=" + this.runSteps + '}';
    }

    public int getYear() {
        return this.year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return this.month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return this.day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getTimeIndex() {
        return this.timeIndex;
    }

    public void setTimeIndex(int timeIndex) {
        this.timeIndex = timeIndex;
    }

    public int getCalorie() {
        return this.calorie;
    }

    public void setCalorie(int calorie) {
        this.calorie = calorie;
    }

    public int getWalkSteps() {
        return this.walkSteps;
    }

    public void setWalkSteps(int walkSteps) {
        this.walkSteps = walkSteps;
    }

    public int getRunSteps() {
        return this.runSteps;
    }

    public void setRunSteps(int runSteps) {
        this.runSteps = runSteps;
    }

    public int getDistance() {
        return this.distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }
}


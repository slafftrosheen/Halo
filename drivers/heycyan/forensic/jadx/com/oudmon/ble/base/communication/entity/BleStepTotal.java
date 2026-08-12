/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.entity;

public class BleStepTotal {
    private int daysAgo;
    private int year;
    private int month;
    private int day;
    private int totalSteps;
    private int runningSteps;
    private int calorie;
    private int walkDistance;
    private int sportDuration;
    private int sleepDuration;

    public String toString() {
        return "BleStepTotal{ daysAgo=" + this.daysAgo + ", year=" + this.year + ", month=" + this.month + ", day=" + this.day + ", totalSteps=" + this.totalSteps + ", runningSteps=" + this.runningSteps + ", calorie=" + this.calorie + ", walkDistance=" + this.walkDistance + ", sportDuration=" + this.sportDuration + ", sleepDuration=" + this.sleepDuration + " }";
    }

    public BleStepTotal clone() {
        BleStepTotal bleStepTotal = new BleStepTotal();
        bleStepTotal.setDaysAgo(this.daysAgo);
        bleStepTotal.setYear(this.year);
        bleStepTotal.setMonth(this.month);
        bleStepTotal.setDay(this.day);
        bleStepTotal.setTotalSteps(this.totalSteps);
        bleStepTotal.setRunningSteps(this.runningSteps);
        bleStepTotal.setCalorie(this.calorie);
        bleStepTotal.setSportDuration(this.sportDuration);
        bleStepTotal.setSleepDuration(this.sleepDuration);
        return bleStepTotal;
    }

    public int getDaysAgo() {
        return this.daysAgo;
    }

    public void setDaysAgo(int daysAgo) {
        this.daysAgo = daysAgo;
    }

    public int getYear() {
        return this.year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getDay() {
        return this.day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getMonth() {
        return this.month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getTotalSteps() {
        return this.totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public int getRunningSteps() {
        return this.runningSteps;
    }

    public void setRunningSteps(int runningSteps) {
        this.runningSteps = runningSteps;
    }

    public int getCalorie() {
        return this.calorie;
    }

    public void setCalorie(int calorie) {
        this.calorie = calorie;
    }

    public int getWalkDistance() {
        return this.walkDistance;
    }

    public void setWalkDistance(int walkDistance) {
        this.walkDistance = walkDistance;
    }

    public int getSportDuration() {
        return this.sportDuration;
    }

    public void setSportDuration(int sportDuration) {
        this.sportDuration = sportDuration;
    }

    public int getSleepDuration() {
        return this.sleepDuration;
    }

    public void setSleepDuration(int sleepDuration) {
        this.sleepDuration = sleepDuration;
    }
}


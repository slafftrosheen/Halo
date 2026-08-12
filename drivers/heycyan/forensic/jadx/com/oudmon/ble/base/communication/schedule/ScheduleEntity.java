/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.schedule;

import java.util.ArrayList;
import java.util.List;

public class ScheduleEntity {
    public String mTitle;
    public int mStartTime;
    public int mEndTime;
    public int mRepeatType;
    public int mDetail;
    public List<Integer> mDetails = new ArrayList<Integer>();

    public ScheduleEntity() {
    }

    public ScheduleEntity(String mTitle, int mStartTime, int mEndTime, int mRepeatType, int mDetail) {
        this.mTitle = mTitle;
        this.mStartTime = mStartTime;
        this.mEndTime = mEndTime;
        this.mRepeatType = mRepeatType;
        this.mDetail = mDetail;
    }
}


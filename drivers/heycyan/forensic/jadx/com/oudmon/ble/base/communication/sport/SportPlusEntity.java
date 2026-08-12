/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.sport;

import com.oudmon.ble.base.communication.sport.SportLocation;
import java.util.ArrayList;
import java.util.List;

public class SportPlusEntity {
    public int mSportType;
    public int mStartTime;
    public int mDuration;
    public int mDistance;
    public float mCalories;
    public int mSpeedAvg;
    public int mSpeedMax;
    public int mRateAvg;
    public int mRateMin;
    public int mRateMax;
    public int mElevation;
    public int mUphill;
    public int mDownhill;
    public int mStepRate;
    public int mSportCount;
    public int steps;
    public List<SportLocation> mLocations = new ArrayList<SportLocation>();

    public String toString() {
        return "SportPlusEntity{mSportType=" + this.mSportType + ", mStartTime=" + this.mStartTime + ", mDuration=" + this.mDuration + ", mDistance=" + this.mDistance + ", mCalories=" + this.mCalories + ", mSpeedAvg=" + this.mSpeedAvg + ", mSpeedMax=" + this.mSpeedMax + ", mRateAvg=" + this.mRateAvg + ", mRateMin=" + this.mRateMin + ", mRateMax=" + this.mRateMax + ", mElevation=" + this.mElevation + ", mUphill=" + this.mUphill + ", mDownhill=" + this.mDownhill + ", mStepRate=" + this.mStepRate + ", mSportCount=" + this.mSportCount + ", mLocations=" + this.mLocations + ", steps=" + this.steps + '}';
    }
}

